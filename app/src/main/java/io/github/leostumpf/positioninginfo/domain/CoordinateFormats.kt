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
    override fun toString(): String = "$zone$band ${easting.roundToLong()} ${northing.roundToLong()}"
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

    fun dms(lat: Double, lon: Double): String = "${dmsPart(lat, 'N', 'S')}, ${dmsPart(lon, 'E', 'W')}"

    /**
     * Rounds once, in tenths of a second, and splits afterwards. Rounding the seconds on
     * their own would print 59.95″ as "60.0″" instead of carrying into the minutes.
     */
    private const val TENTHS = 10L
    private const val TENTHS_PER_MINUTE = 60 * TENTHS
    private const val TENTHS_PER_DEGREE = 60 * TENTHS_PER_MINUTE

    private fun dmsPart(value: Double, positive: Char, negative: Char): String {
        val tenths = (abs(value) * TENTHS_PER_DEGREE).roundToLong()
        val degrees = tenths / TENTHS_PER_DEGREE
        val minutes = tenths % TENTHS_PER_DEGREE / TENTHS_PER_MINUTE
        val secondTenths = tenths % TENTHS_PER_MINUTE
        val letter = if (value < 0 && tenths != 0L) negative else positive
        return "$degrees°$minutes′${secondTenths / TENTHS}.${secondTenths % TENTHS}″ $letter"
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

    /** UTM covers 80° S to 84° N; the poles belong to UPS. */
    private val UTM_LATITUDES = -80.0..84.0

    // Krüger's series in the third flattening n, to n⁶ (Karney 2011): sub-millimetre
    // within a UTM zone, where the classic Snyder series drifts by centimetres.
    private const val N = F / (2 - F)
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
        if (!lon.isFinite() || lat !in UTM_LATITUDES) return null
        val lo = normaliseLongitude(lon)
        val zone = utmZone(lat, lo)
        // Band X is 12° tall (72°..84°), so the index is clamped rather than overflowing.
        val band = BANDS[floor((lat - UTM_LATITUDES.start) / BAND_HEIGHT_DEG).toInt().coerceIn(0, BANDS.length - 1)]

        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(normaliseLongitude(lo - centralMeridian(zone)))

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

    /** Inverse hyperbolic tangent, ½ ln((1 + x) / (1 − x)). */
    private fun atanh(x: Double): Double = kotlin.math.ln((1 + x) / (1 - x)) / 2

    private const val ZONE_WIDTH_DEG = 6.0
    private const val ZONES = 60
    private const val BAND_HEIGHT_DEG = 8.0
    private const val HALF_TURN_DEG = 180.0
    private const val FULL_TURN_DEG = 360.0

    /** The meridian in the middle of a 6° zone: zone 1 spans 180° W to 174° W. */
    private fun centralMeridian(zone: Int): Double = zone * ZONE_WIDTH_DEG - HALF_TURN_DEG - ZONE_WIDTH_DEG / 2

    /** Zone 32V is widened over south-west Norway. */
    private val NORWAY_LATITUDES = 56.0..<64.0
    private val NORWAY_LONGITUDES = 3.0..<12.0
    private const val NORWAY_ZONE = 32

    /** Band X over Svalbard has only the odd zones 31..37, each wider than 6°. */
    private const val SVALBARD_FROM_LAT = 72.0
    private val SVALBARD_ZONES = listOf(0.0..<9.0 to 31, 9.0..<21.0 to 33, 21.0..<33.0 to 35, 33.0..<42.0 to 37)

    /** Standard 6° zones, widened for south-west Norway (32V) and Svalbard (31X..37X). */
    private fun utmZone(lat: Double, lon: Double): Int {
        if (lat in NORWAY_LATITUDES && lon in NORWAY_LONGITUDES) return NORWAY_ZONE
        if (lat >= SVALBARD_FROM_LAT) SVALBARD_ZONES.firstOrNull { (lons, _) -> lon in lons }?.let { return it.second }
        return (floor((lon + HALF_TURN_DEG) / ZONE_WIDTH_DEG).toInt() + 1).coerceIn(1, ZONES)
    }

    /** The same longitude in −180°..180°. */
    private fun normaliseLongitude(lon: Double): Double {
        var l = (lon + HALF_TURN_DEG) % FULL_TURN_DEG
        if (l < 0) l += FULL_TURN_DEG
        return l - HALF_TURN_DEG
    }

    // ---- MGRS ----

    private val MGRS_COLUMNS = arrayOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")
    private const val MGRS_ROWS = "ABCDEFGHJKLMNPQRSTUV"
    private const val MGRS_SQUARE_M = 100_000L
    private const val MGRS_EVEN_ZONE_ROW_OFFSET = 5

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
        val column = MGRS_COLUMNS[(utm.zone - 1) % MGRS_COLUMNS.size][(e / MGRS_SQUARE_M).toInt() - 1]
        val rowOffset = if (utm.zone % 2 == 0) MGRS_EVEN_ZONE_ROW_OFFSET else 0
        val row = MGRS_ROWS[((n / MGRS_SQUARE_M + rowOffset) % MGRS_ROWS.length).toInt()]
        val digits = String.format(Locale.ROOT, "%05d %05d", e % MGRS_SQUARE_M, n % MGRS_SQUARE_M)
        return "${utm.zone}${utm.band} $column$row $digits"
    }

    // ---- Open Location Code ----

    private const val OLC_ALPHABET = "23456789CFGHJMPQRVWX"
    private const val OLC_PAIR_LENGTH = 10
    private const val OLC_BASE = 20
    private const val OLC_SEPARATOR_AT = 8
    private const val QUARTER_TURN_DEG = 90.0
    private const val OLC_LAT_PRECISION = 2.5e7
    private const val OLC_LAT_DIVISOR = 3_125L
    private const val OLC_LAT_UNITS_MAX = 180L * 8_000 - 1
    private const val OLC_LON_PRECISION = 8.192e6
    private const val OLC_LON_DIVISOR = 1_024L

    /** Height of a 10-digit code's cell, taken off the pole so it still has a cell to name. */
    private const val OLC_PRECISION = 1.0 / 8000

    fun plusCode(lat: Double, lon: Double): String {
        var la = lat.coerceIn(-QUARTER_TURN_DEG, QUARTER_TURN_DEG)
        if (la == QUARTER_TURN_DEG) la -= OLC_PRECISION
        val lo = normaliseLongitude(lon)
        // Integer arithmetic, like the reference implementation, so that values sitting exactly
        // on a cell edge do not fall into the cell below through floating point error.
        // Clamped: a latitude a hair below 90° rounds up to the pole, which has no cell.
        // Latitude in 1/8000° steps, longitude in 1/8192°, as the reference implementation does.
        var latUnits = (((la + QUARTER_TURN_DEG) * OLC_LAT_PRECISION).roundToLong() / OLC_LAT_DIVISOR)
            .coerceAtMost(OLC_LAT_UNITS_MAX)
        var lonUnits = ((lo + HALF_TURN_DEG) * OLC_LON_PRECISION).roundToLong() / OLC_LON_DIVISOR
        val digits = CharArray(OLC_PAIR_LENGTH)
        for (pair in OLC_PAIR_LENGTH / 2 - 1 downTo 0) {
            digits[pair * 2] = OLC_ALPHABET[(latUnits % OLC_BASE).toInt()]
            digits[pair * 2 + 1] = OLC_ALPHABET[(lonUnits % OLC_BASE).toInt()]
            latUnits /= OLC_BASE
            lonUnits /= OLC_BASE
        }
        val code = String(digits)
        return code.substring(0, OLC_SEPARATOR_AT) + "+" + code.substring(OLC_SEPARATOR_AT)
    }

    // ---- Maidenhead ----

    /** Fields are 20° × 10°, squares 2° × 1°, subsquares 5′ × 2.5′. */
    private const val FIELD_LON_DEG = 20.0
    private const val FIELD_LAT_DEG = 10.0
    private const val SQUARE_LON_DEG = 2.0
    private const val SQUARE_LAT_DEG = 1.0
    private const val SUBSQUARES_PER_LON_DEG = 12.0
    private const val SUBSQUARES_PER_LAT_DEG = 24.0

    /** Kept off the last edge so the final field and square still exist. */
    private const val EDGE = 1e-9

    fun maidenhead(lat: Double, lon: Double): String {
        // Just below the pole and the antimeridian, so the last field still exists.
        val x = (normaliseLongitude(lon) + HALF_TURN_DEG).coerceIn(0.0, FULL_TURN_DEG - EDGE)
        val y = (
            lat.coerceIn(-QUARTER_TURN_DEG, QUARTER_TURN_DEG) + QUARTER_TURN_DEG
            ).coerceIn(0.0, HALF_TURN_DEG - EDGE)
        val fieldLon = floor(x / FIELD_LON_DEG).toInt()
        val fieldLat = floor(y / FIELD_LAT_DEG).toInt()
        val squareLon = floor(x % FIELD_LON_DEG / SQUARE_LON_DEG).toInt()
        val squareLat = floor(y % FIELD_LAT_DEG / SQUARE_LAT_DEG).toInt()
        val subLon = floor(x % SQUARE_LON_DEG * SUBSQUARES_PER_LON_DEG).toInt()
        val subLat = floor(y % SQUARE_LAT_DEG * SUBSQUARES_PER_LAT_DEG).toInt()
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
