// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

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

    fun utm(lat: Double, lon: Double): UtmCoordinate? = UtmGrid.utm(lat, lon)

    fun mgrs(lat: Double, lon: Double): String? = UtmGrid.mgrs(lat, lon)

    private const val HALF_TURN_DEG = 180.0
    private const val FULL_TURN_DEG = 360.0

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

/** The same longitude in −180°..180°. */
internal fun normaliseLongitude(lon: Double): Double {
    var l = (lon + LONGITUDE_SPAN / 2) % LONGITUDE_SPAN
    if (l < 0) l += LONGITUDE_SPAN
    return l - LONGITUDE_SPAN / 2
}

private const val LONGITUDE_SPAN = 360.0
