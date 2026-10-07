"""Persistent deployment selection and bounded runtime process supervision.

Only the controller writes selection.json. Runtime containers mount that
directory read-only and each gets its own writable acknowledgement directory.
No Docker socket, container API, shell command or archive-supplied command.
"""
from __future__ import annotations

import argparse
import fcntl
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys
import time
from uuid import UUID, uuid4

from .recovery_control import _generation

ROLES = ('api', 'worker', 'monitor')
COMMANDS = {
    'api': [sys.executable, '-m', 'uvicorn', 'tbserver.main:app', '--host', '0.0.0.0', '--port', '8080'],
    'worker': [sys.executable, '-m', 'tbserver.jobs', 'worker'],
    'monitor': [sys.executable, '-m', 'tbserver.monitor'],
}


class RuntimeControlError(RuntimeError):
    pass


def read_json(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode) or info.st_size > 16384:
            raise RuntimeControlError('RECOVERY_RUNTIME_FILE_INVALID')
        def unique(pairs):
            data = {}
            for key, value in pairs:
                if key in data:
                    raise RuntimeControlError('RECOVERY_RUNTIME_DUPLICATE_FIELD')
                data[key] = value
            return data
        with os.fdopen(fd, 'r') as handle:
            fd = -1
            return json.load(handle, object_pairs_hook=unique)
    finally:
        if fd >= 0:
            os.close(fd)


def write_json(path, value):
    path = Path(path)
    info = path.parent.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_mode & 0o022:
        raise RuntimeControlError('RECOVERY_RUNTIME_DIRECTORY_UNSAFE')
    temporary = path.parent / ('.' + str(uuid4()) + '.tmp')
    try:
        with temporary.open('x') as handle:
            os.fchmod(handle.fileno(), 0o600)
            json.dump(value, handle, sort_keys=True)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        temporary.unlink(missing_ok=True)


def validate_selection(value):
    try:
        if (not isinstance(value, dict) or set(value) != {'format', 'generation', 'mode', 'revision'}
                or value['format'] != 'tb-runtime-selection-v1' or value['mode'] not in {'OPEN', 'PROBE', 'STOPPED'}):
            raise ValueError()
        _generation(value['generation'])
        if str(UUID(value['revision'])) != value['revision']:
            raise ValueError()
    except (ValueError, TypeError, AttributeError, KeyError) as exc:
        raise RuntimeControlError('RECOVERY_RUNTIME_SELECTION_INVALID') from exc
    return dict(value)


class Deployment:
    def __init__(self, root):
        self.root = Path(root)

    def read(self):
        return validate_selection(read_json(self.root / 'selection.json'))

    def select(self, generation, mode):
        state = validate_selection({'format': 'tb-runtime-selection-v1', 'generation': generation,
                                    'mode': mode, 'revision': str(uuid4())})
        write_json(self.root / 'selection.json', state)
        return state


def generation_environment(generation, base=None):
    _generation(generation)
    env = dict(os.environ if base is None else base)
    if generation != 'initial':
        root = Path(env['TB_RECOVERY_STORAGE_ROOT'])
        if not root.is_absolute() or '..' in root.parts:
            raise RuntimeControlError('RECOVERY_RUNTIME_STORAGE_INVALID')
        env['DB_NAME'] = 'tb_recovery_' + UUID(generation).hex
        env['STORAGE_ROOT'] = str(root / generation)
        env['TB_RECOVERY_ID'] = generation
    env['TB_RUNTIME_GENERATION'] = generation
    return env


def _stop(process, timeout):
    if process is None:
        return
    # Always signal the process group, including descendants after a leader
    # exits (e.g. a pg_dump/upload helper). Never acknowledge just leader exit.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    try:
        process.wait(timeout=timeout)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait(timeout=5)


