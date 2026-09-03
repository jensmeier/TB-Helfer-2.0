package de.tbhelfer.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Speichert die letzte innerhalb der App bekannte Position ohne dauerndes Hintergrund-GPS. */
object WidgetPositionStore {
    data class Position(
        val lat: Double,
        val lon: Double,
        val accuracyM: Float?,
        val epochMs: Long,
        val fachzeile: String? = null,
        val kontext: String? = null
    )

    private const val PREF = "tb_helfer_widget_position_v1"
    private const val APP = "app_"
    private const val MANUAL = "manual_"
    private const val UPDATE_THROTTLE_MS = 60_000L

    fun saveApp(context: Context, loc: Location, fachzeile: String? = null, kontext: String? = null) {
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val resolvedFach = fachzeile?.takeIf { it.isNotBlank() } ?: p.getString(APP + "fach", null)
        val resolvedKontext = kontext?.takeIf { it.isNotBlank() } ?: p.getString(APP + "kontext", null)
        val richerInformation = (!fachzeile.isNullOrBlank() && fachzeile != p.getString(APP + "fach", null)) ||
            (!kontext.isNullOrBlank() && kontext != p.getString(APP + "kontext", null))
        val lastSave = p.getLong("last_app_save", 0L)
        if (now - lastSave < 5_000L && !richerInformation) return
        p.edit().putLong("last_app_save", now).apply()
        // Ein allgemeiner GPS-Fix darf die zuletzt berechnete Gleis-/KM-Zeile nicht löschen.
        save(context, APP, loc, resolvedFach, resolvedKontext)
        val last = p.getLong("last_widget_update", 0L)
        if (now - last >= UPDATE_THROTTLE_MS || richerInformation) {
            p.edit().putLong("last_widget_update", now).apply()
            TBHelferWidgetUpdater.updateAll(context)
        }
    }

    fun saveManual(context: Context, loc: Location, fachzeile: String? = null, kontext: String? = null) {
        save(context, MANUAL, loc, fachzeile, kontext)
        TBHelferWidgetUpdater.updateAll(context)
    }

    private fun save(context: Context, pre: String, loc: Location, fachzeile: String?, kontext: String?) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(pre + "lat", java.lang.Double.doubleToRawLongBits(loc.latitude))
            .putLong(pre + "lon", java.lang.Double.doubleToRawLongBits(loc.longitude))
            .putFloat(pre + "acc", if (loc.hasAccuracy()) loc.accuracy else Float.NaN)
            .putLong(pre + "time", if (loc.time > 0L) loc.time else System.currentTimeMillis())
            .putString(pre + "fach", fachzeile)
            .putString(pre + "kontext", kontext)
            .apply()
    }

    fun load(context: Context, mode: WidgetPreferences.PositionModus): Position? {
        val pre = if (mode == WidgetPreferences.PositionModus.NUR_MANUELL) MANUAL else APP
        val p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.contains(pre + "lat") || !p.contains(pre + "lon")) return null
        return Position(
            lat = java.lang.Double.longBitsToDouble(p.getLong(pre + "lat", 0L)),
            lon = java.lang.Double.longBitsToDouble(p.getLong(pre + "lon", 0L)),
            accuracyM = p.getFloat(pre + "acc", Float.NaN).takeUnless { it.isNaN() },
            epochMs = p.getLong(pre + "time", 0L),
            fachzeile = p.getString(pre + "fach", null),
            kontext = p.getString(pre + "kontext", null)
        )
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun refreshManualFromSystem(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return false
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
        val best = candidates.maxByOrNull { it.time } ?: return false
        saveManual(context, best)
        return true
    }

    fun timeText(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return runCatching {
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
        }.getOrDefault("")
    }
}
