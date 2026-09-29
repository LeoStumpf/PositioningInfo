// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A direction in the sky: compass bearing and height above the horizon. */
data class SkyPoint(val azimuthDegrees: Float, val elevationDegrees: Float)

data class SkySample(val atMs: Long, val point: SkyPoint)

/** One physical satellite, however many bands it is heard on. */
data class SatelliteId(val constellation: Constellation, val svid: Int)

/**
 * Where one satellite has been, as the receiver reported it.
 *
 * [visible] means "in the receiver's list right now". The receiver also lists satellites it
 * only knows about from the almanac, so [tracked] separates those it actually hears.
 */
data class SkyTrack(
    val id: SatelliteId,
    val samples: List<SkySample>,
    val current: SkyPoint,
    val lastSeenMs: Long,
    val visible: Boolean,
    val tracked: Boolean,
    val usedInFix: Boolean,
)

data class SkyEvent(
    val id: SatelliteId,
    val kind: Kind,
    val atMs: Long,
    /** Elevation when it happened: low means it rose or set, high means it was blocked or acquired. */
    val elevationDegrees: Float,
) {
    enum class Kind { APPEARED, LOST }
}

/**
 * Records every satellite's path across the sky, and which ones come and go.
 *
 * Offline by design: the app has no network access, and Android does not let apps read the
 * orbits the GNSS chip holds. So everything here is built from the azimuth and elevation
 * the receiver reports, and the future is only ever an extrapolation, see [SkyProjection].
 *
 * Immutable, like [SessionStats]: every event returns a new instance.
 */
data class SkyTracker(
    val tracks: Map<SatelliteId, SkyTrack> = emptyMap(),
    /** Newest first. */
    val events: List<SkyEvent> = emptyList(),
    /** Start of the current receiver session, or null before the first snapshot. */
    val sessionStartMs: Long? = null,
    val lastSnapshotMs: Long? = null,
) {

    fun onSnapshot(satellites: List<SatelliteInfo>, nowMs: Long): SkyTracker {
        // After a gap (the app was in the background) every satellite would otherwise be
        // reported lost and then found again. The gap is not news, so start over quietly.
        val resumed = lastSnapshotMs == null || nowMs - lastSnapshotMs > LOST_AFTER_MS
        val base = if (resumed) {
            copy(
                tracks = tracks.mapValues { it.value.copy(visible = false) },
                sessionStartMs = nowMs,
            )
        } else {
            this
        }
        // While the receiver is still acquiring, "appeared" only means "found at start-up".
        val settling = nowMs - (base.sessionStartMs ?: nowMs) < SETTLE_MS

        val seen = satellites
            // GnssStatus has no "unknown" flag: an unlocated satellite reads as 0°/0°.
            .filterNot { it.azimuthDegrees == 0f && it.elevationDegrees == 0f }
            .groupBy { SatelliteId(it.constellation, it.svid) }

        val newEvents = mutableListOf<SkyEvent>()
        val updated = base.tracks.toMutableMap()
        for ((id, signals) in seen) {
            val point = SkyPoint(signals[0].azimuthDegrees, signals[0].elevationDegrees)
            val previous = updated[id]
            if ((previous == null || !previous.visible) && !settling) {
                newEvents += SkyEvent(id, SkyEvent.Kind.APPEARED, nowMs, point.elevationDegrees)
            }
            val samples = previous?.samples.orEmpty()
            val sampled = if (samples.isEmpty() || nowMs - samples.last().atMs >= SAMPLE_EVERY_MS) {
                samples + SkySample(nowMs, point)
            } else {
                samples
            }
            updated[id] = SkyTrack(
                id = id,
                samples = sampled,
                current = point,
                lastSeenMs = nowMs,
                visible = true,
                tracked = signals.any { it.cn0DbHz > 0f },
                usedInFix = signals.any { it.usedInFix },
            )
        }

        return base.copy(tracks = updated, lastSnapshotMs = nowMs)
            .withEvents(newEvents)
            .onTick(nowMs)
    }

    /**
     * The receiver is being released. Nothing it reports disappearing meanwhile is news, so
     * everything goes out of sight quietly and the next snapshot starts a new session.
     */
    fun onPause(): SkyTracker =
        copy(tracks = tracks.mapValues { it.value.copy(visible = false) }, lastSnapshotMs = null)

    /** Ages the history and notices satellites that have gone, whether or not a sweep arrived. */
    fun onTick(nowMs: Long): SkyTracker {
        val newEvents = mutableListOf<SkyEvent>()
        val updated = tracks.mapNotNull { (id, track) ->
            var t = track
            if (t.visible && nowMs - t.lastSeenMs > LOST_AFTER_MS) {
                newEvents += SkyEvent(id, SkyEvent.Kind.LOST, t.lastSeenMs, t.current.elevationDegrees)
                t = t.copy(visible = false)
            }
            val kept = t.samples.filter { nowMs - it.atMs <= HISTORY_MS }
            if (kept.isEmpty() && !t.visible) null else id to t.copy(samples = kept)
        }.toMap()
        return copy(tracks = updated).withEvents(newEvents)
    }

    private fun withEvents(newEvents: List<SkyEvent>): SkyTracker =
        if (newEvents.isEmpty()) this
        else copy(events = (newEvents.sortedByDescending { it.atMs } + events).take(MAX_EVENTS))

    companion object {
        const val SAMPLE_EVERY_MS = 10_000L
        const val HISTORY_MS = 60 * 60_000L
        const val LOST_AFTER_MS = 60_000L
        const val SETTLE_MS = 60_000L
        const val MAX_EVENTS = 20
    }
}

