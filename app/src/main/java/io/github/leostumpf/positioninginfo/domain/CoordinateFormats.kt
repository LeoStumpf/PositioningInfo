// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** A position on the UTM grid: zone, latitude band and metres east and north within the zone. */
data class UtmCoordinate(val zone: Int, val band: Char, val easting: Double, val northing: Double) {
    /** e.g. "33U 389918 5819699" (metres, rounded) */
    override fun toString(): String =
        "$zone$band ${easting.roundToLong()} ${northing.roundToLong()}"
}

/**
 * The same WGS84 position written the ways people actually exchange it: decimal and
 * sexagesimal degrees, UTM and MGRS (maps, search and rescue), Plus Codes and Maidenhead
 * locators (radio amateurs).
 *
 * All of it is plain arithmetic, so it works offline, which the app requires.
 */
object CoordinateFormats {

    fun decimal(lat: Double, lon: Double): String {
        val la = String.format(Locale.ROOT, "%.6f", abs(lat))
        val lo = String.format(Locale.ROOT, "%.6f", abs(lon))
        return "$la° ${hemisphere(lat, la, 'N', 'S')}, $lo° ${hemisphere(lon, lo, 'E', 'W')}"
    }

    fun dms(lat: Double, lon: Double): String =
        "${dmsPart(lat, 'N', 'S')}, ${dmsPart(lon, 'E', 'W')}"

    /**
     * Rounds once, in tenths of a second, and splits afterwards. Rounding the seconds on
     * their own would print 59.95″ as "60.0″" instead of carrying into the minutes.
     */
    private fun dmsPart(value: Double, positive: Char, negative: Char): String {
        val tenths = (abs(value) * 36_000).roundToLong()
        val degrees = tenths / 36_000
        val minutes = tenths % 36_000 / 600
        val secondTenths = tenths % 600
        val letter = if (value < 0 && tenths != 0L) negative else positive
        return "$degrees°$minutes′${secondTenths / 10}.${secondTenths % 10}″ $letter"
    }

    /** "0.000000° S" would be odd: a value that prints as zero takes the positive letter. */
    private fun hemisphere(value: Double, printed: String, positive: Char, negative: Char): Char =
        if (value < 0 && printed.any { it in '1'..'9' }) negative else positive

    // ---- UTM ----

    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500_000.0
    private const val FALSE_NORTHING_SOUTH = 10_000_000.0
    private const val BANDS = "CDEFGHJKLMNPQRSTUVWX"

    // Krüger's series in the third flattening n, to n⁶ (Karney 2011): sub-millimetre
    // within a UTM zone, where the classic Snyder series drifts by centimetres.
    private val N = F / (2 - F)
    private val RECTIFYING_RADIUS = A / (1 + N) * (1 + N * N / 4 + N.pow(4) / 64 + N.pow(6) / 256)
    private val ALPHA = doubleArrayOf(
        N / 2 - 2 * N.pow(2) / 3 + 5 * N.pow(3) / 16 + 41 * N.pow(4) / 180 -
            127 * N.pow(5) / 288 + 7891 * N.pow(6) / 37800,
        13 * N.pow(2) / 48 - 3 * N.pow(3) / 5 + 557 * N.pow(4) / 1440 +
            281 * N.pow(5) / 630 - 1983433 * N.pow(6) / 1935360,
        61 * N.pow(3) / 240 - 103 * N.pow(4) / 140 + 15061 * N.pow(5) / 26880 +
            167603 * N.pow(6) / 181440,
        49561 * N.pow(4) / 161280 - 179 * N.pow(5) / 168 + 6601661 * N.pow(6) / 7257600,
        34729 * N.pow(5) / 80640 - 3418889 * N.pow(6) / 1995840,
        212378941 * N.pow(6) / 319334400,
    )
    /** First eccentricity, written in n. */
    private val E = 2 * sqrt(N) / (1 + N)

    private fun Double.pow(k: Int): Double = Math.pow(this, k.toDouble())

    fun utm(lat: Double, lon: Double): UtmCoordinate? {
        if (!lat.isFinite() || !lon.isFinite() || lat < -80.0 || lat > 84.0) return null
        val lo = normaliseLongitude(lon)
        val zone = utmZone(lat, lo)
        // Band X is 12° tall (72°..84°), so the index is clamped rather than overflowing.
        val band = BANDS[floor((lat + 80) / 8).toInt().coerceIn(0, BANDS.length - 1)]

        val phi = Math.toRadians(lat)
        var dLambda = lo - (zone * 6 - 183)
        if (dLambda < -180) dLambda += 360 else if (dLambda > 180) dLambda -= 360
        val lambda = Math.toRadians(dLambda)

        // Conformal latitude, then the Gauss-Schreiber sphere, then Krüger's correction.
        val sinPhi = sin(phi)
        val t = sinh(atanh(sinPhi) - E * atanh(E * sinPhi))
        val xiP = atan2(t, cos(lambda))
        val etaP = atanh(sin(lambda) / sqrt(1 + t * t))
        var xi = xiP
        var eta = etaP
        for (j in 1..ALPHA.size) {
            xi += ALPHA[j - 1] * sin(2 * j * xiP) * cosh(2 * j * etaP)
            eta += ALPHA[j - 1] * cos(2 * j * xiP) * sinh(2 * j * etaP)
        }
        val easting = FALSE_EASTING + K0 * RECTIFYING_RADIUS * eta
        val northing = K0 * RECTIFYING_RADIUS * xi + if (lat < 0) FALSE_NORTHING_SOUTH else 0.0
        return UtmCoordinate(zone, band, easting, northing)
    }

