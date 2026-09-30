// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dilution of precision: how much the satellite geometry magnifies ranging errors into
 * position (P), horizontal (H), vertical (V) and clock (T) errors. Unitless; lower is better.
 */
data class Dop(val pdop: Double, val hdop: Double, val vdop: Double, val tdop: Double) {
    val rating: DopRating get() = DopRating.of(pdop)
}

/** The customary verbal scale for DOP values. */
enum class DopRating(val label: String, private val upTo: Double) {
    IDEAL("Ideal", 1.0),
    EXCELLENT("Excellent", 2.0),
    GOOD("Good", 5.0),
    MODERATE("Moderate", 10.0),
    FAIR("Fair", 20.0),
    POOR("Poor", Double.POSITIVE_INFINITY),
    ;

    companion object {
        /** The first rating whose upper bound the value does not exceed. */
        fun of(dop: Double): DopRating = entries.first { dop <= it.upTo }
    }
}

/**
 * Computes DOP ourselves because Android only exposes the chip's DOP through NMEA sentences,
 * which not every device emits; azimuth and elevation are always available.
 *
 * The model has one receiver clock. A multi-constellation receiver also estimates an
 * inter-system bias per extra constellation, which costs geometry, so the chip's own DOP
 * can be slightly higher than this one.
 */
object DopCalculator {

    /**
     * Dilution of precision from the directions of the satellites used in the fix. Null for
     * fewer than 4 or a singular geometry.
     */
    fun of(directions: List<SkyPoint>): Dop? {
        if (directions.size < MIN_SATELLITES) return null
        // Rows of the geometry matrix H in local east-north-up, plus the clock column.
        val rows = directions.map { p ->
            val az = Math.toRadians(p.azimuthDegrees.toDouble())
            val el = Math.toRadians(p.elevationDegrees.toDouble())
            doubleArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el), 1.0)
        }
        val normal = Array(4) { i -> DoubleArray(4) { j -> rows.sumOf { it[i] * it[j] } } }
        val q = invert(normal) ?: return null
        val qEE = q[0][0]
        val qNN = q[1][1]
        val qUU = q[2][2]
        val qTT = q[3][3]
        // A nearly singular matrix can come back with negative diagonals from rounding.
        if (listOf(qEE, qNN, qUU, qTT).any { it < 0 }) return null
        return Dop(pdop = sqrt(qEE + qNN + qUU), hdop = sqrt(qEE + qNN), vdop = sqrt(qUU), tdop = sqrt(qTT))
    }

    /** Gauss-Jordan with partial pivoting; null when a pivot vanishes relative to the matrix. */
    private fun invert(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { i ->
            DoubleArray(2 * n) { j ->
                if (j < n) {
                    m[i][j]
                } else if (j - n == i) {
                    1.0
                } else {
                    0.0
                }
            }
        }
        val scale = m.maxOf { row -> row.maxOf { abs(it) } }
        if (scale == 0.0) return null
        val tolerance = scale * SINGULAR_EPSILON
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(a[it][col]) }
            if (abs(a[pivot][col]) < tolerance) return null
            val tmp = a[col]
            a[col] = a[pivot]
            a[pivot] = tmp
            val p = a[col][col]
            for (j in 0 until 2 * n) a[col][j] /= p
            for (r in 0 until n) {
                val factor = a[r][col]
                if (r != col && factor != 0.0) {
                    for (j in 0 until 2 * n) a[r][j] -= factor * a[col][j]
                }
            }
        }
        return Array(n) { i -> DoubleArray(n) { j -> a[i][j + n] } }
    }

    private const val SINGULAR_EPSILON = 1e-9

    /** Three unknowns for the position and one for the receiver clock. */
    private const val MIN_SATELLITES = 4
}
