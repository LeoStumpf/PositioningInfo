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

    const val PREAMBLE = 0x8B
    const val WORDS = 10
    const val BYTES = WORDS * 4

    /** IS-GPS-200 Table 20-XIV: data bits (1-based, d1 = MSB) feeding each parity bit D25..D30. */
    private val PARITY_BITS = listOf(
        intArrayOf(1, 2, 3, 5, 6, 10, 11, 12, 13, 14, 17, 18, 20, 23),
        intArrayOf(2, 3, 4, 6, 7, 11, 12, 13, 14, 15, 18, 19, 21, 24),
        intArrayOf(1, 3, 4, 5, 7, 8, 12, 13, 14, 15, 16, 19, 20, 22),
        intArrayOf(2, 4, 5, 6, 8, 9, 13, 14, 15, 16, 17, 20, 21, 23),
        intArrayOf(1, 3, 5, 6, 7, 9, 10, 14, 15, 16, 17, 18, 21, 22, 24),
        intArrayOf(3, 5, 6, 8, 9, 10, 11, 13, 15, 19, 22, 23, 24),
    )
    private val PARITY_MASKS = PARITY_BITS.map { bits -> bits.fold(0) { m, b -> m or (1 shl (24 - b)) } }

    /** Which of the previous word's bits enters each parity bit: true = D29*, false = D30*. */
    private val USES_D29 = booleanArrayOf(true, false, true, false, false, true)

    /** The six parity bits D25..D30 (D25 = MSB) for 24 source data bits. */
    fun parity(data: Int, d29Star: Int, d30Star: Int): Int {
        var p = 0
        for (i in 0 until 6) {
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
        val raw = IntArray(WORDS) { i ->
            (
                (data[4 * i].toInt() and 0xFF) shl 24 or
                    ((data[4 * i + 1].toInt() and 0xFF) shl 16) or
                    ((data[4 * i + 2].toInt() and 0xFF) shl 8) or
                    (data[4 * i + 3].toInt() and 0xFF)
                ) and 0x3FFFFFFF
        }
        // The previous subframe is not delivered, so its D29*/D30* are unknown. Word 10 is
        // built to end in 00, so word 1 goes out upright; reading the preamble inverted means
        // the receiver locked 180° out of phase and every bit, D29*/D30* included, is flipped.
        var d29: Int
        var d30: Int
        when (raw[0] ushr 22) {
            PREAMBLE -> {
                d29 = 0
                d30 = 0
            }

            PREAMBLE xor 0xFF -> {
                d29 = 1
                d30 = 1
            }

            else -> return null
        }
        var failures = 0
        val words = IntArray(WORDS)
        for (i in 0 until WORDS) {
            val w = raw[i]
            val d = (w ushr 6).let { if (d30 == 1) it xor 0xFFFFFF else it }
            if (parity(d, d29, d30) != (w and 0x3F)) failures++
            words[i] = d
            d29 = (w ushr 1) and 1
            d30 = w and 1
        }
        return words to failures
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
        val subframeId = bits(w[1], 20, 3)
        if (subframeId !in 1..5) return copy(subframesRejected = subframesRejected + 1)
        val decoded = copy(subframesDecoded = subframesDecoded + 1)
        return when (subframeId) {
            1 -> decoded.copy(
                weekNumber = bits(w[2], 1, 10),
                ura = ura + (svid to bits(w[2], 13, 4)),
                health = health + (svid to bits(w[2], 17, 6)),
            )

            4, 5 -> {
                val pageSvId = bits(w[2], 3, 6)
                when {
                    (subframeId == 5 && pageSvId in 1..24) ||
                        (subframeId == 4 && pageSvId in 25..32) ->
                        decoded.copy(almanacSvids = almanacSvids + pageSvId)

                    subframeId == 4 && pageSvId == PAGE_18_SV_ID -> decoded.withPage18(w)

                    else -> decoded
                }
            }

            else -> decoded
        }
    }

    /** IS-GPS-200 Figure 20-1, subframe 4 page 18. */
    private fun withPage18(w: IntArray): GpsNavState {
        val alpha = listOf(
            signed(bits(w[2], 9, 8), 8) * POW2_M30,
            signed(bits(w[2], 17, 8), 8) * POW2_M27,
            signed(bits(w[3], 1, 8), 8) * POW2_M24,
            signed(bits(w[3], 9, 8), 8) * POW2_M24,
        )
        val beta = listOf(
            signed(bits(w[3], 17, 8), 8) * 2048.0,
            signed(bits(w[4], 1, 8), 8) * 16384.0,
            signed(bits(w[4], 9, 8), 8) * 65536.0,
            signed(bits(w[4], 17, 8), 8) * 65536.0,
        )
        // A0: 24 MSBs in word 7, 8 LSBs at the start of word 8. As an Int the 32 bits are
        // already two's complement.
        val a0Raw = (w[6] shl 8) or bits(w[7], 1, 8)
        val utc = GpsUtcParams(
            a0 = a0Raw * POW2_M30,
            a1 = signed(w[5], 24) * POW2_M50,
            tot = bits(w[7], 9, 8) shl 12,
            wnt = bits(w[7], 17, 8),
            deltaTls = signed(bits(w[8], 1, 8), 8),
            wnLsf = bits(w[8], 9, 8),
            dn = bits(w[8], 17, 8),
            deltaTlsf = signed(bits(w[9], 1, 8), 8),
        )
        return copy(ionosphere = Klobuchar(alpha, beta), utc = utc)
    }

    companion object {
        const val PAGE_18_SV_ID = 56

        private const val POW2_M24 = 1.0 / (1 shl 24)
        private const val POW2_M27 = 1.0 / (1 shl 27)
        private const val POW2_M30 = 1.0 / (1 shl 30)
        private val POW2_M50 = Math.scalb(1.0, -50)

        /** [length] bits starting at 1-based [first] of a 24-bit word (bit 1 = MSB). */
        private fun bits(word: Int, first: Int, length: Int): Int =
            (word ushr (24 - first - length + 1)) and ((1 shl length) - 1)

        private fun signed(value: Int, width: Int): Int = (value shl (32 - width)) shr (32 - width)
    }
}
