package de.tbhelfer.vermessung

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Koordinatenwandler fuer DB_REF2016 Zone 4 (EPSG:9934) nach WGS84/GPS.
 *
 * Eingabe: DB_REF Gauss-Krueger Zone 4, Achsenreihenfolge E/N.
 * Ausgabe: WGS84 Latitude/Longitude fuer Android/Google Maps/GPS.
 *
 * Wichtig: Die gelieferten TRA/GVP/KOR-Dateien enthalten keine fertigen GPS-Koordinaten,
 * sondern DB_REF-X/Y. Diese Klasse macht daraus Lat/Lon fuer die spaetere Naeheberechnung.
 */
object DbRefKoordinatenwandler {

    data class Wgs84Position(
        val lat: Double,
        val lon: Double
    )

    private const val A_BESSEL = 6_377_397.155
    private const val INV_F_BESSEL = 299.1528128
    private const val A_GRS80 = 6_378_137.0
    private const val INV_F_GRS80 = 298.257222101

    private const val FALSE_EASTING = 4_500_000.0
    private const val FALSE_NORTHING = 0.0
    private const val SCALE = 1.0
    private val LON0_RAD = Math.toRadians(12.0)

    // EPSG:5826 DB_REF -> ETRS89, Coordinate Frame Rotation.
    private const val TX = 584.9636
    private const val TY = 107.7175
    private const val TZ = 413.8067
    private const val RX_ARCSEC = -1.1155
    private const val RY_ARCSEC = -0.2824
    private const val RZ_ARCSEC = 3.1384
    private const val SCALE_PPM = 7.9922

    fun dbRef2016Zone4ZuWgs84OrNull(xDbRef: Double, yDbRef: Double): Wgs84Position? {
        if (!xDbRef.isFinite() || !yDbRef.isFinite()) return null
        if (xDbRef !in 3_000_000.0..6_000_000.0 || yDbRef !in 4_000_000.0..6_500_000.0) return null

        val dbRefGeod = inverseTransverseMercator(xDbRef, yDbRef)
        val dbRefXyz = geodeticToXyz(dbRefGeod.latRad, dbRefGeod.lonRad, 0.0, A_BESSEL, INV_F_BESSEL)
        val etrsXyz = helmertDbRefToEtrs89(dbRefXyz)
        val wgsGeod = xyzToGeodetic(etrsXyz.x, etrsXyz.y, etrsXyz.z, A_GRS80, INV_F_GRS80)

        val lat = Math.toDegrees(wgsGeod.latRad)
        val lon = Math.toDegrees(wgsGeod.lonRad)
        if (!lat.isFinite() || !lon.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Wgs84Position(lat = lat, lon = lon)
    }

    private fun inverseTransverseMercator(easting: Double, northing: Double): GeodRad {
        val f = 1.0 / INV_F_BESSEL
        val e2 = 2.0 * f - f * f
        val e4 = e2 * e2
        val e6 = e4 * e2
        val ep2 = e2 / (1.0 - e2)

        val meridionalArc = (northing - FALSE_NORTHING) / SCALE
        val mu = meridionalArc / (A_BESSEL * (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0))
        val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))
        val e1_2 = e1 * e1
        val e1_3 = e1_2 * e1
        val e1_4 = e1_2 * e1_2

        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1_3 / 32.0) * sin(2.0 * mu) +
            (21.0 * e1_2 / 16.0 - 55.0 * e1_4 / 32.0) * sin(4.0 * mu) +
            (151.0 * e1_3 / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1_4 / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val n1 = A_BESSEL / sqrt(1.0 - e2 * sinPhi1 * sinPhi1)
        val r1 = A_BESSEL * (1.0 - e2) / (1.0 - e2 * sinPhi1 * sinPhi1).pow(1.5)
        val t1 = tanPhi1 * tanPhi1
        val c1 = ep2 * cosPhi1 * cosPhi1
        val d = (easting - FALSE_EASTING) / (n1 * SCALE)

        val latRad = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * d.pow(4.0) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * d.pow(6.0) / 720.0
            )

        val lonRad = LON0_RAD + (
            d -
                (1.0 + 2.0 * t1 + c1) * d.pow(3.0) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * d.pow(5.0) / 120.0
            ) / cosPhi1

        return GeodRad(latRad = latRad, lonRad = lonRad)
    }

    private fun geodeticToXyz(latRad: Double, lonRad: Double, h: Double, a: Double, inverseFlattening: Double): Xyz {
        val f = 1.0 / inverseFlattening
        val e2 = 2.0 * f - f * f
        val sinLat = sin(latRad)
        val cosLat = cos(latRad)
        val n = a / sqrt(1.0 - e2 * sinLat * sinLat)
        return Xyz(
            x = (n + h) * cosLat * cos(lonRad),
            y = (n + h) * cosLat * sin(lonRad),
            z = (n * (1.0 - e2) + h) * sinLat
        )
    }

    private fun helmertDbRefToEtrs89(source: Xyz): Xyz {
        val rx = arcSecondsToRad(RX_ARCSEC)
        val ry = arcSecondsToRad(RY_ARCSEC)
        val rz = arcSecondsToRad(RZ_ARCSEC)
        val scale = 1.0 + SCALE_PPM * 1e-6

        // EPSG 9607 Coordinate Frame Rotation, Parameterwerte fuer DB_REF -> ETRS89.
        return Xyz(
            x = TX + scale * (source.x + rz * source.y - ry * source.z),
            y = TY + scale * (-rz * source.x + source.y + rx * source.z),
            z = TZ + scale * (ry * source.x - rx * source.y + source.z)
        )
    }

    private fun xyzToGeodetic(x: Double, y: Double, z: Double, a: Double, inverseFlattening: Double): GeodRad {
        val f = 1.0 / inverseFlattening
        val e2 = 2.0 * f - f * f
        val b = a * (1.0 - f)
        val ep2 = (a * a - b * b) / (b * b)
        val p = sqrt(x * x + y * y)
        val theta = atan2(z * a, p * b)
        val lon = atan2(y, x)
        var lat = atan2(
            z + ep2 * b * sin(theta).pow(3.0),
            p - e2 * a * cos(theta).pow(3.0)
        )

        repeat(3) {
            val n = a / sqrt(1.0 - e2 * sin(lat) * sin(lat))
            val h = p / cos(lat) - n
            lat = atan2(z, p * (1.0 - e2 * n / (n + h)))
        }
        return GeodRad(latRad = lat, lonRad = lon)
    }

    private fun arcSecondsToRad(value: Double): Double = value * PI / (180.0 * 3600.0)

    private data class GeodRad(val latRad: Double, val lonRad: Double)
    private data class Xyz(val x: Double, val y: Double, val z: Double)
}
