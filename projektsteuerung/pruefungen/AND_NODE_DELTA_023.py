from pathlib import Path
b=Path(__file__).resolve().parents[2] / 'app/src/main/java/de/tbhelfer'
r=lambda p:(b/p).read_text()
checks={
'feed requests node events':'"WORKSITE_NODE"' in r('server/Server1DeltaApi.kt'),
'structure apply precedes BTB resolution':r('server/Server1DeltaSync.kt').index('worksiteStructureSync.applyServerStateToLocal')<r('server/Server1DeltaSync.kt').index('val contextsByWorksite'),
'cursor is committed after apply':r('server/Server1DeltaSync.kt').index('applyPage(token, page)')<r('server/Server1DeltaSync.kt').index('Server1DeltaCursorStore.commit'),
'no pending-node automatic rebase':'replaceCollabPendingUpsert' not in r('datenbank/WorksiteStructureSync.kt'),
'local snapshot race checked':'before == localWorksiteDao.alleKnotenZuRootSofort(root.id)' in r('datenbank/WorksiteStructureSync.kt'),
'local and remote writes share mutex':all('StructureSyncStore.mutationMutex.withLock' in r(p) for p in ['datenbank/BaustellenBaumRepository.kt','datenbank/WorksiteStructureSync.kt']),
'node pending and conflicts block apply':'hasPendingNodes(bound.context.worksiteServerId)' in r('datenbank/WorksiteStructureSync.kt'),
'unknown base blocks write':'WorksiteDeltaPolicy.knownBasis' in r('datenbank/WorksiteStructureSync.kt'),
'no null-media broad sync': 'btbServerIds = setOf(currentServerBtbId)' in r('server/SelectiveSyncWorker.kt') and r('server/SelectiveSyncWorker.kt').index('if (currentServerBtbId.isNullOrBlank())')<r('server/SelectiveSyncWorker.kt').index('val media = BtbTeamMediaSync'),
'ack after media and local apply':r('server/SelectiveSyncWorker.kt').index('confirmBtbIfClean')>r('server/SelectiveSyncWorker.kt').index('val applied = runCatching'),
'newer save generation blocks ack':'p.getLong("latest:$key", 0L) == scope.generation' in r('server/SelectiveSyncStore.kt'),
'no short-name override in BTB':'val base = k.knotenName?.takeIf' not in r('ui/bautagebuch/BautagebuchFragment.kt'),
'five BTB label paths share formatter':r('ui/bautagebuch/BautagebuchFragment.kt').count('BaustellenObjektLogik.unterbaustellenName(k)')==5,
'existing key bind retained':'clientNodeKey = server.clientNodeKey' in r('datenbank/WorksiteStructureSync.kt'),
'recovery barrier retained':'RecoveryWorkspace.blocked()' in r('server/SelectiveSyncWorker.kt'),
'save completion cannot lose wakeup':'ExistingWorkPolicy.APPEND_OR_REPLACE' in r('server/SelectiveSyncWorker.kt'),
}
for name,ok in checks.items(): print(('PASS ' if ok else 'FAIL ')+name)
assert all(checks.values())
print(f'{len(checks)}/{len(checks)} source integration checks passed (not compiler/runtime tests).')

