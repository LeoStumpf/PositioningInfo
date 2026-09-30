// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/**
 * Decodes GPS L1 C/A LNAV subframes as Android's GnssNavigationMessage delivers them.
 *
 * The app has no network access, so the navigation message is the only source for what the
 * satellites themselves broadcast: their health, the ionosphere model, the GPS-UTC offset
 * and upcoming leap seconds. Android hands the bits over raw, so parity and polarity are
 * handled here (IS-GPS-200, section 20.3.5).
 *
 * Data format: 10 words of 30 bits, each in the low 30 bits of a big-endian 4-byte group,
 * exactly as transmitted, i.e. data bits d1..d24 inverted whenever the previous word's
 * last bit (D30*) was 1.
 */
object GpsNavDecoder {

    private const val BYTES_PER_WORD = 4

    const val PREAMBLE = 0x8B
    const val WORDS = 10
    const val BYTES = WORDS * BYTES_PER_WORD

    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF
    private const val WORD_BITS = 30
    private const val DATA_BITS = 24
    private const val PARITY_BIT_COUNT = 6
    private const val PREAMBLE_BITS = 8
    private const val DATA_MASK = (1 shl DATA_BITS) - 1
    private const val PARITY_MASK = (1 shl PARITY_BIT_COUNT) - 1
    private const val WORD_MASK = (1 shl WORD_BITS) - 1

    /** IS-GPS-200 Table 20-XIV: data bits (1-based, d1 = MSB) feeding each parity bit D25..D30. */
    private val PARITY_BITS = listOf(
        intArrayOf(1, 2, 3, 5, 6, 10, 11, 12, 13, 14, 17, 18, 20, 23),
        intArrayOf(2, 3, 4, 6, 7, 11, 12, 13, 14, 15, 18, 19, 21, 24),
        intArrayOf(1, 3, 4, 5, 7, 8, 12, 13, 14, 15, 16, 19, 20, 22),
        intArrayOf(2, 4, 5, 6, 8, 9, 13, 14, 15, 16, 17, 20, 21, 23),
        intArrayOf(1, 3, 5, 6, 7, 9, 10, 14, 15, 16, 17, 18, 21, 22, 24),
        intArrayOf(3, 5, 6, 8, 9, 10, 11, 13, 15, 19, 22, 23, 24),
    )
    private val PARITY_MASKS = PARITY_BITS.map { bits -> bits.fold(0) { m, b -> m or (1 shl (DATA_BITS - b)) } }

    /** Which of the previous word's bits enters each parity bit: true = D29*, false = D30*. */
    private val USES_D29 = booleanArrayOf(true, false, true, false, false, true)

    /** The six parity bits D25..D30 (D25 = MSB) for 24 source data bits. */
    fun parity(data: Int, d29Star: Int, d30Star: Int): Int {
        var p = 0
        for (i in 0 until PARITY_BIT_COUNT) {
            val seed = if (USES_D29[i]) d29Star else d30Star
            val bit = seed xor (Integer.bitCount(data and PARITY_MASKS[i]) and 1)
            p = (p shl 1) or bit
        }
        return p
    }

    /**
     * Number of words failing parity, or null if the subframe is too short or word 1 does not
     * begin with the preamble in either polarity.
     */
    fun parityFailures(data: ByteArray): Int? = decode(data)?.second

    /**
     * 10 words of 24 corrected data bits each (d1 = MSB of the 24), or null if the preamble
     * or parity is invalid or data.size < 40.
     */
    fun words(data: ByteArray): IntArray? {
        val (words, failures) = decode(data) ?: return null
        return if (failures == 0) words else null
    }

    private fun decode(data: ByteArray): Pair<IntArray, Int>? {
        if (data.size < BYTES) return null
        val raw = IntArray(WORDS) { i -> bigEndianInt(data, i * BYTES_PER_WORD) and WORD_MASK }
        // The previous subframe is not delivered, so its D29*/D30* are unknown. Word 10 is
        // built to end in 00, so word 1 goes out upright; reading the preamble inverted means
        // the receiver locked 180° out of phase and every bit, D29*/D30* included, is flipped.
        var d29: Int
        var d30: Int
        when (raw[0] ushr (WORD_BITS - PREAMBLE_BITS)) {
            PREAMBLE -> {
                d29 = 0
                d30 = 0
            }

            PREAMBLE xor BYTE_MASK -> {
                d29 = 1
                d30 = 1
            }

            else -> return null
        }
        var failures = 0
        val words = IntArray(WORDS)
        for (i in 0 until WORDS) {
            val w = raw[i]
            val d = (w ushr PARITY_BIT_COUNT).let { if (d30 == 1) it xor DATA_MASK else it }
            if (parity(d, d29, d30) != (w and PARITY_MASK)) failures++
            words[i] = d
            d29 = (w ushr 1) and 1
            d30 = w and 1
        }
        return words to failures
    }

