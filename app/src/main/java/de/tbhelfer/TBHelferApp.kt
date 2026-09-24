package de.tbhelfer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import de.tbhelfer.server.Schema13Worker
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
    }
}
