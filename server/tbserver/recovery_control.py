"""Durable recovery coordinator; runtime adapters must implement the safety contract.

No Docker, SQL, HTTP or live-runtime hooks in this module. In particular, importing
it never initiates a restore. The adapter must enforce maintenance independently
of this process and keep prepared targets inaccessible to normal writers.
"""
from __future__ import annotations

from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path
import stat
from typing import Protocol
from uuid import UUID, uuid4


DATA_KINDS = frozenset({"USER", "TRACK", "FULL"})
PHASES = frozenset({"PREFLIGHT", "PAUSING", "SAFETY", "RESTORING", "VERIFYING",
                    "SWITCHING", "PROBING", "COMMITTED", "DONE",
                    "ROLLING_BACK", "ROLLED_BACK", "NEEDS_ATTENTION"})
TERMINAL = frozenset({"DONE", "ROLLED_BACK"})
COMMITTED = frozenset({"COMMITTED", "DONE"})


class RecoveryControlError(RuntimeError):
    pass


class JournalError(RecoveryControlError):
    pass


def _uuid(value: str) -> str:
    if not isinstance(value, str) or str(UUID(value)) != value:
        raise ValueError("non-canonical UUID")
    return value


def _generation(value: str) -> str:
    return value if value == "initial" else _uuid(value)


def validate_state(value: dict) -> dict:
    try:
        required = {"format", "operation_id", "backup_id", "admin_id", "kind",
                    "previous", "target", "phase", "updated_at", "error"}
        if not isinstance(value, dict) or set(value) != required:
            raise ValueError("layout")
        if value["format"] != "tb-recovery-control-v1" or value["phase"] not in PHASES:
            raise ValueError("format/phase")
        if value["kind"] not in DATA_KINDS:
            raise ValueError("kind")
        for key in ("operation_id", "backup_id", "admin_id", "target"):
            _uuid(value[key])
        _generation(value["previous"])
        if value["previous"] == value["target"] or value["target"] != value["operation_id"]:
            raise ValueError("target")
        date = datetime.fromisoformat(value["updated_at"])
        if date.tzinfo is None:
            raise ValueError("date")
        if value["error"] is not None and value["error"] not in {
            "OPERATION_FAILED", "ROLLBACK_FAILED", "RELEASE_FAILED"
        }:
            raise ValueError("error")
    except (ValueError, TypeError, KeyError) as exc:
        raise JournalError("RECOVERY_JOURNAL_INVALID") from exc
    return dict(value)


