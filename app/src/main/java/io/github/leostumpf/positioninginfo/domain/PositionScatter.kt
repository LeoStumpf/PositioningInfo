// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import kotlin.math.cos
import kotlin.math.sqrt

/** One fix relative to the mean position, in metres on the local tangent plane. */
data class ScatterPoint(val eastM: Double, val northM: Double)

data class ScatterStats(
    val count: Int,
    val meanLatitude: Double,
    val meanLongitude: Double,
    /** Each fix relative to the mean position (local tangent plane). */
    val points: List<ScatterPoint>,
    /** Radius around the mean containing 50 % of the fixes (empirical percentile). */
    val cep50M: Double,
    /** Radius around the mean containing 95 % of the fixes (empirical percentile). */
    val cep95M: Double,
    /** Root mean square of the horizontal distances to the mean. */
    val drmsM: Double,
    val twoDrmsM: Double,
    /** Mean of the receiver's claimed 68 % radius; null when no fix carried one. */
    val meanClaimedAccuracyM: Double?,
    /** Share of fixes whose distance to the mean is within their own claimed accuracy. */
    val fractionWithinClaimed: Double?,
    /** Sample standard deviation of the altitudes; null below two altitudes. */
    val altitudeStdDevM: Double?,
)

/**
 * The real accuracy of a stationary phone, measured from the spread of its fixes.
 *
 * The receiver's own accuracy figure is a model estimate; while the phone lies still, the
 * scatter of its fixes around their mean is an honest, independent measurement of how good
 * the positions actually are, and lets us check whether the claimed radius is trustworthy.
 *
 * The mean position stands in for the unknown truth, so the numbers describe precision
 * (repeatability), not a bias shared by all fixes.
 *
 * Immutable: [add] returns a new instance, keeping at most [MAX_SAMPLES] newest samples.
 */
data class PositionScatter(val samples: List<Sample> = emptyList()) {

    data class Sample(val latitude: Double, val longitude: Double, val altitudeM: Double?, val claimedAccuracyM: Float?)

    fun add(sample: Sample): PositionScatter {
        val kept = if (samples.size >= MAX_SAMPLES) samples.drop(samples.size - MAX_SAMPLES + 1) else samples
        return PositionScatter(kept + sample)
    }

    /** Null below two samples: a single fix has no spread. */
    fun stats(): ScatterStats? {
        if (samples.size < 2) return null
        val n = samples.size

        // Average longitudes relative to the first one so a cluster straddling the
        // antimeridian does not average to the other side of the planet.
        val refLon = samples[0].longitude
        val meanLat = samples.sumOf { it.latitude } / n
        val meanLon = wrap(refLon + samples.sumOf { wrap(it.longitude - refLon) } / n)

        val metresPerDegLat = Math.toRadians(1.0) * EARTH_RADIUS_M
        val metresPerDegLon = metresPerDegLat * cos(Math.toRadians(meanLat))
        val points = samples.map {
            ScatterPoint(
                eastM = wrap(it.longitude - meanLon) * metresPerDegLon,
                northM = (it.latitude - meanLat) * metresPerDegLat,
            )
        }
        val distances = points.map { sqrt(it.eastM * it.eastM + it.northM * it.northM) }
        val sorted = distances.sorted()
        val drms = sqrt(distances.sumOf { it * it } / n)

        val claimed = samples.mapIndexedNotNull { i, s -> s.claimedAccuracyM?.let { i to it.toDouble() } }
        val meanClaimed = if (claimed.isEmpty()) null else claimed.sumOf { it.second } / claimed.size
        val within = if (claimed.isEmpty()) {
            null
        } else {
            claimed.count { (i, acc) -> distances[i] <= acc }.toDouble() / claimed.size
        }

        val altitudes = samples.mapNotNull { it.altitudeM }
        val altStd = if (altitudes.size < 2) {
            null
        } else {
            val mean = altitudes.average()
            sqrt(altitudes.sumOf { (it - mean) * (it - mean) } / (altitudes.size - 1))
        }

        return ScatterStats(
            count = n,
            meanLatitude = meanLat,
            meanLongitude = meanLon,
            points = points,
            cep50M = percentile(sorted, CEP50),
            cep95M = percentile(sorted, CEP95),
            drmsM = drms,
            twoDrmsM = 2 * drms,
            meanClaimedAccuracyM = meanClaimed,
            fractionWithinClaimed = within,
            altitudeStdDevM = altStd,
        )
    }

    companion object {
        const val MAX_SAMPLES = 3600

        /** The circular error probable radii: half and 95 % of the fixes lie within. */
        private const val CEP50 = 0.50
        private const val CEP95 = 0.95
        private const val HALF_TURN_DEG = 180.0
        private const val FULL_TURN_DEG = 360.0

        /** Same radius as [NetworkComparison.distanceM], so the two agree. */
        private const val EARTH_RADIUS_M = 6_371_000.0

        /** Linear interpolation between the closest ranks of an ascending, non-empty list. */
        internal fun percentile(sorted: List<Double>, p: Double): Double {
            val rank = p * (sorted.size - 1)
            val lo = rank.toInt()
            val hi = minOf(lo + 1, sorted.size - 1)
            return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo)
        }

        /** Wraps a longitude (difference) into [-180, 180]. */
        private fun wrap(deg: Double): Double {
            var d = deg
            while (d > HALF_TURN_DEG) d -= FULL_TURN_DEG
            while (d < -HALF_TURN_DEG) d += FULL_TURN_DEG
            return d
        }
    }
}
