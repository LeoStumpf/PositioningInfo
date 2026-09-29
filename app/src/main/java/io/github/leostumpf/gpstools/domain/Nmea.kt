// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

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
data class Rmc(
    override val talker: String,
    val valid: Boolean,
    val speedKnots: Double?,
    val courseDeg: Double?,
) : NmeaSentence {
    override val type get() = "RMC"
}

data class OtherSentence(override val talker: String, override val type: String) : NmeaSentence

object NmeaParser {

    private class Malformed : Exception()

    /**
     * Null when malformed or the checksum is wrong. A `*hh` checksum is required: a chip
     * always sends one, and a line without it is more likely truncated than trustworthy.
     *
     * Tolerates trailing whitespace (the `\r\n` the chip terminates each line with) and
     * proprietary `$P...` sentences, returned as [OtherSentence] with talker "P".
     */
    fun parse(line: String): NmeaSentence? {
        val s = line.trimEnd()
        if (s.length < 4 || s[0] != '$') return null
        val star = s.lastIndexOf('*')
        if (star < 0 || s.length != star + 3) return null
        val expected = s.substring(star + 1).toIntOrNull(16) ?: return null
        val body = s.substring(1, star)
        var sum = 0
        for (c in body) {
            if (c.code > 0x7E || c.code < 0x20) return null
            sum = sum xor c.code
        }
        if (sum != expected) return null

        val f = body.split(',')
        val address = f[0]
        if (address.isEmpty() || !address.all { it.isLetterOrDigit() }) return null
        if (address[0] == 'P') {
            return if (address.length > 1) OtherSentence("P", address.substring(1)) else null
        }
        if (address.length < 5) return null
        val talker = address.substring(0, address.length - 3)
        val type = address.substring(address.length - 3)

        return try {
            when (type) {
                "GGA" -> {
                    if (f.size < 12) throw Malformed()
                    Gga(
                        talker = talker,
                        latitude = coordinate(f[2], f[3], 'N', 'S'),
                        longitude = coordinate(f[4], f[5], 'E', 'W'),
                        fixQuality = int(f[6]),
                        satellites = int(f[7]),
                        hdop = double(f[8]),
                        altitudeMslM = double(f[9]),
                        geoidSeparationM = double(f[11]),
                    )
                }
                "GSA" -> {
                    if (f.size < 18) throw Malformed()
                    Gsa(
                        talker = talker,
                        mode = f[1].singleOrNull(),
                        fixType = int(f[2]),
                        prns = (3..14).mapNotNull { int(f[it]) },
                        pdop = double(f[15]),
                        hdop = double(f[16]),
                        vdop = double(f[17]),
                        systemId = f.getOrNull(18)?.let { int(it) },
                    )
                }
                "GST" -> {
                    if (f.size < 9) throw Malformed()
                    Gst(
                        talker = talker,
                        rmsM = double(f[2]),
                        semiMajorM = double(f[3]),
                        semiMinorM = double(f[4]),
                        orientationDeg = double(f[5]),
                        latSigmaM = double(f[6]),
                        lonSigmaM = double(f[7]),
                        altSigmaM = double(f[8]),
                    )
                }
                "RMC" -> {
                    if (f.size < 10) throw Malformed()
                    Rmc(
                        talker = talker,
                        valid = f[2] == "A",
                        speedKnots = double(f[7]),
                        courseDeg = double(f[8]),
                    )
                }
                else -> OtherSentence(talker, type)
            }
        } catch (_: Malformed) {
            null
        }
    }

    /** Empty means "not known"; anything else that is not a number means the line is broken. */
    private fun double(field: String): Double? =
        if (field.isEmpty()) null else field.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw Malformed()

    private fun int(field: String): Int? =
        if (field.isEmpty()) null else field.toIntOrNull() ?: throw Malformed()

    /** `ddmm.mmmm` / `dddmm.mmmm` plus hemisphere to signed decimal degrees. */
    private fun coordinate(value: String, hemisphere: String, positive: Char, negative: Char): Double? {
        val raw = double(value) ?: return null
        if (raw < 0) throw Malformed()
        val degrees = Math.floor(raw / 100.0)
        val minutes = raw - degrees * 100.0
        if (minutes >= 60.0) throw Malformed()
        val abs = degrees + minutes / 60.0
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
    fun onLine(line: String): NmeaState {
        val sentence = NmeaParser.parse(line)
            ?: return copy(total = total + 1, rejected = rejected + 1)
        val counted = copy(
            counts = counts + (sentence.type to (counts[sentence.type] ?: 0) + 1),
            total = total + 1,
        )
        return when (sentence) {
            is Gga -> counted.copy(gga = sentence)
            is Gsa -> if (sentence.pdop != null) counted.copy(gsa = sentence) else counted
            is Gst -> counted.copy(gst = sentence)
            is Rmc -> counted.copy(rmc = sentence)
            is OtherSentence -> counted
        }
    }
}