def supervise(role, deployment, acknowledgements, *, command=None, interval=0.2, stop_timeout=30):
    if role not in ROLES:
        raise RuntimeControlError('RECOVERY_RUNTIME_ROLE_INVALID')
    ack = Path(acknowledgements)
    ack.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(ack / 'supervisor.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    process, running, last_ack = None, None, None
    stopping = False
    def stop_signal(*_):
        nonlocal stopping
        stopping = True
    previous = {s: signal.signal(s, stop_signal) for s in (signal.SIGTERM, signal.SIGINT)}
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        while not stopping:
            try:
                state = deployment.read()
            except (OSError, ValueError, RuntimeControlError):
                state = None
            desired = (state['generation'], state['mode'], state['revision']) if state else None
            should_run = state is not None and (state['mode'] == 'OPEN' or (state['mode'] == 'PROBE' and role == 'api'))
            if process is not None and (running != desired or not should_run or process.poll() is not None):
                _stop(process, stop_timeout)
                process, running = None, None
            if should_run and process is None:
                env = generation_environment(state['generation'])
                env['TB_RUNTIME_MODE'] = state['mode']
                env['TB_RECOVERY_CONTROL_ROOT'] = str(deployment.root)
                # Each database session identifies its supervised writer role.
                env['PGAPPNAME'] = 'tb-runtime-' + role
                process = subprocess.Popen(command or COMMANDS[role], env=env, start_new_session=True)
                running = desired
            status = {'role': role, 'revision': state['revision'] if state else None,
                      'generation': state['generation'] if state else None,
                      'state': 'RUNNING' if process else ('STOPPED' if state else 'BLOCKED')}
            if status != last_ack:
                write_json(ack / 'status.json', status)
                last_ack = status
            time.sleep(interval)
    finally:
        _stop(process, stop_timeout)
        os.close(fd)
        for signum, handler in previous.items():
            signal.signal(signum, handler)


class RuntimeMaintenanceMiddleware:
    """Outer ASGI gate. Disabled only for legacy, unsupervised deployments.

    Missing/corrupt selection or mismatched generation fails closed. Existing
    in-flight requests are drained/terminated by the supervisor before safety.
    """
    def __init__(self, app):
        self.app = app
        root = os.getenv('TB_RECOVERY_CONTROL_ROOT')
        self.deployment = Deployment(root) if root else None
        self.generation = os.getenv('TB_RUNTIME_GENERATION')
        self.mode = os.getenv('TB_RUNTIME_MODE')

    async def __call__(self, scope, receive, send):
        if self.deployment is None or scope['type'] not in {'http', 'websocket'}:
            return await self.app(scope, receive, send)
        state = None
        try:
            state = self.deployment.read()
            matching = state['generation'] == self.generation and state['mode'] == self.mode
            health = scope['type'] == 'http' and scope.get('method') == 'GET' and scope.get('path') in {'/health/ready', '/health/live'}
            allowed = matching and (state['mode'] == 'OPEN' or (state['mode'] == 'PROBE' and health))
        except (OSError, ValueError, RuntimeControlError):
            allowed = False
        if not allowed:
            if scope['type'] == 'websocket':
                await send({'type': 'websocket.close', 'code': 1013})
            else:
                body = b'{"detail":{"code":"SERVER_RECOVERY_MAINTENANCE"}}'
                await send({'type': 'http.response.start', 'status': 503, 'headers': [
                    (b'content-type', b'application/json'), (b'cache-control', b'no-store'), (b'retry-after', b'3')]})
                await send({'type': 'http.response.body', 'body': body})
            return
        async def identified(message):
            if message['type'] == 'http.response.start':
                message = dict(message, headers=list(message.get('headers', [])) + [
                    (b'x-tb-runtime-generation', state['generation'].encode()),
                    (b'x-tb-runtime-revision', state['revision'].encode())])
            await send(message)
        await self.app(scope, receive, identified)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('role', choices=ROLES)
    args = parser.parse_args()
    supervise(args.role, Deployment(os.environ['TB_RECOVERY_CONTROL_ROOT']), os.environ['TB_RUNTIME_ACK_ROOT'])


if __name__ == '__main__':
    main()
