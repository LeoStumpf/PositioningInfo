// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * Running maximum and average speed for the current session.
 *
 * The average is **time weighted** — `sum(speed * dt) / sum(dt)` — rather than a plain
 * mean of the samples. GNSS updates do not arrive at a perfectly fixed rate, and a naive
 * mean would over-weight the bursts of closely spaced fixes.
 *
 * Intervals longer than [maxGapMs] are treated as a gap in reception (a tunnel, a
 * backgrounded app) and contribute no weight at all, so the time spent without a fix is
 * never silently charged to the last known speed.
 *
 * Immutable: [accept] returns a new instance, which keeps it trivially usable from a
 * StateFlow and free of shared mutable state.
 */
data class SessionStats(
    val maxMps: Double = 0.0,
    val distanceM: Double = 0.0,
    val weightedTimeMs: Long = 0L,
    private val lastSampleAtMs: Long? = null,
) {
    /** Time-weighted mean speed in m/s, or null before enough data has accumulated. */
    val averageMps: Double?
        get() = if (weightedTimeMs > 0L) distanceM / (weightedTimeMs / 1000.0) else null

    val hasData: Boolean get() = lastSampleAtMs != null

    /**
     * Folds one accepted speed sample into the statistics.
     *
     * @param speedMps speed for this sample, already noise-floored by the caller.
     * @param atElapsedMs the sample's timestamp on the monotonic elapsed-realtime clock.
     */
    fun accept(speedMps: Double, atElapsedMs: Long): SessionStats {
        val previous = lastSampleAtMs
        val deltaMs = if (previous == null) 0L else atElapsedMs - previous

        // A non-monotonic or absurdly long delta carries no usable weight, but the sample
        // still counts towards the maximum and re-anchors the clock for the next interval.
        val usableDelta = if (deltaMs in 1..maxGapMs) deltaMs else 0L

        return SessionStats(
            maxMps = maxOf(maxMps, speedMps),
            distanceM = distanceM + speedMps * (usableDelta / 1000.0),
            weightedTimeMs = weightedTimeMs + usableDelta,
            lastSampleAtMs = atElapsedMs,
        )
    }

    companion object {
        /** Intervals above this are reception gaps, not time spent travelling. */
        const val maxGapMs: Long = 5_000L
    }
}
