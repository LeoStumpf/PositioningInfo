// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsNavMessageTest {

    // --- A test-side encoder, written independently of the decoder, straight from IS-GPS-200. ---

    /** Table 20-XIV, one row per parity bit D25..D30: the previous-word bit and the data bits. */
    private val parityTable = listOf(
        29 to listOf(1, 2, 3, 5, 6, 10, 11, 12, 13, 14, 17, 18, 20, 23),
        30 to listOf(2, 3, 4, 6, 7, 11, 12, 13, 14, 15, 18, 19, 21, 24),
        29 to listOf(1, 3, 4, 5, 7, 8, 12, 13, 14, 15, 16, 19, 20, 22),
        30 to listOf(2, 4, 5, 6, 8, 9, 13, 14, 15, 16, 17, 20, 21, 23),
        30 to listOf(1, 3, 5, 6, 7, 9, 10, 14, 15, 16, 17, 18, 21, 22, 24),
        29 to listOf(3, 5, 6, 8, 9, 10, 11, 13, 15, 19, 22, 23, 24),
    )

    private fun bit(d: Int, n: Int) = (d shr (24 - n)) and 1

    /** 30 transmitted bits for 24 source bits, given the previous word's D29* and D30*. */
    private fun encodeWord(d: Int, d29: Int, d30: Int): Int {
        var parity = 0
        for ((prev, bits) in parityTable) {
            var p = if (prev == 29) d29 else d30
            for (b in bits) p = p xor bit(d, b)
            parity = (parity shl 1) or p
        }
        val onAir = if (d30 == 1) d.inv() and 0xFFFFFF else d
        return (onAir shl 6) or parity
    }

    /**
     * Builds the 40 bytes Android would deliver. Words 2 and 10 get their last two data bits
     * solved so the word ends in D29 = D30 = 0, as the satellite does. [invert] flips every
     * bit, as a receiver locked 180° out of phase sees it.
     */
    private fun encode(data: IntArray, invert: Boolean = false): ByteArray {
        require(data.size == 10)
        var d29 = 0
        var d30 = 0
        val out = ByteArray(40)
        for (i in 0 until 10) {
            var w = encodeWord(data[i], d29, d30)
            if (i == 1 || i == 9) {
                w = (0..3).map { t -> encodeWord((data[i] and 3.inv()) or t, d29, d30) }
                    .first { it and 3 == 0 }
            }
            d29 = (w shr 1) and 1
            d30 = w and 1
            val sent = if (invert) w.inv() and 0x3FFFFFFF else w
            for (b in 0..3) out[4 * i + b] = (sent shr (24 - 8 * b)).toByte()
        }
        return out
    }

    /** Sets [length] bits starting at 1-based [first] (bit 1 = MSB of 24). */
    private fun put(word: Int, first: Int, length: Int, value: Int): Int {
        val shift = 24 - first - length + 1
        val mask = ((1 shl length) - 1) shl shift
        return (word and mask.inv()) or ((value shl shift) and mask)
    }

    private fun tlm() = put(put(0, 1, 8, 0x8B), 9, 14, 0x1234)
    private fun how(subframe: Int, tow: Int = 100_000) = put(put(0, 1, 17, tow), 20, 3, subframe)

    /** Filler that varies from word to word so plenty of D30 bits come out as 1. */
    private fun filler(i: Int) = (0x5A3C96 * (i + 7)) and 0xFFFFFF

    private fun subframe(id: Int, fill: (IntArray) -> Unit): IntArray {
        val w = IntArray(10) { filler(it) }
        w[0] = tlm()
        w[1] = how(id)
        fill(w)
        return w
    }

    private fun subframe1(wn: Int, ura: Int, health: Int) = subframe(1) {
        it[2] = put(put(put(it[2], 1, 10, wn), 13, 4, ura), 17, 6, health)
    }

    private fun almanacPage(subframe: Int, svId: Int) = subframe(subframe) {
        it[2] = put(put(it[2], 1, 2, 1), 3, 6, svId)
    }

    private fun page18(deltaTls: Int, deltaTlsf: Int) = subframe(4) {
        it[2] = put(put(put(put(0, 1, 2, 1), 3, 6, 56), 9, 8, 12), 17, 8, 1)       // α0, α1
        it[3] = put(put(put(0, 1, 8, -1), 9, 8, -1), 17, 8, 44)                    // α2, α3, β0
        it[4] = put(put(put(0, 1, 8, -7), 9, 8, -3), 17, 8, 6)                     // β1, β2, β3
        it[5] = 5 and 0xFFFFFF                                                     // A1
        val a0 = -3
        it[6] = (a0 shr 8) and 0xFFFFFF                                            // A0 MSBs
        it[7] = put(put(put(0, 1, 8, a0 and 0xFF), 9, 8, 144), 17, 8, 137)         // A0 LSBs, tot, WNt
        it[8] = put(put(put(0, 1, 8, deltaTls), 9, 8, 137), 17, 8, 7)              // ΔtLS, WNLSF, DN
        it[9] = put(0, 1, 8, deltaTlsf)                                            // ΔtLSF
    }

    // --- Tests ---

    @Test
    fun `words round-trip through parity and D30 inversion`() {
        val data = subframe1(wn = 345, ura = 2, health = 0)
        val bytes = encode(data)
        // Some words really are inverted on air, or this test proves nothing.
        val inverted = (1 until 10).count { i ->
            val onAir = ((bytes[4 * i].toInt() and 0x3F) shl 18) or
                ((bytes[4 * i + 1].toInt() and 0xFF) shl 10) or
                ((bytes[4 * i + 2].toInt() and 0xFF) shl 2) or
                ((bytes[4 * i + 3].toInt() and 0xFF) shr 6)
            onAir != data[i]
        }
        assertTrue(inverted > 0)

        val words = GpsNavDecoder.words(bytes)
        assertNotNull(words)
        // Words 2 and 10 had their last two bits solved by the encoder.
        val expected = data.copyOf().also {
            it[1] = words!![1] and 3 or (it[1] and 3.inv())
            it[9] = words[9] and 3 or (it[9] and 3.inv())
        }
        assertArrayEquals(expected, words)
        assertEquals(0, GpsNavDecoder.parityFailures(bytes))
    }

    @Test
    fun `a subframe received with inverted polarity decodes the same`() {
        val data = subframe1(wn = 345, ura = 2, health = 0)
        val upright = GpsNavDecoder.words(encode(data))
        val flipped = encode(data, invert = true)
        assertEquals(0x74, (flipped[0].toInt() and 0x3F) shl 2 or ((flipped[1].toInt() and 0xFF) shr 6))
        assertArrayEquals(upright, GpsNavDecoder.words(flipped))
    }

    @Test
    fun `a single bit error fails parity and rejects the subframe`() {
        val bytes = encode(subframe1(wn = 345, ura = 2, health = 0))
        bytes[4 * 3 + 2] = (bytes[4 * 3 + 2].toInt() xor 0x10).toByte()
        assertNull(GpsNavDecoder.words(bytes))
        assertEquals(1, GpsNavDecoder.parityFailures(bytes))

        val state = GpsNavState().onSubframe(5, bytes)
        assertEquals(1, state.subframesRejected)
        assertEquals(0, state.subframesDecoded)
    }

    @Test
    fun `a flipped D30 cascades into the next word`() {
        // D30 selects the next word's inversion, so a hit there breaks two words.
        val bytes = encode(subframe1(wn = 345, ura = 2, health = 0))
        bytes[4 * 4 + 3] = (bytes[4 * 4 + 3].toInt() xor 0x01).toByte()
        assertEquals(2, GpsNavDecoder.parityFailures(bytes))
    }

    @Test
    fun `a missing preamble or short data is rejected`() {
        val data = subframe1(wn = 345, ura = 2, health = 0)
        data[0] = put(data[0], 1, 8, 0x8A)
        assertNull(GpsNavDecoder.words(encode(data)))
        assertNull(GpsNavDecoder.parityFailures(encode(data)))
        assertNull(GpsNavDecoder.words(ByteArray(39)))
    }

    @Test
    fun `subframe 1 gives week number, URA and health per satellite`() {
        val state = GpsNavState()
            .onSubframe(12, encode(subframe1(wn = 345, ura = 2, health = 0)))
            .onSubframe(17, encode(subframe1(wn = 345, ura = 15, health = 0b111111), invert = true))
        assertEquals(345, state.weekNumber)
        assertEquals(mapOf(12 to 0, 17 to 63), state.health)
        assertEquals(mapOf(12 to 2, 17 to 15), state.ura)
        assertEquals(2, state.subframesDecoded)
    }

    @Test
    fun `page 18 gives the ionosphere model and UTC parameters`() {
        val state = GpsNavState().onSubframe(3, encode(page18(deltaTls = 18, deltaTlsf = 18)))
        val iono = state.ionosphere!!
        val eps = 1e-20
        assertEquals(12 * Math.scalb(1.0, -30), iono.alpha[0], eps)
        assertEquals(1.1176e-8, iono.alpha[0], 1e-12)
        assertEquals(Math.scalb(1.0, -27), iono.alpha[1], eps)
        assertEquals(-Math.scalb(1.0, -24), iono.alpha[2], eps)
        assertEquals(-Math.scalb(1.0, -24), iono.alpha[3], eps)
        assertEquals(listOf(90112.0, -114688.0, -196608.0, 393216.0), iono.beta)

        val utc = state.utc!!
        assertEquals(-3 * Math.scalb(1.0, -30), utc.a0, eps)
        assertEquals(5 * Math.scalb(1.0, -50), utc.a1, 1e-30)
        assertEquals(589_824, utc.tot)
        assertEquals(137, utc.wnt)
        assertEquals(18, utc.deltaTls)
        assertEquals(137, utc.wnLsf)
        assertEquals(7, utc.dn)
        assertEquals(18, utc.deltaTlsf)
        assertFalse(utc.leapSecondPending)
        assertTrue(state.almanacSvids.isEmpty())
    }

    @Test
    fun `a scheduled leap second shows as pending`() {
        val state = GpsNavState().onSubframe(3, encode(page18(deltaTls = 18, deltaTlsf = 19), invert = true))
        assertEquals(19, state.utc!!.deltaTlsf)
        assertTrue(state.utc!!.leapSecondPending)
    }

    @Test
    fun `a large A0 keeps its sign across the word boundary`() {
        val data = page18(18, 18)
        val a0 = -123_456_789
        data[6] = (a0 shr 8) and 0xFFFFFF
        data[7] = put(data[7], 1, 8, a0 and 0xFF)
        val utc = GpsNavState().onSubframe(3, encode(data)).utc!!
        assertEquals(a0 * Math.scalb(1.0, -30), utc.a0, 1e-18)
    }

    @Test
    fun `almanac pages are collected from subframes 4 and 5`() {
        val state = listOf(
            almanacPage(5, 1), almanacPage(5, 24), almanacPage(5, 7),
            almanacPage(4, 25), almanacPage(4, 32),
            // Subframe 5 page 25 (SV ID 51) is the health page, subframe 4 SV ID 57 a
            // reserved page, and SV IDs 1..24 in subframe 4 are not almanac pages.
            almanacPage(5, 51), almanacPage(4, 57), almanacPage(4, 3),
        ).fold(GpsNavState()) { acc, sf -> acc.onSubframe(9, encode(sf)) }
        assertEquals(setOf(1, 7, 24, 25, 32), state.almanacSvids)
        assertEquals(8, state.subframesDecoded)
        assertNull(state.utc)
    }

    @Test
    fun `an invalid subframe id is rejected`() {
        val data = subframe(0) {}
        val state = GpsNavState().onSubframe(1, encode(data))
        assertEquals(1, state.subframesRejected)
    }
}