    private fun atanh(x: Double): Double = 0.5 * kotlin.math.ln((1 + x) / (1 - x))

    /** Standard 6° zones, widened for south-west Norway (32V) and Svalbard (31X..37X). */
    private fun utmZone(lat: Double, lon: Double): Int {
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) return 32
        if (lat >= 72.0) {
            when {
                lon >= 0.0 && lon < 9.0 -> return 31
                lon >= 9.0 && lon < 21.0 -> return 33
                lon >= 21.0 && lon < 33.0 -> return 35
                lon >= 33.0 && lon < 42.0 -> return 37
            }
        }
        return (floor((lon + 180) / 6).toInt() + 1).coerceIn(1, 60)
    }

    private fun normaliseLongitude(lon: Double): Double {
        var l = (lon + 180) % 360
        if (l < 0) l += 360
        return l - 180
    }

    // ---- MGRS ----

    private val MGRS_COLUMNS = arrayOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")
    private const val MGRS_ROWS = "ABCDEFGHJKLMNPQRSTUV"

    /**
     * MGRS is UTM with the leading digits replaced by a 100 km square name. The digits are
     * truncated, not rounded, so the reference names the square's corner the point lies in.
     */
    fun mgrs(lat: Double, lon: Double): String? {
        val utm = utm(lat, lon) ?: return null
        val e = floor(utm.easting).toLong()
        val n = floor(utm.northing).toLong()
        // Column letters cycle through three sets over zones; rows repeat every 2000 km and
        // even zones are offset by five letters so neighbouring squares never share a name.
        val column = MGRS_COLUMNS[(utm.zone - 1) % 3][(e / 100_000).toInt() - 1]
        val rowOffset = if (utm.zone % 2 == 0) 5 else 0
        val row = MGRS_ROWS[((n / 100_000 + rowOffset) % 20).toInt()]
        val digits = String.format(Locale.ROOT, "%05d %05d", e % 100_000, n % 100_000)
        return "${utm.zone}${utm.band} $column$row $digits"
    }

    // ---- Open Location Code ----

    private const val OLC_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val OLC_PAIR_LENGTH = 10

    /** Height of a 10-digit code's cell, taken off the pole so it still has a cell to name. */
    private const val OLC_PRECISION = 1.0 / 8000

    fun plusCode(lat: Double, lon: Double): String {
        var la = lat.coerceIn(-90.0, 90.0)
        if (la == 90.0) la -= OLC_PRECISION
        val lo = normaliseLongitude(lon)
        // Integer arithmetic, like the reference implementation, so that values sitting exactly
        // on a cell edge do not fall into the cell below through floating point error.
        // Clamped: a latitude a hair below 90° rounds up to the pole, which has no cell.
        var latUnits = (((la + 90) * 2.5e7).roundToLong() / 3125).coerceAtMost(180L * 8000 - 1) // 1/8000° steps
        var lonUnits = ((lo + 180) * 8.192e6).roundToLong() / 1024
        val digits = CharArray(OLC_PAIR_LENGTH)
        for (pair in OLC_PAIR_LENGTH / 2 - 1 downTo 0) {
            digits[pair * 2] = OLC_ALPHABET[(latUnits % 20).toInt()]
            digits[pair * 2 + 1] = OLC_ALPHABET[(lonUnits % 20).toInt()]
            latUnits /= 20
            lonUnits /= 20
        }
        val code = String(digits)
        return code.substring(0, 8) + "+" + code.substring(8)
    }

    // ---- Maidenhead ----

    fun maidenhead(lat: Double, lon: Double): String {
        // Just below the pole and the antimeridian, so the last field still exists.
        val x = (normaliseLongitude(lon) + 180).coerceIn(0.0, 360 - 1e-9)
        val y = (lat.coerceIn(-90.0, 90.0) + 90).coerceIn(0.0, 180 - 1e-9)
        val fieldLon = floor(x / 20).toInt()
        val fieldLat = floor(y / 10).toInt()
        val squareLon = floor(x % 20 / 2).toInt()
        val squareLat = floor(y % 10).toInt()
        val subLon = floor(x % 2 * 12).toInt()
        val subLat = floor(y % 1 * 24).toInt()
        return buildString {
            append('A' + fieldLon)
            append('A' + fieldLat)
            append(squareLon)
            append(squareLat)
            append('a' + subLon)
            append('a' + subLat)
        }
    }
}