    /** Four bytes from [offset] as one big-endian Int. */
    private fun bigEndianInt(data: ByteArray, offset: Int): Int = (0 until BYTES_PER_WORD).fold(
        0,
    ) { acc, k -> (acc shl BITS_PER_BYTE) or (data[offset + k].toInt() and BYTE_MASK) }
}

/**
 * One field of a subframe as IS-GPS-200 lays it out: [word] 1..10 and the 1-based [first] bit
 * of its 24 data bits (bit 1 = MSB), [length] bits long; [signed] when two's complement.
 */
private class NavField(val word: Int, val first: Int, val length: Int, val signed: Boolean = false) {
    fun readFrom(words: IntArray): Int {
        val raw = (words[word - 1] ushr (DATA_BITS - first - length + 1)) and ((1 shl length) - 1)
        return if (signed) (raw shl (Int.SIZE_BITS - length)) shr (Int.SIZE_BITS - length) else raw
    }

    private companion object {
        const val DATA_BITS = 24
    }
}

/**
 * The broadcast Klobuchar ionosphere model.
 * [alpha]: s, s/semicircle, s/semicircle², s/semicircle³. [beta]: s, s/semicircle, ...
 */
data class Klobuchar(val alpha: List<Double>, val beta: List<Double>)

/**
 * GPS to UTC: UTC = GPS time − ΔtLS − (A0 + A1·(t − tot)), and the next leap second.
 *
 * [tot] in seconds of the week, [wnt] and [wnLsf] as broadcast (8 bits, modulo 256),
 * [dn] the day (1..7) at whose end the leap second takes effect.
 */
data class GpsUtcParams(
    val a0: Double,
    val a1: Double,
    val tot: Int,
    val wnt: Int,
    val deltaTls: Int,
    val wnLsf: Int,
    val dn: Int,
    val deltaTlsf: Int,
) {
    val leapSecondPending: Boolean get() = deltaTlsf != deltaTls
}

/**
 * What has been read from the GPS navigation messages so far.
 *
 * Each satellite reports its own health in subframe 1, which is fresher than the almanac
 * status other satellites repeat about it; subframe 4 page 18 is the only place the
 * ionosphere model and the leap-second schedule can be learned offline.
 *
 * Immutable, like [SessionStats]: every subframe returns a new instance.
 */
