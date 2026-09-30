// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import kotlin.math.floor

/** One patch of sky and how strong the signals arriving from it have been. */
data class ObstructionCell(
    val azimuthFrom: Float,
    val azimuthTo: Float,
    val elevationFrom: Float,
    val elevationTo: Float,
    val meanCn0DbHz: Float,
    val maxCn0DbHz: Float,
    val samples: Int,
)

/** Running totals for one bin: enough for a mean and a maximum without keeping every sample. */
data class ObstructionBin(val sumCn0: Double = 0.0, val maxCn0: Float = 0f, val count: Int = 0) {
    fun add(cn0: Float): ObstructionBin = ObstructionBin(sumCn0 + cn0, maxOf(maxCn0, cn0), count + 1)
}

/**
 * Where the sky is blocked, learnt from signal strength by direction over time.
 *
 * Satellites move, so over minutes to hours they sweep most of the sky. A building or tree
 * between the phone and a patch of sky shows up as a region whose signals are persistently
 * weak, or which never has any: the map does not know about the obstruction, it just
 * accumulates what the receiver heard from each direction.
 *
 * Bins are [AZIMUTH_STEP] by [ELEVATION_STEP] degrees, keyed by (azimuth index, elevation
 * index). Only sums and counts are kept, so memory stays bounded however long it runs.
 *
 * Immutable, like [SkyTracker]: every event returns a new instance.
 */
data class ObstructionMap(val bins: Map<Pair<Int, Int>, ObstructionBin> = emptyMap()) {

    /**
     * One snapshot's worth: every tracked signal (cn0 > 0, located) contributes to its
     * direction's bin. Signals of the same satellite on several bands count separately:
     * each band is its own measurement of how clear that line of sight is.
     */
    fun onSnapshot(satellites: List<SatelliteInfo>): ObstructionMap {
        val updated = bins.toMutableMap()
        // GnssStatus has no "unknown" flag: an unlocated satellite reads as 0°/0°.
        val located = satellites.filter { it.cn0DbHz > 0f && !(it.azimuthDegrees == 0f && it.elevationDegrees == 0f) }
        for (sat in located) {
            val key = binOf(sat.azimuthDegrees, sat.elevationDegrees)
            updated[key] = (updated[key] ?: ObstructionBin()).add(sat.cn0DbHz)
        }
        return copy(bins = updated)
    }

    /** Every bin that has heard something, ordered by azimuth then elevation. */
    val cells: List<ObstructionCell>
        get() = bins.entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (key, bin) ->
                val (az, el) = key
                ObstructionCell(
                    azimuthFrom = az * AZIMUTH_STEP,
                    azimuthTo = (az + 1) * AZIMUTH_STEP,
                    elevationFrom = el * ELEVATION_STEP,
                    elevationTo = minOf((el + 1) * ELEVATION_STEP, MAX_ELEVATION),
                    meanCn0DbHz = (bin.sumCn0 / bin.count).toFloat(),
                    maxCn0DbHz = bin.maxCn0,
                    samples = bin.count,
                )
            }

    val totalSamples: Int get() = bins.values.sumOf { it.count }

    companion object {
        const val AZIMUTH_STEP = 10f
        const val ELEVATION_STEP = 10f
        private const val MAX_ELEVATION = 90f
        private val AZIMUTH_BINS = (360f / AZIMUTH_STEP).toInt()
        private val ELEVATION_BINS = (MAX_ELEVATION / ELEVATION_STEP).toInt()

        /** Azimuth wraps into [0, 360); elevation 90° (the zenith) belongs to the top bin. */
        fun binOf(azimuthDegrees: Float, elevationDegrees: Float): Pair<Int, Int> {
            val az = ((azimuthDegrees % 360f) + 360f) % 360f
            val azIndex = floor(az / AZIMUTH_STEP).toInt().coerceIn(0, AZIMUTH_BINS - 1)
            val elIndex = floor(elevationDegrees / ELEVATION_STEP).toInt().coerceIn(0, ELEVATION_BINS - 1)
            return azIndex to elIndex
        }
    }
}
