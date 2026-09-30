// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * One NMEA 0183 sentence as the phone's GNSS chip emits it.
 *
 * Android's Location API hides much of what the chip knows: the receiver's own accuracy
 * estimate per axis (GST), which satellites each constellation solved with (GSA) and the
 * height above the geoid (GGA). The raw sentences carry it, so they are parsed here.
 *
 * Only the handful of types this app shows are decoded; everything else is kept as
 * [OtherSentence] so it can still be counted.
 */
sealed interface NmeaSentence {
    /** "GP", "GN", "GL", ... or "P" for a proprietary sentence. */
    val talker: String

    /** "GGA", "GSV", ... or, for a proprietary sentence, the rest of the address field. */
    val type: String
}

/** Fix data. Latitude and longitude in signed decimal degrees, north and east positive. */
data class Gga(
    override val talker: String,
    val latitude: Double?,
    val longitude: Double?,
    /** 0 = no fix, 1 = GNSS, 2 = differential, 4 = RTK fixed, 5 = RTK float, 6 = dead reckoning. */
    val fixQuality: Int?,
    val satellites: Int?,
    val hdop: Double?,
    val altitudeMslM: Double?,
    /** Geoid height above the WGS-84 ellipsoid. */
    val geoidSeparationM: Double?,
) : NmeaSentence {
    override val type get() = "GGA"
}

/** DOP and active satellites. Receivers send one per constellation, told apart by [systemId]. */
data class Gsa(
    override val talker: String,
    /** 'A' automatic or 'M' manual 2D/3D selection. */
    val mode: Char?,
    /** 1 = no fix, 2 = 2D, 3 = 3D. */
    val fixType: Int?,
    val prns: List<Int>,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
    /** NMEA 4.10 GNSS system ID (1 GPS, 2 GLONASS, 3 Galileo, 4 BeiDou, ...), null before 4.10. */
    val systemId: Int?,
) : NmeaSentence {
    override val type get() = "GSA"
}

/** The receiver's own error estimate: one sigma, in metres. */
data class Gst(
    override val talker: String,
    val rmsM: Double?,
    val semiMajorM: Double?,
    val semiMinorM: Double?,
    /** Orientation of the semi-major axis, degrees from true north. */
    val orientationDeg: Double?,
    val latSigmaM: Double?,
    val lonSigmaM: Double?,
    val altSigmaM: Double?,
) : NmeaSentence {
    override val type get() = "GST"
}

/** Recommended minimum data. [valid] is the status field: 'A' valid, 'V' warning. */
data class Rmc(override val talker: String, val valid: Boolean, val speedKnots: Double?, val courseDeg: Double?) :
    NmeaSentence {
    override val type get() = "RMC"
}

/** A sentence type not decoded here, kept only so it can be counted. */
data class OtherSentence(override val talker: String, override val type: String) : NmeaSentence

/** Turns single NMEA lines into [NmeaSentence]s; stateless. */
object NmeaParser {

    private class Malformed : Exception()

    /** Fewer fields than the sentence type always carries: the line was cut off. */
    private fun List<String>.requireFields(count: Int) {
        if (size < count) throw Malformed()
    }

    private const val DEGREES_SHIFT = 100.0
    private const val MINUTES_PER_DEGREE = 60.0

    /** Fields, counting the address, that each decoded sentence type always carries. */
    private const val GGA_FIELDS = 12
    private const val GSA_FIELDS = 18
    private const val GST_FIELDS = 9
    private const val RMC_FIELDS = 10

