"""Targeted source integration checks; not a build or device/E2E test."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
base = root / "app/src/main/java/de/tbhelfer"
read = lambda p: (base / p).read_text(encoding="utf-8")
outbox = read("server/BtbLifecycleOutbox.kt")
worker = read("server/BtbLifecycleWorker.kt")
api = read("server/Server1BtbApi.kt")
ui = read("ui/bautagebuch/BtbModernLifecycleInstaller.kt")
coordinator = read("server/BtbLifecycleCoordinator.kt")
checks = {
    "disk commit before enqueue": 'putString(btbId, encode(i)).commit()' in outbox,
    "duplicate retains request": 'previous != null && !force' in outbox,
    "identity binding": 'intent.owner != owner' in worker and 'RecoveryWorkspace.wrap' in outbox,
    "recovery barrier": 'RecoveryWorkspace.blocked()' in worker,
    "same id on replay": '(clientChangeId ?: "android-lifecycle-' in api and api.count('clientChangeId = clientChangeId') >= 3,
    "read before post": worker.index('coordinator.prepareAccess') < worker.index('coordinator.sendPrepared'),
    "media before final read": coordinator.index('BtbTeamMediaSync(appContext).synchronize') < coordinator.index('return loadAccess()', coordinator.index('BtbTeamMediaSync(appContext).synchronize')),
    "preserve unknown confirmation": 'Statusbestätigung ausstehend:' in api and 'Result.retry()' in worker,
    "dirty generation guard": 'removeBtbIfUnchanged(ctx, before)' in worker and 'isBtbDirty(ctx, intent.localId, intent.btbId)' in worker,
    "no silent rebase": 'if (dirty || pending != null)' in coordinator and 'original entity/base JSON' in coordinator,
    "conflict before export": coordinator.index('STATE_CONFLICT') < coordinator.index('val exported = bridge.exportBtbChange'),
    "click guard": 'if (!closingViews.add(root)) return' in ui,
    "strict focused save": 'requireConfirmedSave = true' in ui,
    "no automatic resume in UI": 'coordinator.shiftResume' not in ui,
    "force only explicit dialog": '.setPositiveButton("Trotzdem abschließen")' in ui and 'previous?.participantsBlocked == true' in outbox,
    "start recovery": 'BtbLifecycleWorker.enqueue(this)' in read('ui/HauptActivity.kt'),
    "visible pending": 'Abschluss ausstehend' in ui and 'Serverbestätigung ausstehend' in outbox,
}
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
version = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)[1]
build = re.search(r'versionCode\s*=\s*(\d+)', gradle)[1]
release = (root / f'projektsteuerung/releases/TB-Helfer_{version}_{build}.md').read_text(encoding='utf-8')
checks['release consistency'] = f'VERSION: {version}' in release and f'BUILD: {build}' in release
for name, passed in checks.items():
    print(('PASS' if passed else 'FAIL') + ': ' + name)
assert all(checks.values()), 'Lifecycle source integration failed'
print(f'{len(checks)}/{len(checks)} SOURCE checks passed (not runtime proof)')
