package de.tbhelfer.server

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Trigger für Fachänderungen.
 *
 * BTB mit stabiler btbId läuft selektiv über [SelectiveSyncWorker]. Der generische Pfad bleibt
 * während der schrittweisen Umstellung für noch nicht selektiv angebundene Fachbereiche erhalten.
 */
object SofortSync {
    private const val UNIQUE_NAME = "tbhelfer-server-sync-now"

    fun btbAnstossen(
        context: Context,
        localBaustelleId: Long,
        clientBtbId: String
    ) {
        val appContext = context.applicationContext
        if (clientBtbId.isBlank()) {
            anstossen(appContext)
            return
        }
        SelectiveSyncStore.markBtbDirty(
            appContext,
            localBaustelleId,
            clientBtbId
        )
        SelectiveSyncWorker.enqueue(appContext)
    }

    fun anstossen(context: Context) {
        val appContext = context.applicationContext
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        // Schema-13-Daten laufen bis zur spaeteren Worker-Konsolidierung in einem kleinen eigenen
        // Worker. Damit derselbe Sofort-Trigger auch Unterbaustellen/Streckendaten/Tombstones zeitnah
        // verarbeitet, wird er hier gemeinsam angestossen.
        Schema13Worker.enqueueNow(appContext)
    }
}