    /**
     * Null when malformed or the checksum is wrong. A `*hh` checksum is required: a chip
     * always sends one, and a line without it is more likely truncated than trustworthy.
     *
     * Tolerates trailing whitespace (the `\r\n` the chip terminates each line with) and
     * proprietary `$P...` sentences, returned as [OtherSentence] with talker "P".
     */
    fun parse(line: String): NmeaSentence? {
        val body = checkedBody(line) ?: return null
        val f = body.split(',')
        val address = f[0]
        if (address.isEmpty() || !address.all { it.isLetterOrDigit() }) return null
        if (address[0] == 'P') {
            return if (address.length > 1) OtherSentence("P", address.substring(1)) else null
        }
        if (address.length < TALKER_LENGTH + TYPE_LENGTH) return null
        val talker = address.dropLast(TYPE_LENGTH)
        val type = address.takeLast(TYPE_LENGTH)
        return try {
            when (type) {
                "GGA" -> gga(talker, f)
                "GSA" -> gsa(talker, f)
                "GST" -> gst(talker, f)
                "RMC" -> rmc(talker, f)
                else -> OtherSentence(talker, type)
            }
        } catch (_: Malformed) {
            null
        }
    }

    /**
     * The sentence between `$` and `*hh`, if the line is complete, printable and its checksum —
     * the XOR of every character in between — matches; null otherwise.
     */
    private fun checkedBody(line: String): String? {
        val s = line.trimEnd()
        if (s.length < MIN_LINE_LENGTH || s[0] != '$') return null
        val star = s.lastIndexOf('*')
        if (star < 0 || s.length != star + 1 + CHECKSUM_DIGITS) return null
        val hex = s.substring(star + 1)
        // Exactly two hex digits: toIntOrNull alone would also take "+7".
        if (!hex.all { it in '0'..'9' || it in 'A'..'F' || it in 'a'..'f' }) return null
        val body = s.substring(1, star)
        if (!body.all { it.code in PRINTABLE_ASCII }) return null
        val sum = body.fold(0) { acc, c -> acc xor c.code }
        return body.takeIf { sum == hex.toInt(HEX_RADIX) }
    }

    private fun gga(talker: String, f: List<String>): Gga {
        f.requireFields(GGA_FIELDS)
        return Gga(
            talker = talker,
            latitude = coordinate(f[GGA_LAT], f[GGA_LAT + 1], 'N', 'S', maxDegrees = MAX_LATITUDE),
            longitude = coordinate(f[GGA_LON], f[GGA_LON + 1], 'E', 'W', maxDegrees = MAX_LONGITUDE),
            fixQuality = int(f[GGA_QUALITY]),
            satellites = int(f[GGA_SATELLITES]),
            hdop = double(f[GGA_HDOP]),
            altitudeMslM = double(f[GGA_ALTITUDE]),
            geoidSeparationM = double(f[GGA_GEOID_SEPARATION]),
        )
    }

    private fun gsa(talker: String, f: List<String>): Gsa {
        f.requireFields(GSA_FIELDS)
        return Gsa(
            talker = talker,
            mode = f[GSA_MODE].singleOrNull(),
            fixType = int(f[GSA_FIX_TYPE]),
            prns = GSA_PRNS.mapNotNull { int(f[it]) },
            pdop = double(f[GSA_PDOP]),
            hdop = double(f[GSA_PDOP + 1]),
            vdop = double(f[GSA_PDOP + 2]),
            systemId = f.getOrNull(GSA_SYSTEM_ID)?.let { int(it) },
        )
    }

    private fun gst(talker: String, f: List<String>): Gst {
        f.requireFields(GST_FIELDS)
        return Gst(
            talker = talker,
            rmsM = double(f[GST_RMS]),
            semiMajorM = double(f[GST_SEMI_MAJOR]),
            semiMinorM = double(f[GST_SEMI_MINOR]),
            orientationDeg = double(f[GST_ORIENTATION]),
            latSigmaM = double(f[GST_LAT_SIGMA]),
            lonSigmaM = double(f[GST_LON_SIGMA]),
            altSigmaM = double(f[GST_ALT_SIGMA]),
        )
    }

    private fun rmc(talker: String, f: List<String>): Rmc {
        f.requireFields(RMC_FIELDS)
        return Rmc(
            talker = talker,
            valid = f[RMC_STATUS] == "A",
            speedKnots = double(f[RMC_SPEED]),
            courseDeg = double(f[RMC_SPEED + 1]),
        )
    }

