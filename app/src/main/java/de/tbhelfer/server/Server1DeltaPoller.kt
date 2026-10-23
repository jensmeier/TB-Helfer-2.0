package de.tbhelfer.server

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Long-Poll waehrend einer aktiven App-Phase.
 *
 * Ein einzelner /changes/wait-Request darf bis 25 s offen sein. Fehler verwenden einen
 * begrenzten Backoff; derselbe persistente Cursor bleibt dabei unveraendert und wird erneut
 * versucht. Stoppen der UI vernichtet keinen Cursor und keine Pending-Daten.
 */
object Server1DeltaPoller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var job: Job? = null

    fun start(context: Context) {
        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return
        val appContext = context.applicationContext
        synchronized(this) {
            if (job?.isActive == true) return
            job = scope.launch {
                var errorBackoffMs = 2_000L
                while (isActive) {
                    when (
                        Server1DeltaSync(appContext).synchronize(
                            firstWaitSeconds = 25,
                            maxPages = 50
                        )
                    ) {
                        is Server1DeltaSync.Result.Success -> {
                            errorBackoffMs = 2_000L
                            // Direkt in den naechsten Long-Poll gehen.
                        }
                        Server1DeltaSync.Result.Unsupported -> {
                            delay(60_000L)
                        }
                        Server1DeltaSync.Result.SessionInvalid -> {
                            break
                        }
                        is Server1DeltaSync.Result.Error -> {
                            delay(errorBackoffMs)
                            errorBackoffMs = (errorBackoffMs * 2L).coerceAtMost(5L * 60L * 1000L)
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(this) {
            job?.cancel()
            job = null
        }
    }
}

