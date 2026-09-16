package de.tbhelfer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import de.tbhelfer.einstellungen.AnsichtEinstellungenSpeicher
import de.tbhelfer.server.Schema13Worker
import de.tbhelfer.server.SyncWorker
import de.tbhelfer.util.StorageCleanupScheduler

class TBHelferApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialisieren(this)

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

        // NAS-/Cloudflare-Sync nur mit Netzwerk und nicht bei niedrigem Akkustand.
        // Der manuelle „Neu laden“-Button bleibt für sofortiges Synchronisieren zuständig.
        SyncWorker.schedule(this)
        // Schema 13 bleibt vorerst technisch getrennt, nutzt aber denselben Zeitplan/Netzregeln:
        // Unterbaustellen, mobile Streckendaten und globale Baustellen-Loeschtombstones.
        Schema13Worker.schedule(this)
    }
}
