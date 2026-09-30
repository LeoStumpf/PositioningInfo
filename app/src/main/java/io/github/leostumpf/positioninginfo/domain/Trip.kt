// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** One recorded fix: UTC time, degrees, altitude in metres, speed in m/s and horizontal accuracy in metres. */
data class TripPoint(
    val timeUtcMs: Long,
    val latitude: Double,
    val longitude: Double,
    /** The best height above sea level at the time: calibrated barometer, else GNSS. For the profile and GPX. */
    val altitudeM: Double?,
    val speedMps: Float?,
    val accuracyM: Float?,
    /**
     * The height ascent and descent are counted from, and its source. Kept apart from [altitudeM]
     * because before calibration the barometer's standard-atmosphere height is smoother than GNSS
     * but tens of metres off; saving both lets a reloaded trip count exactly what was shown live.
     */
    val climbAltitudeM: Double? = null,
    val climbSource: ClimbSource? = null,
)

/** Totals of a trip so far; distances and heights in metres, times in milliseconds, speeds in m/s. */
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
 * Where a climb altitude comes from. The sources disagree by tens of metres — the standard
 * atmosphere is off by about 8 m per hPa of weather, GNSS height by its geoid and its noise —
 * so a switch between them is a jump in the reference, not a climb.
 */
enum class ClimbSource(val hysteresisM: Double, val code: Char) {
    /** Barometer calibrated against GNSS: smooth to a metre or so. */
    BAROMETER(3.0, 'B'),

    /** Barometer on the standard atmosphere, before calibration: smooth, but offset. */
    BAROMETER_STANDARD(3.0, 'S'),

    /** GNSS height alone: 5–10 m of slowly wandering noise, so a wider band. */
    GNSS(10.0, 'G'),
    ;

    companion object {
        /** The source saved as [code] in a trip file; null for an unknown code. */
        fun fromCode(code: Char): ClimbSource? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Running statistics of a recorded trip.
 *
 * GNSS positions wander by metres even when the phone lies still; summing every step would
 * let a parked phone "travel" kilometres. So a segment only counts once the position has
 * moved further than its own uncertainty (at least [MIN_SEGMENT_M]) from the last counted
 * point, or the receiver reports real motion (> [MOVING_SPEED_MPS]). Fixes worse than
 * [MAX_ACCURACY_M] never contribute distance. Across a reception gap longer than [MAX_GAP_MS]
 * the jump in position still counts, and so does the gap's time as moving time whenever the
 * jump implies travel — distance and moving time always cover the same stretches. Ascent and
 * descent use a hysteresis for the same reason, as wide as the altitude source is noisy (see
 * [ClimbSource]): altitude noise must not add up to phantom climbing.
 *
 * Immutable: [add] returns a new instance.
 */
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
     * Adds [point]. Ascent and descent come from its [TripPoint.climbAltitudeM]; when its
     * [TripPoint.climbSource] differs from the last one, the new altitude becomes the reference
     * without counting the step between the two.
     */
    fun add(point: TripPoint): TripAccumulator {
        val first = firstTimeMs ?: point.timeUtcMs
        val (distanceStep, newAnchor) = distanceStep(point)
        val distance = stats.distanceM + distanceStep
        val moving = stats.movingTimeMs + movingTimeStep(point, distanceStep)
        val climb = climbStep(point.climbAltitudeM, point.climbSource)
        val lastTime = maxOf(point.timeUtcMs, previous?.timeUtcMs ?: point.timeUtcMs)
        return TripAccumulator(
            stats = TripStats(
                points = stats.points + 1,
                distanceM = distance,
                durationMs = (lastTime - first).coerceAtLeast(0L),
                movingTimeMs = moving,
                maxSpeedMps = listOfNotNull(stats.maxSpeedMps, point.speedMps).maxOrNull(),
                avgMovingSpeedMps = if (moving > 0L) distance / (moving / MS_PER_S) else null,
                ascentM = stats.ascentM + climb.ascentM,
                descentM = stats.descentM + climb.descentM,
            ),
            firstTimeMs = first,
            previous = point,
            anchor = newAnchor,
            climbReferenceM = climb.referenceM,
            climbSource = climb.source,
        )
    }

    /**
     * The distance [point] adds, and the anchor to measure the next one from. A segment counts
     * once the position has moved beyond both fixes' uncertainty, or the receiver reports
     * motion; an imprecise fix adds nothing and leaves the anchor where it was.
     */
    private fun distanceStep(point: TripPoint): Pair<Double, TripPoint?> {
        if (point.accuracyM != null && point.accuracyM > MAX_ACCURACY_M) return 0.0 to anchor
        val a = anchor ?: return 0.0 to point
        val d = NetworkComparison.distanceM(a.latitude, a.longitude, point.latitude, point.longitude)
        val threshold = maxOf(MIN_SEGMENT_M, ((a.accuracyM ?: 0f) + (point.accuracyM ?: 0f)) / 2.0)
        val moved = d > threshold || (point.speedMps ?: 0f) > MOVING_SPEED_MPS
        return if (moved) d to point else 0.0 to a
    }

    /** The moving time [point] adds after the previous one; [distanceStep] is what it added in distance. */
    private fun movingTimeStep(point: TripPoint, distanceStep: Double): Long {
        val prev = previous ?: return 0L
        val dt = point.timeUtcMs - prev.timeUtcMs
        val speed = when {
            dt <= 0 -> return 0L

            // Without a reported speed, fall back to the displacement over the interval.
            dt <= MAX_GAP_MS -> point.speedMps?.toDouble() ?: (displacementM(prev, point) / (dt / MS_PER_S))

            // A gap the track jumped across — a tunnel, or the app closed on the way. Its distance
            // counts, so its time must too, or the average moving speed would divide the whole
            // jump by the few seconds either side of it. The displacement over the gap decides
            // whether it was travelled or stood.
            else -> distanceStep / (dt / MS_PER_S)
        }
        return if (speed > MOVING_SPEED_MPS) dt else 0L
    }

    private fun displacementM(from: TripPoint, to: TripPoint): Double =
        NetworkComparison.distanceM(from.latitude, from.longitude, to.latitude, to.longitude)

    /** What one altitude reading adds to ascent and descent, and the reference it leaves. */
    private class ClimbStep(
        val ascentM: Double,
        val descentM: Double,
        val referenceM: Double?,
        val source: ClimbSource?,
    )

    private fun climbStep(altitudeM: Double?, source: ClimbSource?): ClimbStep {
        val ref = climbReferenceM
        return when {
            altitudeM == null || !altitudeM.isFinite() || source == null -> ClimbStep(0.0, 0.0, ref, climbSource)

            // A new source starts a new reference: the step between two sources is no climb.
            ref == null || source != climbSource -> ClimbStep(0.0, 0.0, altitudeM, source)

            abs(altitudeM - ref) < source.hysteresisM -> ClimbStep(0.0, 0.0, ref, climbSource)

            altitudeM > ref -> ClimbStep(altitudeM - ref, 0.0, altitudeM, source)

            else -> ClimbStep(0.0, ref - altitudeM, altitudeM, source)
        }
    }

    companion object {
        const val MAX_ACCURACY_M = 30f
        private const val MS_PER_S = 1_000.0
        const val MIN_SEGMENT_M = 3.0
        const val MOVING_SPEED_MPS = 0.5f

        /** Intervals above this are reception gaps, judged by their overall displacement. */
        const val MAX_GAP_MS = 30_000L
    }
}

/**
 * One trip point per line, so a recording can be appended to a file as it happens and
 * survive the process being killed. Locale independent ('.' decimals); nullable fields are
 * left empty: `time,lat,lon,alt,speed,accuracy,climbAlt,climbSource`, the source as its
 * [ClimbSource.code]. The file starts with [HEADER]; a file without it is from another format
 * and is not read.
 */
object TripCsv {
    /** First line of every trip file; bumped whenever the line format changes. */
    const val HEADER = "# Positioning Info trip, format 2"

