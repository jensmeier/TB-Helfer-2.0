package de.tbhelfer

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import de.tbhelfer.server.Schema13Worker
import de.tbhelfer.server.Server1DeltaPoller
import de.tbhelfer.server.Server1DeltaWorker
import de.tbhelfer.server.SelectiveSyncWorker
import de.tbhelfer.server.ServerConfig
import de.tbhelfer.server.SyncWorker
import de.tbhelfer.util.StorageCleanupScheduler

class TBHelferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialisieren(this)
        ServerConfig.initialize(this)

        // Nachtmodus früh anwenden (damit die App direkt korrekt startet)
        // Wichtig: kein runBlocking/DataStore-IO im App-Start.
        // Wir lesen den NightMode synchron aus einem kleinen SharedPrefs-Cache,
        // der bei jeder Einstellung-Änderung aktualisiert wird.
        val cached = AnsichtEinstellungenSpeicher.readCachedNightMode(this)
        val mode = when (cached) {
            AnsichtEinstellungenSpeicher.NightMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AnsichtEinstellungenSpeicher.NightMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            AnsichtEinstellungenSpeicher.NightMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        // Periodische Speicher-Bereinigung (Orphans/Thumb-Leichen/DB-Leichen)
        StorageCleanupScheduler.scheduleWeekly(this)

        // Alte periodische Vollsync-Zeitpläne aus Vorversionen entfernen.
        // "Neu laden" bleibt der bewusste vollständige Recovery-Abgleich.
        SyncWorker.schedule(this)
        Schema13Worker.schedule(this)

        // Persistente Dirty-Scopes nach Prozess-/Geräteneustart wieder aufnehmen.
        SelectiveSyncWorker.enqueueIfPending(this)

        // Server->Android sofort nach App-Start nachziehen. Dies ist ein gezielter Changefeed-
        // Catch-up und KEIN globaler Vollsync.
        Server1DeltaWorker.enqueue(this)
        installDeltaLifecycle()
        installNetworkReturnWakeup()
    }

    private fun installDeltaLifecycle() {
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                if (startedActivities == 1) {
                    Server1DeltaWorker.enqueue(this@TBHelferApp)
                    Server1DeltaPoller.start(this@TBHelferApp)
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    Server1DeltaPoller.stop()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                Server1DeltaWorker.enqueue(this@TBHelferApp)
                SelectiveSyncWorker.enqueueIfPending(this@TBHelferApp)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun installNetworkReturnWakeup() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        SelectiveSyncWorker.enqueueIfPending(this@TBHelferApp)
                        Server1DeltaWorker.enqueue(this@TBHelferApp)
                    }
                }
            )
        }
    }
}