data class GpsNavState(
    /** 10-bit broadcast week (mod 1024) from subframe 1. */
    val weekNumber: Int? = null,
    /** svid -> 6-bit SV health from its own subframe 1 (0 = healthy). */
    val health: Map<Int, Int> = emptyMap(),
    /** svid -> URA index. */
    val ura: Map<Int, Int> = emptyMap(),
    /** Subframe 4 page 18 (SV/page ID 56). */
    val ionosphere: Klobuchar? = null,
    /** Subframe 4 page 18. */
    val utc: GpsUtcParams? = null,
    /** SVs whose almanac page was decoded: subframe 5 SV ID 1..24, subframe 4 SV ID 25..32. */
    val almanacSvids: Set<Int> = emptySet(),
    val subframesDecoded: Int = 0,
    val subframesRejected: Int = 0,
) {
    /** [svid] is the transmitting satellite. Subframe ID comes from the HOW (word 2, bits 20-22). */
    fun onSubframe(svid: Int, data: ByteArray): GpsNavState {
        val w = GpsNavDecoder.words(data) ?: return copy(subframesRejected = subframesRejected + 1)
        val subframeId = SUBFRAME_ID.readFrom(w)
        if (subframeId !in SUBFRAME_IDS) return copy(subframesRejected = subframesRejected + 1)
        val decoded = copy(subframesDecoded = subframesDecoded + 1)
        return when (subframeId) {
            CLOCK_SUBFRAME -> decoded.copy(
                weekNumber = WEEK.readFrom(w),
                ura = ura + (svid to URA_INDEX.readFrom(w)),
                health = health + (svid to SV_HEALTH.readFrom(w)),
            )

            ALMANAC_SUBFRAME_4, ALMANAC_SUBFRAME_5 -> {
                val pageSvId = PAGE_SV_ID.readFrom(w)
                val almanacPage = (subframeId == ALMANAC_SUBFRAME_5 && pageSvId in SUBFRAME_5_ALMANAC_SVIDS) ||
                    (subframeId == ALMANAC_SUBFRAME_4 && pageSvId in SUBFRAME_4_ALMANAC_SVIDS)
                when {
                    almanacPage -> decoded.copy(almanacSvids = almanacSvids + pageSvId)
                    subframeId == ALMANAC_SUBFRAME_4 && pageSvId == PAGE_18_SV_ID -> decoded.withPage18(w)
                    else -> decoded
                }
            }

            else -> decoded
        }
    }

    /** IS-GPS-200 Figure 20-1, subframe 4 page 18. */
    private fun withPage18(w: IntArray): GpsNavState {
        val alpha = listOf(
            ALPHA_0.readFrom(w) * Math.scalb(1.0, -30),
            ALPHA_1.readFrom(w) * Math.scalb(1.0, -27),
            ALPHA_2.readFrom(w) * Math.scalb(1.0, -24),
            ALPHA_3.readFrom(w) * Math.scalb(1.0, -24),
        )
        val beta = listOf(
            BETA_0.readFrom(w) * Math.scalb(1.0, 11),
            BETA_1.readFrom(w) * Math.scalb(1.0, 14),
            BETA_2.readFrom(w) * Math.scalb(1.0, 16),
            BETA_3.readFrom(w) * Math.scalb(1.0, 16),
        )
        // A0 is split: 24 MSBs fill word 7, 8 LSBs start word 8. Put together in an Int, its
        // 32 bits are already two's complement.
        val a0Raw = (A0_MSB.readFrom(w) shl A0_LSB.length) or A0_LSB.readFrom(w)
        val utc = GpsUtcParams(
            a0 = a0Raw * Math.scalb(1.0, -30),
            a1 = A1.readFrom(w) * Math.scalb(1.0, -50),
            tot = TOT.readFrom(w) shl TOT_SCALE_BITS,
            wnt = WNT.readFrom(w),
            deltaTls = DELTA_T_LS.readFrom(w),
            wnLsf = WN_LSF.readFrom(w),
            dn = DN.readFrom(w),
            deltaTlsf = DELTA_T_LSF.readFrom(w),
        )
        return copy(ionosphere = Klobuchar(alpha, beta), utc = utc)
    }

    /*
     * Field positions and scale factors from IS-GPS-200, Figure 20-1 and Tables 20-I, 20-IX
     * and 20-X. Scale factors are powers of two, written as Math.scalb(1.0, n) = 2^n.
     */
    companion object {
        const val PAGE_18_SV_ID = 56

        private val SUBFRAME_IDS = 1..5
        private const val CLOCK_SUBFRAME = 1
        private const val ALMANAC_SUBFRAME_4 = 4
        private const val ALMANAC_SUBFRAME_5 = 5

        /** Subframe 5 pages 1–24 carry the almanac of SVs 1–24, subframe 4 those of 25–32. */
        private val SUBFRAME_5_ALMANAC_SVIDS = 1..24
        private val SUBFRAME_4_ALMANAC_SVIDS = 25..32

        /** Word 2 (HOW) */
        private val SUBFRAME_ID = NavField(word = 2, first = 20, length = 3)

        /** Subframe 1 */
        private val WEEK = NavField(word = 3, first = 1, length = 10)
        private val URA_INDEX = NavField(word = 3, first = 13, length = 4)
        private val SV_HEALTH = NavField(word = 3, first = 17, length = 6)

        /** Subframes 4 and 5: the data ID's SV/page ID */
        private val PAGE_SV_ID = NavField(word = 3, first = 3, length = 6)

        /** Subframe 4 page 18: ionosphere */
        private val ALPHA_0 = NavField(word = 3, first = 9, length = 8, signed = true)
        private val ALPHA_1 = NavField(word = 3, first = 17, length = 8, signed = true)
        private val ALPHA_2 = NavField(word = 4, first = 1, length = 8, signed = true)
        private val ALPHA_3 = NavField(word = 4, first = 9, length = 8, signed = true)
        private val BETA_0 = NavField(word = 4, first = 17, length = 8, signed = true)
        private val BETA_1 = NavField(word = 5, first = 1, length = 8, signed = true)
        private val BETA_2 = NavField(word = 5, first = 9, length = 8, signed = true)
        private val BETA_3 = NavField(word = 5, first = 17, length = 8, signed = true)

        /** Subframe 4 page 18: UTC */
        private val A1 = NavField(word = 6, first = 1, length = 24, signed = true)
        private val A0_MSB = NavField(word = 7, first = 1, length = 24)
        private val A0_LSB = NavField(word = 8, first = 1, length = 8)
        private val TOT = NavField(word = 8, first = 9, length = 8)
        private val WNT = NavField(word = 8, first = 17, length = 8)
        private val DELTA_T_LS = NavField(word = 9, first = 1, length = 8, signed = true)
        private val WN_LSF = NavField(word = 9, first = 9, length = 8)
        private val DN = NavField(word = 9, first = 17, length = 8)
        private val DELTA_T_LSF = NavField(word = 10, first = 1, length = 8, signed = true)

        /** t_ot is broadcast in units of 2^12 s. */
        private const val TOT_SCALE_BITS = 12
    }
}
