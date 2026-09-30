// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/** One moment of the receiver's state, for the history graphs. */
data class HistorySample(
    val atMs: Long,
    val usedInFix: Int,
    val heard: Int,
    /** Mean C/N0 of the satellites heard, or null with none. */
    val meanCn0: Float?,
    val accuracyM: Float?,
)

/**
 * The last half hour of the receiver's state, one sample every [INTERVAL_MS], so a graph
 * can answer "was it worse a minute ago, under the trees?".
 *
 * Held in memory only. Immutable, like [SessionStats]: every event returns a new instance.
 */
data class History(val samples: List<HistorySample> = emptyList()) {

    fun add(sample: HistorySample): History {
        val last = samples.lastOrNull()
        if (last != null && sample.atMs - last.atMs < INTERVAL_MS) return this
        return History((samples + sample).filter { sample.atMs - it.atMs <= WINDOW_MS })
    }

    companion object {
        const val INTERVAL_MS = 5_000L
        const val WINDOW_MS = 30 * 60_000L

        /** A longer silence (app in the background) breaks the line rather than bridging it. */
        const val GAP_MS = 20_000L
    }
}

/** How a session started, and how long its first fix took. */
data class TtffEntry(val utcMs: Long, val ttffMs: Long, val startType: AlmanacReadiness) {

    fun encode(): String = "$utcMs,$ttffMs,${startType.name}"

    companion object {
        const val MAX_ENTRIES = 20

        /** A line is time, duration and start type, comma separated. */
        private const val FIELDS = 3

        fun decode(line: String): TtffEntry? {
            val parts = line.trim().split(',')
            if (parts.size != FIELDS) return null
            val utc = parts[0].toLongOrNull() ?: return null
            val ttff = parts[1].toLongOrNull() ?: return null
            val type = AlmanacReadiness.entries.firstOrNull { it.name == parts[2] } ?: return null
            return TtffEntry(utc, ttff, type)
        }
    }
}
