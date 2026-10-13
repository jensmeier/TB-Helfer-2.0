"""Independent restore request service. API can submit IDs, never commands.

Runtime selection and journal are controller-only mounts. The API writes only
one bounded request slot. The read-only progress server reveals just phase/error
for an unguessable request ID and remains available while runtime writers stop.
"""
from contextlib import contextmanager
import fcntl
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import signal
import stat
import threading
import time
from uuid import UUID

from .recovery_control import Journal, RecoveryController, RecoveryControlError, TERMINAL, _generation, _uuid
from .recovery_runtime import Deployment, read_json, write_json


class RequestError(RecoveryControlError):
    pass


def validate_request(value):
    try:
        if set(value) != {'request_id', 'backup_id', 'admin_id', 'kind', 'generation'}:
            raise ValueError()
        for key in ('request_id', 'backup_id', 'admin_id'):
            _uuid(value[key])
        if value['kind'] not in {'USER', 'TRACK', 'FULL'}:
            raise ValueError()
        _generation(value['generation'])
        return dict(value)
    except (ValueError, TypeError, KeyError, AttributeError) as exc:
        raise RequestError('RECOVERY_REQUEST_INVALID') from exc


class RequestQueue:
    def __init__(self, root):
        self.root = Path(root)

    @contextmanager
    def locked(self):
        self.root.mkdir(mode=0o700, parents=True, exist_ok=True)
        if not stat.S_ISDIR(self.root.lstat().st_mode):
            raise RequestError('RECOVERY_QUEUE_UNSAFE')
        fd = os.open(self.root / 'queue.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            yield
        except BlockingIOError as exc:
            raise RequestError('RECOVERY_BUSY') from exc
        finally:
            os.close(fd)

    def read(self):
        try:
            return validate_request(read_json(self.root / 'pending.json'))
        except FileNotFoundError:
            return None

    def submit(self, value, completed=None):
        value = validate_request(value)
        with self.locked():
            pending = self.read()
            if pending:
                if pending == value:
                    return value
                raise RequestError('RECOVERY_BUSY')
            if completed and completed.get('operation_id') == value['request_id']:
                if any(completed[k] != value[k] for k in ('backup_id', 'admin_id', 'kind')):
                    raise RequestError('RECOVERY_REQUEST_ID_CONFLICT')
                return value
            if completed and (completed['phase'] not in TERMINAL or completed['error'] == 'RELEASE_FAILED'):
                raise RequestError('RECOVERY_REVIEW_REQUIRED')
            write_json(self.root / 'pending.json', value)
            return value

    def finish(self, request):
        with self.locked():
            if self.read() != request:
                raise RequestError('RECOVERY_REQUEST_CHANGED')
            (self.root / 'pending.json').unlink()
            fd = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)


def initialize(control_root, journal_root):
    control, journal = Path(control_root), Journal(journal_root)
    control.mkdir(mode=0o700, parents=True, exist_ok=True)
    with journal.locked():
        selection = control / 'selection.json'
        if selection.exists():
            Deployment(control).read()  # Corruption never falls back to initial.
            return
        if (control / 'initialized.json').exists() or journal.read() is not None:
            raise RequestError('RECOVERY_SELECTION_MISSING_REVIEW_REQUIRED')
        write_json(control / 'initialized.json', {'initialized': True})
        Deployment(control).select('initial', 'OPEN')


def public_progress(request_id, journal, queue):
    _uuid(request_id)
    state = journal.read()
    if state and state['operation_id'] == request_id:
        return {'phase': state['phase'], 'error': state['error'],
                'complete': state['phase'] in TERMINAL and state['error'] != 'RELEASE_FAILED'}
    pending = queue.read()
    if pending and pending['request_id'] == request_id:
        return {'phase': 'QUEUED', 'error': None, 'complete': False}
    return None


def process_one(controller, queue):
    request = queue.read()
    if request is None:
        return None
    previous = controller.journal.read()
    if previous and previous['operation_id'] == request['request_id']:
        # Crash between journal commit and queue removal must NEVER rerun a
        # restore: later accepted writes must stay on the chosen generation.
        result = controller.recover()
    else:
        if controller.backend.active_generation() != request['generation']:
            raise RequestError('RECOVERY_REQUEST_GENERATION_CHANGED')
        result = controller.run(backup_id=request['backup_id'], admin_id=request['admin_id'],
                                kind=request['kind'], operation_id=request['request_id'])
    if result['phase'] in TERMINAL and result['error'] != 'RELEASE_FAILED':
        queue.finish(request)
    return result


def main():
    control = os.environ['TB_RECOVERY_CONTROL_ROOT']
    journal = Journal(os.environ['TB_RECOVERY_JOURNAL_ROOT'])
    if '--initialize' in __import__('sys').argv:
        initialize(control, journal.root)
        return
    from .recovery_backend import ProcessBackend
    queue = RequestQueue(os.environ['TB_RECOVERY_QUEUE_ROOT'])
    backend = ProcessBackend(control, os.environ['TB_RECOVERY_ACK_ROOT'],
                             probe_url=os.getenv('TB_RECOVERY_PROBE_URL', 'http://api:8080/health/ready'))
    controller = RecoveryController(journal, backend)
    stopping = threading.Event()
    for signum in (signal.SIGTERM, signal.SIGINT):
        signal.signal(signum, lambda *_: stopping.set())
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            try:
                if not self.path.startswith('/recovery-progress/'):
                    raise ValueError()
                value = public_progress(self.path.removeprefix('/recovery-progress/'), journal, queue)
                if value is None:
                    self.send_error(404)
                    return
                body = json.dumps(value).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.send_header('Cache-Control', 'no-store')
                self.end_headers()
                self.wfile.write(body)
            except Exception:
                self.send_error(503)
        def log_message(self, *_):
            pass  # Never log the unguessable progress URL.
    server = ThreadingHTTPServer(('0.0.0.0', 8090), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    # Any interrupted operation is resolved before accepting another request.
    controller.recover()
    while not stopping.is_set():
        result = process_one(controller, queue)
        if result and (result['phase'] not in TERMINAL or result['error'] == 'RELEASE_FAILED'):
            raise RequestError('RECOVERY_REVIEW_REQUIRED')
        stopping.wait(0.5)
    server.shutdown()


if __name__ == '__main__':
    main()
