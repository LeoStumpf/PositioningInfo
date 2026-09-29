// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import org.junit.Assert.assertEquals
import org.junit.Test

class TimingFormattingTest {

    @Test
    fun `durations under a minute keep tenths, longer ones use minutes`() {
        assertEquals("4.2 s", formatDuration(4_249L))
        assertEquals("59.9 s", formatDuration(59_900L))
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("12:05", formatDuration(725_000L))
    }

    @Test
    fun `first fix shows the time, the search, or nothing`() {
        assertEquals(
            TimingText("4.2 s", TimingTone.GOOD),
            TimingUiState(firstFixMs = 4_200L).firstFixText(gpsEnabled = true),
        )
        assertEquals(
            TimingText("searching… 37.0 s", TimingTone.PENDING),
            TimingUiState(searchingForMs = 37_000L).firstFixText(gpsEnabled = true),
        )
        assertEquals(
            TimingText("--", TimingTone.NONE),
            TimingUiState(searchingForMs = 37_000L).firstFixText(gpsEnabled = false),
        )
    }

    @Test
    fun `small clock offsets read as in sync rather than a false-precision figure`() {
        assertEquals(TimingTone.GOOD, TimingUiState(clockOffsetMs = 400L).clockText().tone)
        assertEquals(TimingTone.GOOD, TimingUiState(clockOffsetMs = -999L).clockText().tone)
    }

    @Test
    fun `larger clock offsets say which way`() {
        assertEquals("3.20 s ahead of GNSS time", TimingUiState(clockOffsetMs = 3_200L).clockText().text)
        assertEquals("2.00 s behind GNSS time", TimingUiState(clockOffsetMs = -2_000L).clockText().text)
        assertEquals("--", TimingUiState().clockText().text)
    }

    @Test
    fun `the system's GNSS time stands in until this session has a fix`() {
        assertEquals("3.20 s ahead of GNSS time", TimingUiState(systemGnssOffsetMs = 3_200L).clockText().text)
        assertEquals(
            "in sync (within 1 s)",
            TimingUiState(clockOffsetMs = 100L, systemGnssOffsetMs = 3_200L).clockText().text,
        )
    }

    @Test
    fun `network time is compared with a tighter tolerance`() {
        assertEquals("in sync (within 500 ms)", TimingUiState(networkOffsetMs = -120L).networkClockText().text)
        assertEquals(TimingTone.WARN, TimingUiState(networkOffsetMs = 800L).networkClockText().tone)
        assertEquals("not known", TimingUiState().networkClockText().text)
    }

    @Test
    fun `network against GNSS needs both clocks`() {
        assertEquals(null, TimingUiState(networkOffsetMs = 100L).networkVsGnssText())
        // Phone 3 s ahead of GNSS and 1 s ahead of the network: the network is 2 s ahead of GNSS.
        assertEquals(
            "2.00 s ahead of GNSS time",
            TimingUiState(clockOffsetMs = 3_000L, networkOffsetMs = 1_000L).networkVsGnssText()?.text,
        )
    }
}