/** A short-range guess at where a satellite goes next. */
data class SkyProjection(
    val points: List<SkyPoint>,
    /** Minutes from now until it drops below the horizon, or null if not within the window. */
    val setsInMinutes: Float?,
) {
    companion object {
        const val FIT_WINDOW_MS = 10 * 60_000L
        const val MIN_SPAN_MS = 2 * 60_000L
        const val HORIZON_MINUTES = 15
        /** Below this the satellite is effectively still (a geostationary SBAS or BeiDou GEO). */
        const val MIN_RATE_DEG_PER_MIN = 0.05

        /**
         * Continues the recent motion in a straight line through space and projects it back
         * onto the sky.
         *
         * Working in unit vectors rather than azimuth/elevation means a satellite crossing
         * north (359° → 1°) or passing overhead moves smoothly instead of jumping. Over a
         * quarter of an hour a GNSS orbit curves only slightly, so a line is a fair guess.
         */
        fun of(samples: List<SkySample>, nowMs: Long): SkyProjection? {
            val last = samples.lastOrNull() ?: return null
            val window = samples.filter { last.atMs - it.atMs <= FIT_WINDOW_MS }
            if (window.size < 3 || last.atMs - window.first().atMs < MIN_SPAN_MS) return null

            // Least squares per axis, time in minutes relative to the newest sample.
            val ts = window.map { (it.atMs - last.atMs) / 60_000.0 }
            val vs = window.map { it.point.toVector() }
            val tMean = ts.average()
            val denom = ts.sumOf { (it - tMean) * (it - tMean) }
            val start = DoubleArray(3)
            val rate = DoubleArray(3)
            for (axis in 0..2) {
                val mean = vs.sumOf { it[axis] } / vs.size
                val slope = ts.indices.sumOf { (ts[it] - tMean) * (vs[it][axis] - mean) } / denom
                rate[axis] = slope
                start[axis] = mean - slope * tMean  // fitted value at the newest sample
            }
            val degPerMin = Math.toDegrees(sqrt(rate.sumOf { it * it }))
            if (degPerMin < MIN_RATE_DEG_PER_MIN) return null

            val sinceLastMin = (nowMs - last.atMs) / 60_000.0
            val points = mutableListOf<SkyPoint>()
            var setsIn: Float? = null
            var prev = toPoint(start)
            points += prev
            for (step in 1..HORIZON_MINUTES) {
                val t = sinceLastMin + step
                val next = toPoint(DoubleArray(3) { start[it] + rate[it] * t })
                if (next.elevationDegrees < 0f) {
                    // Interpolate the crossing and end the path on the horizon.
                    val f = prev.elevationDegrees / (prev.elevationDegrees - next.elevationDegrees)
                    setsIn = ((t - 1 + f) - sinceLastMin).toFloat().coerceAtLeast(0f)
                    points += SkyPoint(interpolateAzimuth(prev.azimuthDegrees, next.azimuthDegrees, f), 0f)
                    break
                }
                points += next
                prev = next
            }
            return SkyProjection(points, setsIn)
        }

        private fun SkyPoint.toVector(): DoubleArray {
            val az = Math.toRadians(azimuthDegrees.toDouble())
            val el = Math.toRadians(elevationDegrees.toDouble())
            return doubleArrayOf(cos(el) * sin(az), cos(el) * cos(az), sin(el))
        }

        private fun toPoint(v: DoubleArray): SkyPoint {
            val norm = sqrt(v.sumOf { it * it })
            val az = Math.toDegrees(atan2(v[0], v[1])).let { if (it < 0) it + 360 else it }
            val el = Math.toDegrees(asin((v[2] / norm).coerceIn(-1.0, 1.0)))
            return SkyPoint(az.toFloat(), el.toFloat())
        }

        private fun interpolateAzimuth(from: Float, to: Float, f: Float): Float {
            var delta = to - from
            if (delta > 180f) delta -= 360f
            if (delta < -180f) delta += 360f
            return ((from + delta * f) % 360f + 360f) % 360f
        }
    }
}