class Journal:
    """One persistent, private controller directory (not inside either target).

The directory must be owned by the dedicated controller, with no write access
for API/Worker. Keep the lock for all operations, including restart recovery.
"""

    def __init__(self, root: Path):
        self.root = Path(root)

    def _prepare(self) -> None:
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        info = self.root.lstat()
        if not stat.S_ISDIR(info.st_mode) or info.st_mode & 0o022:
            raise JournalError("RECOVERY_JOURNAL_DIRECTORY_UNSAFE")

    @contextmanager
    def locked(self):
        self._prepare()
        fd = os.open(self.root / "controller.lock",
                     os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        try:
            if not stat.S_ISREG(os.fstat(fd).st_mode):
                raise JournalError("RECOVERY_LOCK_UNSAFE")
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as exc:
                raise RecoveryControlError("RECOVERY_BUSY") from exc
            yield
        finally:
            os.close(fd)

    def read(self) -> dict | None:
        path = self.root / "state.json"
        try:
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        except FileNotFoundError:
            return None
        except OSError as exc:
            raise JournalError("RECOVERY_JOURNAL_UNREADABLE") from exc
        try:
            info = os.fstat(fd)
            if not stat.S_ISREG(info.st_mode) or info.st_size > 16384:
                raise JournalError("RECOVERY_JOURNAL_INVALID")
            with os.fdopen(fd, "r", encoding="utf-8") as handle:
                fd = -1
                return validate_state(json.load(handle))
        except (UnicodeError, ValueError, OSError) as exc:
            raise JournalError("RECOVERY_JOURNAL_UNREADABLE") from exc
        finally:
            if fd >= 0:
                os.close(fd)

    def write(self, value: dict) -> dict:
        state = validate_state(dict(value, updated_at=datetime.now(timezone.utc).isoformat()))
        temporary = self.root / ("state-" + str(uuid4()) + ".tmp")
        try:
            with temporary.open("x", encoding="utf-8") as handle:
                os.fchmod(handle.fileno(), 0o600)
                json.dump(state, handle, sort_keys=True)
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temporary, self.root / "state.json")
            directory = os.open(self.root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
            try:
                os.fsync(directory)
            finally:
                os.close(directory)
        except OSError as exc:
            # A failed directory fsync may occur AFTER replace. Never infer that
            # the old decision remains durable or automatically roll it back.
            raise JournalError("RECOVERY_JOURNAL_WRITE_FAILED") from exc
        finally:
            temporary.unlink(missing_ok=True)
        return state


class RecoveryBackend(Protocol):
    """Adapter contract. Every operation must be idempotent by operation_id.

    quiesce: persist maintenance, reject new writes and drain/stop ALL writers.
    safety: durable verified copy of the quiesced previous generation, pinned
            outside the normal four-backup retention until completion/review.
    prepare: exclusively fresh target, NEVER overwrite previous generation.
    verify: schema, files, scope preservation, FK/JSON and software compatibility.
    select: atomically select DB AND storage; maintenance remains closed.
    probe: start selected runtime under maintenance and verify its identity/health.
    release: allow writes only to the supplied, already-probed generation.

    There must be no independent timeout that opens maintenance. Neither target
    deletion nor normal retention belongs in this coordinator's failure path.
    """

    def active_generation(self) -> str: ...
    def preflight(self, state: dict) -> None: ...
    def quiesce(self, state: dict) -> None: ...
    def safety(self, state: dict) -> None: ...
    def prepare(self, state: dict) -> None: ...
    def verify(self, state: dict) -> None: ...
    def select(self, state: dict, generation: str) -> None: ...
    def probe(self, state: dict, generation: str) -> None: ...
    def release(self, state: dict, generation: str) -> None: ...


class RecoveryController:
    def __init__(self, journal: Journal, backend: RecoveryBackend):
        self.journal, self.backend = journal, backend

    def _phase(self, state: dict, phase: str, error: str | None = None) -> dict:
        return self.journal.write(dict(state, phase=phase, error=error))

    def _rollback(self, state: dict) -> dict:
        if state["phase"] in COMMITTED:
            raise RecoveryControlError("RECOVERY_COMMITTED_CANNOT_ROLL_BACK")
        state = self._phase(state, "ROLLING_BACK", "OPERATION_FAILED")
        try:
            self.backend.quiesce(dict(state))
            self.backend.select(dict(state), state["previous"])
            self.backend.probe(dict(state), state["previous"])
            # Persist rollback decision before reopening writes. On restart a
            # ROLLED_BACK journal only retries release, never selects old data.
            state = self._phase(state, "ROLLED_BACK", "OPERATION_FAILED")
        except JournalError:
            raise
        except Exception:
            return self._phase(state, "NEEDS_ATTENTION", "ROLLBACK_FAILED")
        return self._release(state, state["previous"])

    def _release(self, state: dict, generation: str) -> dict:
        try:
            self.backend.release(dict(state), generation)
        except Exception:
            # Keep the decision! Writes may already have been accepted even if
            # the adapter raised after completing release.
            return self._phase(state, state["phase"], "RELEASE_FAILED")
        return self._phase(state, "DONE" if state["phase"] in COMMITTED else "ROLLED_BACK",
                           None if state["phase"] in COMMITTED else "OPERATION_FAILED")

    def recover(self) -> dict | None:
        """Called before accepting a new request after any controller restart."""
        with self.journal.locked():
            state = self.journal.read()
            if state is None:
                return None
            if state["phase"] == "DONE" and state["error"] is None:
                return state
            if state["phase"] == "ROLLED_BACK" and state["error"] != "RELEASE_FAILED":
                # release could have crashed after the durable rollback decision;
                # an idempotent release is required even without an error marker.
                return self._release(state, state["previous"])
            if state["phase"] in COMMITTED:
                return self._release(state, state["target"])
            if state["phase"] == "ROLLED_BACK":
                return self._release(state, state["previous"])
            return self._rollback(state)

    def run(self, *, backup_id: str, admin_id: str, kind: str, operation_id: str | None = None) -> dict:
        if kind not in DATA_KINDS:
            raise RecoveryControlError("RECOVERY_KIND_NOT_SUPPORTED")
        try:
            _uuid(backup_id)
            _uuid(admin_id)
            if operation_id is not None:
                _uuid(operation_id)
        except (ValueError, TypeError, AttributeError) as exc:
            raise RecoveryControlError("RECOVERY_ID_INVALID") from exc
        with self.journal.locked():
            previous_state = self.journal.read()
            if previous_state and (previous_state["phase"] not in TERMINAL or
                                   previous_state["error"] == "RELEASE_FAILED"):
                raise RecoveryControlError("RECOVERY_RESTART_REVIEW_REQUIRED")
            generation = _generation(self.backend.active_generation())
            operation = operation_id or str(uuid4())
            state = self.journal.write({
                "format": "tb-recovery-control-v1", "operation_id": operation,
                "backup_id": backup_id, "admin_id": admin_id, "kind": kind,
                "previous": generation, "target": operation, "phase": "PREFLIGHT",
                "updated_at": datetime.now(timezone.utc).isoformat(), "error": None,
            })
            try:
                self.backend.preflight(dict(state))
                for phase, action in (("PAUSING", self.backend.quiesce),
                                      ("SAFETY", self.backend.safety),
                                      ("RESTORING", self.backend.prepare),
                                      ("VERIFYING", self.backend.verify)):
                    state = self._phase(state, phase)
                    action(dict(state))
                state = self._phase(state, "SWITCHING")
                self.backend.select(dict(state), state["target"])
                state = self._phase(state, "PROBING")
                self.backend.probe(dict(state), state["target"])
                state = self._phase(state, "COMMITTED")
            except JournalError:
                # Storage durability cannot be established. Preserve whatever
                # independent maintenance barrier has already been installed.
                raise
            except Exception:
                return self._rollback(state)
            return self._release(state, state["target"])
