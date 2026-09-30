// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class TripPoint(
    val timeUtcMs: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val speedMps: Float?,
    val accuracyM: Float?,
)

data class TripStats(
    val points: Int,
    val distanceM: Double,
    /** Last minus first point time, gaps included. */
    val durationMs: Long,
    /** Intervals spent moving; reception gaps are never counted. */
    val movingTimeMs: Long,
    val maxSpeedMps: Float?,
    /** [distanceM] over [movingTimeMs]; null before any moving time. */
    val avgMovingSpeedMps: Double?,
    val ascentM: Double,
    val descentM: Double,
)

/**
 * Running statistics of a recorded trip.
 *
 * GNSS positions wander by metres even when the phone lies still; summing every step would
 * let a parked phone "travel" kilometres. So a segment only counts once the position has
 * moved further than its own uncertainty (at least [MIN_SEGMENT_M]) from the last counted
 * point, or the receiver reports real motion (> [MOVING_SPEED_MPS]). Fixes worse than
 * [MAX_ACCURACY_M] never contribute distance. Across a reception gap longer than [MAX_GAP_MS]
 * the jump in position still counts, and so does the gap's time as moving time whenever the
 * jump implies travel — distance and moving time always cover the same stretches. Ascent and descent use a hysteresis (see [ClimbSource])
 * hysteresis for the same reason: altitude noise must not add up to phantom climbing.
 *
 * Immutable: [add] returns a new instance.
 */
/**
 * Where a climb altitude comes from. The sources disagree by tens of metres — the standard
 * atmosphere is off by about 8 m per hPa of weather, GNSS height by its geoid and its noise —
 * so a switch between them is a jump in the reference, not a climb.
 */
enum class ClimbSource(val hysteresisM: Double) {
    /** Barometer calibrated against GNSS: smooth to a metre or so. */
    BAROMETER(3.0),
    /** Barometer on the standard atmosphere, before calibration: smooth, but offset. */
    BAROMETER_STANDARD(3.0),
    /** GNSS height alone: 5–10 m of slowly wandering noise, so a wider band. */
    GNSS(10.0),
}

data class TripAccumulator(
    val stats: TripStats = TripStats(0, 0.0, 0L, 0L, null, null, 0.0, 0.0),
    private val firstTimeMs: Long? = null,
    private val previous: TripPoint? = null,
    /** Last point that was counted for distance. */
    private val anchor: TripPoint? = null,
    /** Altitude at which the last climb or descent was counted. */
    private val climbReferenceM: Double? = null,
    /** Where [climbReferenceM] came from; a different source starts a new reference. */
    private val climbSource: ClimbSource? = null,
) {
    /**
     * [climbAltitudeM] is the altitude to use for ascent/descent, from [source]; may be null.
     * When the source changes, the new altitude becomes the reference without counting the
     * step between the two.
     */
    fun add(point: TripPoint, climbAltitudeM: Double?, source: ClimbSource = ClimbSource.BAROMETER): TripAccumulator {
        val first = firstTimeMs ?: point.timeUtcMs

        var distance = stats.distanceM
        var newAnchor = anchor
        if (point.accuracyM == null || point.accuracyM <= MAX_ACCURACY_M) {
            val a = anchor
            if (a == null) {
                newAnchor = point
            } else {
                val d = NetworkComparison.distanceM(a.latitude, a.longitude, point.latitude, point.longitude)
                val threshold = maxOf(MIN_SEGMENT_M, ((a.accuracyM ?: 0f) + (point.accuracyM ?: 0f)) / 2.0)
                if (d > threshold || (point.speedMps ?: 0f) > MOVING_SPEED_MPS) {
                    distance += d
                    newAnchor = point
                }
            }
        }

        var moving = stats.movingTimeMs
        val prev = previous
        if (prev != null) {
            val dt = point.timeUtcMs - prev.timeUtcMs
            if (dt in 1..MAX_GAP_MS) {
                // Without a reported speed, fall back to the displacement over the interval.
                val speed = point.speedMps?.toDouble()
                    ?: (NetworkComparison.distanceM(prev.latitude, prev.longitude, point.latitude, point.longitude) /
                        (dt / 1000.0))
                if (speed > MOVING_SPEED_MPS) moving += dt
            } else if (dt > MAX_GAP_MS && distance > stats.distanceM) {
                // A gap the track jumped across — a tunnel, or the app closed on the way. Its
                // distance was counted above, so its time must be too, or the average moving
                // speed would divide the whole jump by the few seconds either side of it. The
                // displacement over the gap decides whether it was travelled or stood.
                val gapSpeed = (distance - stats.distanceM) / (dt / 1000.0)
                if (gapSpeed > MOVING_SPEED_MPS) moving += dt
            }
        }

        var ascent = stats.ascentM
        var descent = stats.descentM
        var reference = climbReferenceM
        var referenceSource = climbSource
        if (climbAltitudeM != null && climbAltitudeM.isFinite()) {
            val ref = reference
            if (ref == null || source != climbSource) {
                reference = climbAltitudeM
                referenceSource = source
            } else {
                val delta = climbAltitudeM - ref
                if (abs(delta) >= source.hysteresisM) {
                    if (delta > 0) ascent += delta else descent -= delta
                    reference = climbAltitudeM
                }
            }
        }

        val lastTime = maxOf(point.timeUtcMs, prev?.timeUtcMs ?: point.timeUtcMs)
        return TripAccumulator(
            stats = TripStats(
                points = stats.points + 1,
                distanceM = distance,
                durationMs = (lastTime - first).coerceAtLeast(0L),
                movingTimeMs = moving,
                maxSpeedMps = listOfNotNull(stats.maxSpeedMps, point.speedMps).maxOrNull(),
                avgMovingSpeedMps = if (moving > 0L) distance / (moving / 1000.0) else null,
                ascentM = ascent,
                descentM = descent,
            ),
            firstTimeMs = first,
            previous = point,
            anchor = newAnchor,
            climbReferenceM = reference,
            climbSource = referenceSource,
        )
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        const val MIN_SEGMENT_M = 3.0
        const val MOVING_SPEED_MPS = 0.5f
        /** Intervals above this are reception gaps, judged by their overall displacement. */
        const val MAX_GAP_MS = 30_000L
    }
}

