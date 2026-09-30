// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedResolverTest {

    private fun fix(speed: Float? = 20f, accuracy: Float? = 0.5f, atMs: Long = 100_000, cached: Boolean = false) =
        SpeedFix(
            speedMps = speed,
            speedAccuracyMps = accuracy,
            horizontalAccuracyM = 4f,
            elapsedRealtimeMs = atMs,
            isCached = cached,
        )

    @Test
    fun `a recent fix reads through at full confidence`() {
        val reading = SpeedResolver.resolve(fix(atMs = 100_000), nowElapsedMs = 100_500)
        assertEquals(20f, reading.speedMps!!, 0f)
        assertEquals(FixFreshness.FRESH, reading.freshness)
        assertTrue(reading.countsTowardsStats)
    }

    @Test
    fun `a stale fix is still shown but no longer feeds the statistics`() {
        val reading = SpeedResolver.resolve(fix(atMs = 100_000), nowElapsedMs = 104_000)
        assertEquals(20f, reading.speedMps!!, 0f)
        assertEquals(FixFreshness.STALE, reading.freshness)
        assertFalse(reading.countsTowardsStats)
    }

    @Test
    fun `an expired fix is withdrawn rather than left frozen on screen`() {
        val reading = SpeedResolver.resolve(fix(atMs = 100_000), nowElapsedMs = 111_000)
        assertNull(reading.speedMps)
        assertEquals(FixFreshness.EXPIRED, reading.freshness)
        assertFalse(reading.countsTowardsStats)
    }

    @Test
    fun `a fix carrying no velocity never becomes a speed`() {
        val reading = SpeedResolver.resolve(fix(speed = null), nowElapsedMs = 100_100)
        assertNull(reading.speedMps)
        assertEquals(FixFreshness.FRESH, reading.freshness)
    }

    @Test
    fun `having no fix at all reads as expired`() {
        val reading = SpeedResolver.resolve(null, nowElapsedMs = 100_000)
        assertNull(reading.speedMps)
        assertEquals(FixFreshness.EXPIRED, reading.freshness)
    }

    @Test
    fun `receiver noise while stationary reads as zero, not as creeping motion`() {
        val reading = SpeedResolver.resolve(
            fix(speed = 0.7f, accuracy = 0.9f),
            nowElapsedMs = 100_100,
        )
        assertEquals(0f, reading.speedMps!!, 0f)
    }

    @Test
    fun `a fix timestamped in the future is treated as current, not as an error`() {
        val reading = SpeedResolver.resolve(fix(atMs = 100_000), nowElapsedMs = 99_000)
        assertEquals(0L, SpeedResolver.ageMs(fix(atMs = 100_000), 99_000))
        assertEquals(FixFreshness.FRESH, reading.freshness)
    }

    @Test
    fun `withdrawal happens exactly at the expiry boundary`() {
        val at = 100_000L
        val justBefore = SpeedResolver.resolve(fix(atMs = at), at + 9_999)
        val exactly = SpeedResolver.resolve(fix(atMs = at), at + 10_000)

        assertEquals(20f, justBefore.speedMps!!, 0f)
        assertNull(exactly.speedMps)
    }

    @Test
    fun `the cached startup fix is displayed but never counted`() {
        // Reopening the app right after a drive must not carry the previous trip's speed
        // into the new session's maximum.
        val reading = SpeedResolver.resolve(
            fix(speed = 25f, atMs = 100_000, cached = true),
            nowElapsedMs = 100_200,
        )
        assertEquals(25f, reading.speedMps!!, 0f)
        assertEquals(FixFreshness.FRESH, reading.freshness)
        assertFalse(reading.countsTowardsStats)
    }

    @Test
    fun `a live fix in the same conditions is counted`() {
        val reading = SpeedResolver.resolve(
            fix(speed = 25f, atMs = 100_000, cached = false),
            nowElapsedMs = 100_200,
        )
        assertTrue(reading.countsTowardsStats)
    }
}
