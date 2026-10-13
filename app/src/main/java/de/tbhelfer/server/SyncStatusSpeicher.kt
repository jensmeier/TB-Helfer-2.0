package de.tbhelfer.server

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Kleiner gerätelokaler Status für die letzte erfolgreiche Server-Synchronisation. */
object SyncStatusSpeicher {
    private const val PREFS = "tbhelfer_sync_status_v1"
    private const val KEY_LAST_SUCCESS = "last_success_epoch_ms"

    fun letzterErfolgEpochMs(context: Context): Long? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_LAST_SUCCESS, 0L)
        .takeIf { it > 0L }

    fun erfolgMerken(context: Context, epochMs: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SUCCESS, epochMs)
            .apply()
    }

    fun anzeige(context: Context): String {
        val epochMs = letzterErfolgEpochMs(context) ?: return "Noch nicht synchronisiert"
        val zone = ZoneId.systemDefault()
        val value = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = LocalDate.now(zone)
        val date = when (value.toLocalDate()) {
            today -> "heute"
            today.minusDays(1) -> "gestern"
            else -> value.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }
        val time = value.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        return "$date $time"
    }
}
