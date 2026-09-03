package de.tbhelfer.vermessung

import kotlin.math.*

/**
 * Einfache Geometrie/Geo-Hilfen für TB-Helfer.
 *
 * Wichtig: Smartphone-GPS ist nicht zentimetergenau. Diese Hilfen dienen dazu,
 * plausible km-Werte für Baudokumentation zu berechnen.
 */
object GeoMath {

    /**
     * Projektionsreferenz für eine lokale Meter-Ebene (Equirectangular Approx.).
     * Für Baustellenlängen im Bereich einiger 10 km ausreichend.
     */
    data class Ref(val lat0Deg: Double, val lon0Deg: Double) {
        val lat0Rad: Double = Math.toRadians(lat0Deg)
        val cosLat0: Double = cos(lat0Rad)
    }

    data class XY(val x: Double, val y: Double)

    private const val ERDE_R = 6378137.0 // WGS84

    fun toXY(latDeg: Double, lonDeg: Double, ref: Ref): XY {
        val latRad = Math.toRadians(latDeg)
        val lonRad = Math.toRadians(lonDeg)
        val lon0Rad = Math.toRadians(ref.lon0Deg)
        val lat0Rad = Math.toRadians(ref.lat0Deg)
        val x = (lonRad - lon0Rad) * ERDE_R * ref.cosLat0
        val y = (latRad - lat0Rad) * ERDE_R
        return XY(x, y)
    }

    data class Projection(val t: Double, val closest: XY, val dist: Double)

    /** Projektion von P auf Segment AB (in Metern). t wird auf [0,1] geclamped. */
    fun projectPointToSegment(p: XY, a: XY, b: XY): Projection {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val apx = p.x - a.x
        val apy = p.y - a.y
        val abLen2 = abx * abx + aby * aby
        val tRaw = if (abLen2 <= 1e-9) 0.0 else (apx * abx + apy * aby) / abLen2
        val t = tRaw.coerceIn(0.0, 1.0)
        val cx = a.x + t * abx
        val cy = a.y + t * aby
        val dx = p.x - cx
        val dy = p.y - cy
        val dist = sqrt(dx * dx + dy * dy)
        return Projection(t, XY(cx, cy), dist)
    }
}
