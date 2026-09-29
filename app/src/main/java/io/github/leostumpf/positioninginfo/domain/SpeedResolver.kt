// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.SpeedFix

/** What the screen should display for a given fix at a given moment. */
data class SpeedReading(
    val speedMps: Float?,
    val freshness: FixFreshness,
    /**
     * Whether this reading should be folded into the session statistics. A cached startup
     * fix is shown but never counted, so the session maximum reflects the trip you are on
     * rather than the one you just finished.
     */
    val countsTowardsStats: Boolean = false,
)

/**
 * Decides what a fix is worth right now.
 *
 * Kept separate from the ViewModel because it has to run in two situations — when a fix
 * arrives, and on a timer while none does — and because it is where every rule about
 * withholding a reading lives:
 *
 * - a fix with no velocity field is not a speed, and is never substituted for one;
 * - a fix too old to mean anything is withdrawn rather than left frozen on screen;
 * - anything the receiver cannot distinguish from standing still reads as zero.
 */
object SpeedResolver {

    fun resolve(fix: SpeedFix?, nowElapsedMs: Long): SpeedReading {
        if (fix == null) return SpeedReading(speedMps = null, freshness = FixFreshness.EXPIRED)

        val freshness = FixFreshness.ofAge(ageMs(fix, nowElapsedMs))
        val raw = fix.speedMps

        val speed = if (raw == null || freshness == FixFreshness.EXPIRED) {
            null
        } else {
            SpeedFilter.apply(raw, fix.speedAccuracyMps)
        }

        return SpeedReading(
            speedMps = speed,
            freshness = freshness,
            countsTowardsStats = speed != null && freshness == FixFreshness.FRESH && !fix.isCached,
        )
    }

    /** Clamped at zero: a fix timestamped in the future is a clock artefact, not a new fix. */
    fun ageMs(fix: SpeedFix, nowElapsedMs: Long): Long =
        (nowElapsedMs - fix.elapsedRealtimeMs).coerceAtLeast(0L)
}
