package de.tbhelfer.vermessung

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Einfache Live-Standortquelle auf Basis des Android LocationManager.
 * (ohne Google Play Services Abhängigkeit)
 */
object StandortLiveQuelle {

    data class GnssZusammenfassung(
        val gps: Int = 0,
        val galileo: Int = 0,
        val glonass: Int = 0,
        val beidou: Int = 0,
        val qzss: Int = 0,
        val navic: Int = 0,
        val sbas: Int = 0,
        val unbekannt: Int = 0,
        val usedInFix: Int = 0,
        val total: Int = 0
    ) {
        fun alsText(): String {
            return buildString {
                append("GNSS GPS ").append(gps)
                append(" · Galileo ").append(galileo)
                append(" · GLONASS ").append(glonass)
                append(" · BeiDou ").append(beidou)
                if (qzss > 0) append(" · QZSS ").append(qzss)
                if (navic > 0) append(" · NavIC ").append(navic)
                append(" · Fix ").append(usedInFix)
            }
        }
    }

    fun hatStandortBerechtigung(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun standortFlow(
        context: Context,
        minTimeMs: Long = 1000L,
        minDistanceM: Float = 0.5f,
        emitLastKnown: Boolean = true,
        glaettungFenster: Int = 5
    ): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!hatStandortBerechtigung(context)) {
            close(IllegalStateException("Standortberechtigung fehlt"))
            return@callbackFlow
        }

        val puffer = ArrayDeque<Location>()
        val listener = LocationListener { loc ->
            val fix = glaetteLocation(loc, puffer, glaettungFenster)
            de.tbhelfer.widget.WidgetPositionStore.saveApp(context.applicationContext, fix)
            trySend(fix).isSuccess
        }

        val gpsEnabled = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

        runCatching {
            lm.requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, Looper.getMainLooper())
        }.onFailure { e ->
            close(e)
            return@callbackFlow
        }

        if (emitLastKnown) {
            runCatching { lm.getLastKnownLocation(provider) }.getOrNull()?.let {
                val fix = glaetteLocation(it, puffer, glaettungFenster)
                de.tbhelfer.widget.WidgetPositionStore.saveApp(context.applicationContext, fix)
                trySend(fix).isSuccess
            }
        }

        awaitClose {
            runCatching { lm.removeUpdates(listener) }
        }
    }

    @SuppressLint("MissingPermission")
    fun gnssStatusFlow(context: Context): Flow<GnssZusammenfassung> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!hatStandortBerechtigung(context)) {
            close(IllegalStateException("Standortberechtigung fehlt"))
            return@callbackFlow
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            trySend(GnssZusammenfassung())
            close()
            return@callbackFlow
        }

        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var gps = 0
                var galileo = 0
                var glonass = 0
                var beidou = 0
                var qzss = 0
                var navic = 0
                var sbas = 0
                var unbekannt = 0
                var usedInFix = 0
                for (i in 0 until status.satelliteCount) {
                    when (status.getConstellationType(i)) {
                        GnssStatus.CONSTELLATION_GPS -> gps++
                        GnssStatus.CONSTELLATION_GALILEO -> galileo++
                        GnssStatus.CONSTELLATION_GLONASS -> glonass++
                        GnssStatus.CONSTELLATION_BEIDOU -> beidou++
                        GnssStatus.CONSTELLATION_QZSS -> qzss++
                        GnssStatus.CONSTELLATION_SBAS -> sbas++
                        else -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                status.getConstellationType(i) == GnssStatus.CONSTELLATION_IRNSS
                            ) {
                                navic++
                            } else {
                                unbekannt++
                            }
                        }
                    }
                    if (status.usedInFix(i)) usedInFix++
                }
                trySend(
                    GnssZusammenfassung(
                        gps = gps,
                        galileo = galileo,
                        glonass = glonass,
                        beidou = beidou,
                        qzss = qzss,
                        navic = navic,
                        sbas = sbas,
                        unbekannt = unbekannt,
                        usedInFix = usedInFix,
                        total = status.satelliteCount
                    )
                ).isSuccess
            }
        }

        runCatching {
            lm.registerGnssStatusCallback(callback, android.os.Handler(Looper.getMainLooper()))
        }.onFailure {
            trySend(GnssZusammenfassung())
        }

        awaitClose {
            runCatching { lm.unregisterGnssStatusCallback(callback) }
        }
    }

    private fun glaetteLocation(neu: Location, puffer: ArrayDeque<Location>, glaettungFenster: Int): Location {
        if (neu.hasAccuracy() && neu.accuracy > 25f) return neu
        val fenster = glaettungFenster.coerceAtLeast(1)
        puffer.addLast(Location(neu))
        while (puffer.size > fenster) puffer.removeFirst()
        val list = puffer.toList()
        val avgLat = list.map { it.latitude }.average()
        val avgLon = list.map { it.longitude }.average()
        val avgAlt = list.mapNotNull { if (it.hasAltitude()) it.altitude else null }.averageOrNull()
        val medianAcc = list.mapNotNull { if (it.hasAccuracy()) it.accuracy else null }.sorted().medianOrNull()
        return Location(neu).apply {
            latitude = avgLat
            longitude = avgLon
            if (avgAlt != null) altitude = avgAlt
            if (medianAcc != null) accuracy = medianAcc
        }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
    private fun List<Float>.medianOrNull(): Float? {
        if (isEmpty()) return null
        val mid = size / 2
        return if (size % 2 == 1) this[mid] else (this[mid - 1] + this[mid]) / 2f
    }
}
