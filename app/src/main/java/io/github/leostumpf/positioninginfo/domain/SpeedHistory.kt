// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/** One point of the speed-over-time plot. */
data class SpeedSample(val atMs: Long, val mps: Float)

/**
 * Speed since the last reset, for the plot on the speed page. Held in memory only.
 *
 * At most one sample every [INTERVAL_MS]; once [MAX_SAMPLES] are held, neighbouring pairs
 * are merged — keeping the faster of the two, so the peaks a driver remembers survive — and
 * the interval doubles. A two-hour drive therefore costs as much memory as a ten-minute one.
 * Immutable, like [SessionStats]: every event returns a new instance.
 */
data class SpeedHistory(
    val samples: List<SpeedSample> = emptyList(),
    val intervalMs: Long = INTERVAL_MS,
    val startedAtMs: Long? = null,
) {
    /** Adds a speed in m/s at [atMs] (elapsed realtime); within the current interval only a faster one counts. */
    fun add(atMs: Long, mps: Float): SpeedHistory {
        val start = startedAtMs ?: atMs
        val last = samples.lastOrNull()
        if (last != null && atMs - last.atMs < intervalMs) {
            // Within the same slot: keep the faster reading.
            return if (mps > last.mps) copy(samples = samples.dropLast(1) + SpeedSample(last.atMs, mps)) else this
        }
        val grown = samples + SpeedSample(atMs, mps)
        if (grown.size <= MAX_SAMPLES) return copy(samples = grown, startedAtMs = start)
        val merged = grown.chunked(2).map { pair -> pair.maxBy { it.mps }.copy(atMs = pair.first().atMs) }
        return SpeedHistory(merged, intervalMs * 2, start)
    }

    val maxMps: Float? get() = samples.maxOfOrNull { it.mps }

    companion object {
        const val INTERVAL_MS = 1_000L
        const val MAX_SAMPLES = 600

        /** Longer than this without a sample (app closed) breaks the line. */
        const val GAP_MS = 15_000L
    }
}