    // Envelope: "$" + talker (usually 2 letters) + type (3) … "*" + two hex digits.
    private const val TALKER_LENGTH = 2
    private const val TYPE_LENGTH = 3
    private const val CHECKSUM_DIGITS = 2
    private const val MIN_LINE_LENGTH = 1 + CHECKSUM_DIGITS + 1
    private const val HEX_RADIX = 16
    private val PRINTABLE_ASCII = 0x20..0x7E

    // Field positions (0 = the address) per NMEA 0183; a coordinate is followed by its hemisphere.
    private const val GGA_LAT = 2
    private const val GGA_LON = 4
    private const val GGA_QUALITY = 6
    private const val GGA_SATELLITES = 7
    private const val GGA_HDOP = 8
    private const val GGA_ALTITUDE = 9
    private const val GGA_GEOID_SEPARATION = 11
    private const val GSA_MODE = 1
    private const val GSA_FIX_TYPE = 2

    /** Twelve slots for the PRNs of the satellites used. */
    private val GSA_PRNS = 3..14
    private const val GSA_PDOP = 15
    private const val GSA_SYSTEM_ID = 18

    private const val GST_RMS = 2
    private const val GST_SEMI_MAJOR = 3
    private const val GST_SEMI_MINOR = 4
    private const val GST_ORIENTATION = 5
    private const val GST_LAT_SIGMA = 6
    private const val GST_LON_SIGMA = 7
    private const val GST_ALT_SIGMA = 8
    private const val RMC_STATUS = 2
    private const val RMC_SPEED = 7
    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    /** Empty means "not known"; anything else that is not a number means the line is broken. */
    private fun double(field: String): Double? =
        if (field.isEmpty()) null else field.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw Malformed()

    private fun int(field: String): Int? = if (field.isEmpty()) null else field.toIntOrNull() ?: throw Malformed()

    /** `ddmm.mmmm` / `dddmm.mmmm` plus hemisphere to signed decimal degrees. */
    private fun coordinate(
        value: String,
        hemisphere: String,
        positive: Char,
        negative: Char,
        maxDegrees: Double,
    ): Double? {
        val raw = double(value) ?: return null
        // ddmm.mmmm: the degrees are the hundreds.
        val degrees = Math.floor(raw / DEGREES_SHIFT)
        val minutes = raw - degrees * DEGREES_SHIFT
        val abs = degrees + minutes / MINUTES_PER_DEGREE
        // Negative, 60 minutes or more, or beyond the pole or the antimeridian: not a coordinate.
        if (raw < 0 || minutes >= MINUTES_PER_DEGREE || abs > maxDegrees) throw Malformed()
        return when (hemisphere.singleOrNull()) {
            positive -> abs
            negative -> -abs
            else -> throw Malformed()
        }
    }
}

/**
 * Everything seen on the NMEA stream so far: how many of each type, and the latest of each
 * decoded kind.
 *
 * Several GSA arrive per epoch, one per constellation, and some chips pad the stream with
 * empty ones; the latest carrying a PDOP is kept so the DOP shown does not flicker to blank.
 *
 * [total] counts every line offered, [rejected] those that failed to parse.
 * Immutable, like [SessionStats]: every line returns a new instance.
 */
data class NmeaState(
    val counts: Map<String, Int> = emptyMap(),
    val gga: Gga? = null,
    val gsa: Gsa? = null,
    val gst: Gst? = null,
    val rmc: Rmc? = null,
    val total: Int = 0,
    val rejected: Int = 0,
) {
    /** Counts [line] and keeps it if it is the latest of its decoded kind; a broken line only counts as rejected. */
    fun onLine(line: String): NmeaState {
        val sentence = NmeaParser.parse(line)
            ?: return copy(total = total + 1, rejected = rejected + 1)
        val counted = copy(counts = counts + (sentence.type to (counts[sentence.type] ?: 0) + 1), total = total + 1)
        return when (sentence) {
            is Gga -> counted.copy(gga = sentence)
            is Gsa -> if (sentence.pdop != null) counted.copy(gsa = sentence) else counted
            is Gst -> counted.copy(gst = sentence)
            is Rmc -> counted.copy(rmc = sentence)
            is OtherSentence -> counted
        }
    }
}
