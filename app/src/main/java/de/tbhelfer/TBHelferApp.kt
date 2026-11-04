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
    override fun attachBaseContext(base: android.content.Context) {
        de.tbhelfer.server.recovery.RecoveryWorkspace.initialize(base)
        super.attachBaseContext(de.tbhelfer.server.recovery.RecoveryWorkspace.wrap(base))
    }
    private var foregroundActivity: java.lang.ref.WeakReference<Activity>? = null
    private fun redirectRecovery(activity: Activity): Boolean {
        if (!de.tbhelfer.server.recovery.RecoveryWorkspace.blocked() ||
            activity is de.tbhelfer.server.recovery.RecoveryActivity ||
            activity is de.tbhelfer.server.recovery.RecoveryRestartActivity) return false
        foregroundActivity = null
        activity.startActivity(android.content.Intent(activity, de.tbhelfer.server.recovery.RecoveryActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        return true
    }

    override fun onCreate() {
        super.onCreate()
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)?.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        if (processName?.endsWith(":tb_recovery_restart") == true) return
        de.tbhelfer.server.recovery.RecoveryProtocol.onBlocked = {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                foregroundActivity?.get()?.let { redirectRecovery(it) }
            }
        }

        ServiceLocator.initialisieren(this)
        ServerConfig.initialize(this)
        de.tbhelfer.speech.SpeechWidgetProvider.install(this)

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

        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) {
            installDeltaLifecycle()
            return
        }

        // Periodische Speicher-Bereinigung (Orphans/Thumb-Leichen/DB-Leichen)
        StorageCleanupScheduler.scheduleWeekly(this)

        // Alte periodische Vollsync-Zeitpläne aus Vorversionen entfernen.
        // "Neu laden" bleibt der bewusste vollständige Recovery-Abgleich.
        SyncWorker.schedule(this)
        Schema13Worker.schedule(this)

        // Persistente Dirty-Scopes nach Prozess-/Geräteneustart wieder aufnehmen.
        de.tbhelfer.server.BtbLifecycleWorker.enqueue(this, replaceWaiting = true)
        SelectiveSyncWorker.enqueueIfPending(this)

        // Server->Android sofort nach App-Start nachziehen. Dies ist ein gezielter Changefeed-
        // Catch-up und KEIN globaler Vollsync.
        Server1DeltaWorker.enqueue(this)
        Server1DeltaWorker.schedule(this)
        installDeltaLifecycle()
        installNetworkReturnWakeup()
        if (!de.tbhelfer.server.recovery.RecoveryWorkspace.blocked() &&
            de.tbhelfer.server.recovery.RecoveryWorkspace.read().optBoolean("initial_sync")) {
            SyncWorker.enqueueManual(this)
        }
    }

    private fun installDeltaLifecycle() {
        var startedActivities = 0
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities += 1
                if (startedActivities == 1 && !de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) {
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
                de.tbhelfer.speech.SpeechWidgetProvider.refresh(this@TBHelferApp)
                foregroundActivity = java.lang.ref.WeakReference(activity)
                if (redirectRecovery(activity) || activity is de.tbhelfer.server.recovery.RecoveryActivity) return

                Server1DeltaWorker.enqueue(this@TBHelferApp)
                de.tbhelfer.server.BtbLifecycleWorker.enqueue(this@TBHelferApp, replaceWaiting = true)
                SelectiveSyncWorker.enqueueIfPending(this@TBHelferApp)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) {
                if (foregroundActivity?.get() === activity) foregroundActivity = null
            }
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
                        if (de.tbhelfer.server.recovery.RecoveryWorkspace.blocked()) return
                        de.tbhelfer.server.BtbLifecycleWorker.enqueue(this@TBHelferApp, replaceWaiting = true)
                        SelectiveSyncWorker.enqueueIfPending(this@TBHelferApp)
                        Server1DeltaWorker.enqueue(this@TBHelferApp)
                    }
                }
            )
        }
    }
}




