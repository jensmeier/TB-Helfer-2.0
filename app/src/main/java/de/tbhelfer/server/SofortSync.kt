package de.tbhelfer.server

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Kurzer, entprellter Sofort-Trigger fuer neue Baustellendaten/Bilder.
 *
 * Er ersetzt NICHT die Upload-Queue und auch nicht den periodischen Sync. Mehrere Fotos in kurzer
 * Folge erzeugen dank unique KEEP nur einen Lauf. Ohne Netz wartet WorkManager; ohne aktiviertes
 * Gerät beendet sich [SyncWorker] ohne Fehler.
 */
object SofortSync {
    private const val UNIQUE_NAME = "tbhelfer-server-sync-now"

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
