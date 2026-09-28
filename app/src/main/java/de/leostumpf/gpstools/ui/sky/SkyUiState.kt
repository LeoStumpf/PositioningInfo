// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.sky

import de.leostumpf.gpstools.domain.Constellation
import de.leostumpf.gpstools.domain.SatelliteId
import de.leostumpf.gpstools.domain.SkyEvent
import de.leostumpf.gpstools.domain.SkyPoint
import de.leostumpf.gpstools.domain.SkyProjection
import de.leostumpf.gpstools.domain.SkyTrack
import de.leostumpf.gpstools.domain.SkyTracker

/** Everything the sky view draws. */
data class SkyUiState(
    val markers: List<SkyMarker> = emptyList(),
    /** Satellites expected to set soon, soonest first. */
    val upcoming: List<UpcomingSet> = emptyList(),
    val events: List<EventRow> = emptyList(),
) {
    companion object {
        fun from(tracker: SkyTracker, nowMs: Long) = SkyUiState(
            markers = tracker.tracks.values
                .sortedBy { it.usedInFix }  // satellites in the fix are drawn on top
                .map { it.toMarker(nowMs) },
            upcoming = tracker.tracks.values
                .filter { it.visible }
                .mapNotNull { track ->
                    SkyProjection.of(track.samples, nowMs)?.setsInMinutes
                        ?.let { UpcomingSet(track.id.label(), it) }
                }
                .sortedBy { it.minutes },
            events = tracker.events.map { event ->
                EventRow(
                    key = "${event.id}-${event.kind}-${event.atMs}",
                    text = event.describe(),
                    ago = formatAgo(nowMs - event.atMs),
                )
            },
        )
    }
}

data class SkyMarker(
    val key: String,
    val label: String,
    val constellation: Constellation,
    /** Null once the satellite has left the receiver's list: only its trail remains. */
    val current: SkyPoint?,
    /** The path so far, split wherever the satellite was out of sight. */
    val trail: List<List<SkyPoint>>,
    val projection: List<SkyPoint>,
    val tracked: Boolean,
    val usedInFix: Boolean,
)

data class UpcomingSet(val label: String, val minutes: Float)

data class EventRow(val key: String, val text: String, val ago: String)

private fun SkyTrack.toMarker(nowMs: Long): SkyMarker {
    val segments = mutableListOf<MutableList<SkyPoint>>()
    var lastAt: Long? = null
    for (sample in samples) {
        if (lastAt == null || sample.atMs - lastAt > SkyTracker.LOST_AFTER_MS) segments += mutableListOf<SkyPoint>()
        segments.last() += sample.point
        lastAt = sample.atMs
    }
    if (visible && segments.isNotEmpty()) segments.last() += current
    return SkyMarker(
        key = id.toString(),
        label = id.label(),
        constellation = id.constellation,
        current = current.takeIf { visible },
        trail = segments,
        projection = if (visible) SkyProjection.of(samples, nowMs)?.points.orEmpty() else emptyList(),
        tracked = tracked,
        usedInFix = usedInFix,
    )
}

/** RINEX-style short name, e.g. "G07" for GPS PRN 7 or "E24" for Galileo 24. */
fun SatelliteId.label(): String = constellation.prefix() + svid.toString().padStart(2, '0')

private fun Constellation.prefix(): String = when (this) {
    Constellation.GPS -> "G"
    Constellation.GLONASS -> "R"
    Constellation.GALILEO -> "E"
    Constellation.BEIDOU -> "C"
    Constellation.QZSS -> "J"
    Constellation.IRNSS -> "I"
    Constellation.SBAS -> "S"
    Constellation.UNKNOWN -> "?"
}

/** Low elevation means the horizon was involved; high means an obstruction or acquisition. */
private fun SkyEvent.describe(): String {
    val name = id.label()
    val low = elevationDegrees < LOW_ELEVATION_DEGREES
    return when (kind) {
        SkyEvent.Kind.APPEARED -> if (low) "$name rose above the horizon" else "$name acquired"
        SkyEvent.Kind.LOST -> if (low) "$name set below the horizon" else "$name lost"
    } + " (${elevationDegrees.toInt()}°)"
}

internal fun formatAgo(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ${minutes % 60} min ago"
    }
}

private const val LOW_ELEVATION_DEGREES = 15f
