// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

import de.leostumpf.gpstools.data.model.SpeedFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixTimingTest {

    private fun fix(atMs: Long, cached: Boolean = false, utcMs: Long? = null) = SpeedFix(
        speedMps = 0f,
        speedAccuracyMps = null,
        horizontalAccuracyM = 5f,
        elapsedRealtimeMs = atMs,
        isCached = cached,
        utcTimeMs = utcMs,
    )

    @Test
    fun `first live fix is measured from its own timestamp`() {
        val timer = FirstFixTimer(startedAtMs = 10_000L).onFix(fix(atMs = 14_200L))
        assertEquals(4_200L, timer.firstFixAfterMs)
    }

    @Test
    fun `the cached startup fix does not count as a first fix`() {
        // It was produced by an earlier session, not by this one.
        val timer = FirstFixTimer(startedAtMs = 10_000L).onFix(fix(atMs = 10_100L, cached = true))
        assertNull(timer.firstFixAfterMs)
        assertEquals(500L, timer.searchingForMs(nowMs = 10_500L))
    }

    @Test
    fun `later fixes do not change the time to first fix`() {
        val timer = FirstFixTimer(startedAtMs = 0L)
            .onFix(fix(atMs = 3_000L))
            .onFix(fix(atMs = 4_000L))
        assertEquals(3_000L, timer.firstFixAfterMs)
    }

    @Test
    fun `searching time grows until the fix and then disappears`() {
        val timer = FirstFixTimer(startedAtMs = 1_000L)
        assertEquals(0L, timer.searchingForMs(nowMs = 1_000L))
        assertEquals(29_000L, timer.searchingForMs(nowMs = 30_000L))
        assertNull(timer.onFix(fix(atMs = 30_000L)).searchingForMs(nowMs = 31_000L))
    }

    @Test
    fun `time with location switched off is not charged to the receiver`() {
        val timer = FirstFixTimer(startedAtMs = 0L)
            .holdWhileDisabled(nowMs = 60_000L, gpsEnabled = false)
            .holdWhileDisabled(nowMs = 61_000L, gpsEnabled = true)
            .onFix(fix(atMs = 65_000L))
        assertEquals(5_000L, timer.firstFixAfterMs)
    }

    @Test
    fun `switching location off after the fix keeps the measurement`() {
        val timer = FirstFixTimer(startedAtMs = 0L)
            .onFix(fix(atMs = 2_000L))
            .holdWhileDisabled(nowMs = 90_000L, gpsEnabled = false)
        assertEquals(2_000L, timer.firstFixAfterMs)
    }

    @Test
    fun `a fix stamped just before the session started counts as immediate`() {
        val timer = FirstFixTimer(startedAtMs = 5_000L).onFix(fix(atMs = 4_900L))
        assertEquals(0L, timer.firstFixAfterMs)
    }

    @Test
    fun `clock offset cancels the delivery delay`() {
        // The fix was computed 800 ms before it is evaluated; the phone's clock matches GNSS.
        val offset = ClockOffset.of(
            fix(atMs = 9_200L, utcMs = 1_700_000_000_000L),
            nowWallMs = 1_700_000_000_800L,
            nowElapsedMs = 10_000L,
        )
        assertEquals(0L, offset)
    }

    @Test
    fun `a phone clock ahead of GNSS gives a positive offset`() {
        val offset = ClockOffset.of(
            fix(atMs = 10_000L, utcMs = 1_700_000_000_000L),
            nowWallMs = 1_700_000_003_000L,
            nowElapsedMs = 10_000L,
        )
        assertEquals(3_000L, offset)
    }

    @Test
    fun `a phone clock behind GNSS gives a negative offset`() {
        val offset = ClockOffset.of(
            fix(atMs = 10_000L, utcMs = 1_700_000_000_000L),
            nowWallMs = 1_699_999_998_000L,
            nowElapsedMs = 10_000L,
        )
        assertEquals(-2_000L, offset)
    }

    @Test
    fun `no offset from a cached fix or one without UTC time`() {
        assertNull(ClockOffset.of(fix(atMs = 0L, cached = true, utcMs = 1L), 1_000L, 0L))
        assertNull(ClockOffset.of(fix(atMs = 0L), 1_000L, 0L))
    }
}