    /** One CSV line for [p], without the line break. */
    fun encode(p: TripPoint): String = listOf(
        p.timeUtcMs.toString(),
        p.latitude.toString(),
        p.longitude.toString(),
        p.altitudeM?.toString().orEmpty(),
        p.speedMps?.toString().orEmpty(),
        p.accuracyM?.toString().orEmpty(),
        p.climbAltitudeM?.toString().orEmpty(),
        p.climbSource?.code?.toString().orEmpty(),
    ).joinToString(",")

    /** The points of a whole file, [HEADER] first; empty when the header is missing or different. */
    fun decodeFile(lines: List<String>): List<TripPoint> =
        if (lines.firstOrNull()?.trim() != HEADER) emptyList() else lines.drop(1).mapNotNull(::decode)

    private const val FIELDS = 8

    /** Null for a malformed line, for example one truncated by a crash mid-write. */
    fun decode(line: String): TripPoint? {
        val f = line.trim().split(',').map { it.trim() }
        if (f.size != FIELDS) return null
        val time = f[0].toLongOrNull() ?: return null
        // Finite and in range only: "NaN" and "Infinity" parse as numbers but would end up
        // as invalid coordinates in an exported GPX.
        val lat = f[1].toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: return null
        val lon = f[2].toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } ?: return null
        val alt = optional(f[3]) { it.toDoubleOrNull()?.takeIf(Double::isFinite) } ?: return null
        val speed = optional(f[4]) { it.toFloatOrNull()?.takeIf(Float::isFinite) } ?: return null
        val acc = optional(f[5]) { it.toFloatOrNull()?.takeIf(Float::isFinite) } ?: return null
        val climbAlt = optional(f[6]) { it.toDoubleOrNull()?.takeIf(Double::isFinite) } ?: return null
        val source = optional(f[7]) { it.singleOrNull()?.let(ClimbSource::fromCode) } ?: return null
        return TripPoint(time, lat, lon, alt.value, speed.value, acc.value, climbAlt.value, source.value)
    }

    /** A field that may be empty: [Field] of null when empty, null when present but invalid. */
    private fun <T : Any> optional(text: String, parse: (String) -> T?): Field<T>? =
        if (text.isEmpty()) Field(null) else parse(text)?.let { Field(it) }

    /** A decoded optional field; wrapped so "empty" and "invalid" can be told apart. */
    private class Field<T : Any>(val value: T?)
}

/** GPX 1.1 export, the format every mapping tool can import. */
object Gpx {
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    /** Writes [points] as a single-segment track called [name]; the caller closes [out]. */
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
