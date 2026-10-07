"""Bounded process/SQL adapter for the durable recovery coordinator.

No database/container deletion. API, worker and monitor are managed by their
fixed supervisors. All selected data operations run in separate processes with
the selected generation's configuration, avoiding mutable global settings.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request

from .recovery_control import RecoveryControlError, validate_state
from .recovery_runtime import Deployment, ROLES, generation_environment, read_json, write_json


class ProcessBackend:
    def __init__(self, control_root, ack_root, *, timeout=90, probe_url='http://api:8080/health/ready'):
        self.deployment = Deployment(control_root)
        self.ack_root = Path(ack_root)
        self.timeout, self.probe_url = timeout, probe_url

    def active_generation(self):
        selection = self.deployment.read()
        if selection['mode'] != 'OPEN':
            raise RecoveryControlError('RECOVERY_RUNTIME_NOT_OPEN')
        return selection['generation']

    def _wait(self, predicate, code):
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.2)
        raise RecoveryControlError(code)

    def _acknowledged(self, selection, roles, desired):
        for role in roles:
            try:
                ack = read_json(self.ack_root / role / 'status.json')
                if (ack.get('role') != role or ack.get('revision') != selection['revision']
                        or ack.get('generation') != selection['generation'] or ack.get('state') != desired):
                    return False
            except (OSError, ValueError, TypeError):
                return False
        return True

    def _action(self, action, state):
        state = validate_state(state)
        env = generation_environment(state['previous'])
        # Internal task receives validated IDs/state on stdin, never shell text.
        env['PGAPPNAME'] = 'tb-recovery-data'
        result = subprocess.run([sys.executable,'-m','tbserver.recovery_data',action],
            input=json.dumps(state).encode(), env=env, capture_output=True, timeout=8*3600)
        if result.returncode:
            raise RecoveryControlError('RECOVERY_DATA_ACTION_FAILED_' + action.upper())

    def preflight(self, state):
        self._action('preflight', state)

    def quiesce(self, state):
        selection = self.deployment.select(self.deployment.read()['generation'], 'STOPPED')
        self._wait(lambda: self._acknowledged(selection, ROLES, 'STOPPED'), 'RECOVERY_WRITERS_NOT_STOPPED')
        # Source sessions must have ended as well as their process groups.
        self._action('drained', state)

    def safety(self, state):
        self._action('safety', state)

    def prepare(self, state):
        self._action('prepare', state)

    def verify(self, state):
        self._action('verify', state)

    def select(self, state, generation):
        if generation not in {state['previous'], state['target']}:
            raise RecoveryControlError('RECOVERY_GENERATION_NOT_IN_OPERATION')
        self.deployment.select(generation, 'STOPPED')

    def probe(self, state, generation):
        if self.deployment.read()['generation'] != generation:
            raise RecoveryControlError('RECOVERY_SELECTED_GENERATION_MISMATCH')
        selection = self.deployment.select(generation, 'PROBE')
        self._wait(lambda: self._acknowledged(selection, ('worker','monitor'), 'STOPPED'), 'RECOVERY_BACKGROUND_NOT_STOPPED')
        def healthy():
            if not self._acknowledged(selection, ('api',), 'RUNNING'):
                return False
            try:
                with urllib.request.urlopen(self.probe_url, timeout=3) as response:
                    payload = json.loads(response.read(16384))
                    return (response.status == 200 and payload.get('status') == 'ok'
                            and response.headers.get('x-tb-runtime-generation') == generation
                            and response.headers.get('x-tb-runtime-revision') == selection['revision'])
            except (OSError, ValueError):
                return False
        self._wait(healthy, 'RECOVERY_TARGET_PROBE_FAILED')

    def release(self, state, generation):
        expected = state['target'] if state['phase'] in {'COMMITTED','DONE'} else state['previous']
        if state['phase'] not in {'COMMITTED','DONE','ROLLED_BACK'} or generation != expected:
            raise RecoveryControlError('RECOVERY_RELEASE_WITHOUT_DURABLE_DECISION')
        selected = self.deployment.read()
        if selected['generation'] != generation:
            raise RecoveryControlError('RECOVERY_SELECTED_GENERATION_MISMATCH')
        # Restart after durable commit may need to finish the probe stage. Never
        # choose the previous generation after the target has been committed.
        if selected['mode'] != 'OPEN':
            self.probe(state, generation)
            selected = self.deployment.select(generation,'OPEN')
        self._wait(lambda: self._acknowledged(selected, ROLES, 'RUNNING'), 'RECOVERY_RELEASE_INCOMPLETE')