/**
 * One trip point per line, so a recording can be appended to a file as it happens and
 * survive the process being killed. Locale independent ('.' decimals); nullable fields are
 * left empty: `time,lat,lon,alt,speed,accuracy`.
 */
object TripCsv {
    fun encode(p: TripPoint): String = listOf(
        p.timeUtcMs.toString(),
        p.latitude.toString(),
        p.longitude.toString(),
        p.altitudeM?.toString().orEmpty(),
        p.speedMps?.toString().orEmpty(),
        p.accuracyM?.toString().orEmpty(),
    ).joinToString(",")

    /** Null for a malformed line, for example one truncated by a crash mid-write. */
    fun decode(line: String): TripPoint? {
        val f = line.trim().split(',').map { it.trim() }
        if (f.size != 6) return null
        val time = f[0].toLongOrNull() ?: return null
        // Finite and in range only: "NaN" and "Infinity" parse as numbers but would end up
        // as invalid coordinates in an exported GPX.
        val lat = f[1].toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val lon = f[2].toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val alt = if (f[3].isEmpty()) null else f[3].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        val speed = if (f[4].isEmpty()) null else f[4].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        val acc = if (f[5].isEmpty()) null else f[5].toFloatOrNull()?.takeIf { it.isFinite() } ?: return null
        return TripPoint(time, lat, lon, alt, speed, acc)
    }
}

/** GPX 1.1 export, the format every mapping tool can import. */
object Gpx {
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    fun write(points: List<TripPoint>, name: String, out: Appendable) {
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append(
            "<gpx version=\"1.1\" creator=\"Positioning Info\" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n",
        )
        out.append("  <trk>\n")
        out.append("    <name>").append(escape(name)).append("</name>\n")
        out.append("    <trkseg>\n")
        for (p in points) {
            out.append("      <trkpt lat=\"").append(coordinate(p.latitude))
                .append("\" lon=\"").append(coordinate(p.longitude)).append("\">")
            p.altitudeM?.let { out.append("<ele>").append(String.format(Locale.ROOT, "%.2f", it)).append("</ele>") }
            out.append("<time>").append(formatTime(p.timeUtcMs)).append("</time>")
            out.append("</trkpt>\n")
        }
        out.append("    </trkseg>\n")
        out.append("  </trk>\n")
        out.append("</gpx>\n")
    }

    /** ISO-8601 UTC with milliseconds, e.g. `2026-09-28T19:12:03.250Z`. */
    fun formatTime(timeUtcMs: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(timeUtcMs))

    /** Plain decimal: never scientific notation, never a locale's decimal comma. */
    private fun coordinate(deg: Double): String = String.format(Locale.ROOT, "%.8f", deg)

    private fun escape(s: String): String = buildString {
        for (c in s) {
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                // Other control characters are not allowed in XML 1.0 at all.
                c < ' ' && c != '\t' && c != '\n' && c != '\r' -> Unit
                else -> append(c)
            }
        }
    }
}
