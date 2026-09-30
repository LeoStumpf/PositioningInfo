// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * How often fixes actually arrive, as opposed to how often they were requested.
 *
 * Phones deliver 1 Hz nominally but slow down when the signal is poor or the chip
 * duty-cycles to save power, so the measured interval says something about the receiver.
 * Immutable, like [SessionStats]: every event returns a new instance.
 */
data class UpdateRate(val timesMs: List<Long> = emptyList()) {

    /** Notes a fix arriving at [elapsedMs] (elapsed realtime), keeping the last [WINDOW] intervals. */
    fun onFix(elapsedMs: Long): UpdateRate {
        val last = timesMs.lastOrNull()
        // A repeated or out-of-order timestamp is not a new fix.
        if (last != null && elapsedMs <= last) return this
        // After a gap (app in the background) the old spacing says nothing.
        val kept = if (last != null && elapsedMs - last > GAP_MS) emptyList() else timesMs
        return UpdateRate((kept + elapsedMs).takeLast(WINDOW + 1))
    }

    /** Mean spacing of the recent fixes, or null with fewer than two. */
    val meanIntervalMs: Long?
        get() = if (timesMs.size < 2) null else (timesMs.last() - timesMs.first()) / (timesMs.size - 1)

    val hz: Double? get() = meanIntervalMs?.takeIf { it > 0 }?.let { 1_000.0 / it }

    companion object {
        const val WINDOW = 10
        const val GAP_MS = 10_000L
    }
}
