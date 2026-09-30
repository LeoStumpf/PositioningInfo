// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixDiagnosisTest {

    private fun input(
        gpsEnabled: Boolean = true,
        isMock: Boolean = false,
        heard: Int = 10,
        strong: Int = 8,
        used: Int = 0,
        readiness: AlmanacReadiness = AlmanacReadiness.HOT,
        data: Boolean? = true,
        pdop: Double? = null,
        searchingMs: Long? = 5_000L,
        firstFixMs: Long? = null,
    ) = DiagnosisInput(
        gpsEnabled = gpsEnabled, isMock = isMock, powerSave = PowerSaveLocation.UNRESTRICTED,
        airplaneMode = false, dataConnection = data, satellitesHeard = heard, satellitesStrong = strong,
        usedInFix = used, readiness = readiness, withEphemeris = 6, pdop = pdop,
        searchingMs = searchingMs, firstFixMs = firstFixMs,
    )

    @Test
    fun `nothing heard after losing the fix is no signal, not acquiring`() {
        val d = FixDiagnosis.evaluate(input(heard = 0, strong = 0, searchingMs = null, firstFixMs = 4_000L))
        assertEquals("No satellite signals", d.verdict)
        assertEquals(CheckStatus.FAIL, d.verdictStatus)
    }

    @Test
    fun `a lost fix with signals is reacquiring, not learning orbits`() {
        val d = FixDiagnosis.evaluate(
            input(heard = 8, strong = 6, readiness = AlmanacReadiness.COLD, data = false, searchingMs = null, firstFixMs = 4_000L),
        )
        assertEquals("Fix lost, reacquiring", d.verdict)
    }

    @Test
    fun `one satellite is singular`() {
        assertEquals("Only 1 satellite heard", FixDiagnosis.evaluate(input(heard = 1, strong = 1)).verdict)
    }

    @Test
    fun `a cold start with data is expected within minutes, not twelve`() {
        assertEquals(120_000L, FixDiagnosis.expectedMs(AlmanacReadiness.COLD, dataConnection = true))
        assertEquals(720_000L, FixDiagnosis.expectedMs(AlmanacReadiness.COLD, dataConnection = false))
    }

    @Test
    fun `location off comes before everything else`() {
        val d = FixDiagnosis.evaluate(input(gpsEnabled = false, heard = 0))
        assertEquals("Location is switched off", d.verdict)
        assertEquals(CheckStatus.FAIL, d.verdictStatus)
        assertFalse(d.fixed)
    }

    @Test
    fun `a simulated position is called out`() {
        assertEquals("The position is simulated", FixDiagnosis.evaluate(input(isMock = true, used = 8)).verdict)
    }

    @Test
    fun `fixed with good geometry passes`() {
        val d = FixDiagnosis.evaluate(input(used = 8, pdop = 1.6, searchingMs = null, firstFixMs = 3_000L))
        assertTrue(d.fixed)
        assertEquals(CheckStatus.OK, d.verdictStatus)
        assertEquals("All checks pass", d.verdict)
        assertTrue(d.detail.contains("3.0 s"))
    }

    @Test
    fun `fixed with poor geometry warns`() {
        assertEquals(CheckStatus.WARN, FixDiagnosis.evaluate(input(used = 5, pdop = 9.0)).verdictStatus)
    }

    @Test
    fun `nothing heard after the grace period means no sky`() {
        assertEquals("No satellite signals", FixDiagnosis.evaluate(input(heard = 0, strong = 0, searchingMs = 30_000L)).verdict)
        // Right after starting, silence is normal.
        assertEquals("Acquiring", FixDiagnosis.evaluate(input(heard = 0, strong = 0, searchingMs = 2_000L)).verdict)
    }

    @Test
    fun `too few satellites are counted`() {
        assertEquals("Only 3 satellites heard", FixDiagnosis.evaluate(input(heard = 3, strong = 3)).verdict)
    }

    @Test
    fun `many weak signals mean they cannot be decoded`() {
        assertEquals("Signals too weak", FixDiagnosis.evaluate(input(heard = 9, strong = 2)).verdict)
    }

    @Test
    fun `a cold start without data is explained`() {
        val d = FixDiagnosis.evaluate(input(readiness = AlmanacReadiness.COLD, data = false))
        assertEquals("Learning orbits from the satellites", d.verdict)
    }

    @Test
    fun `slow hot start is flagged`() {
        assertEquals("Taking longer than expected", FixDiagnosis.evaluate(input(searchingMs = 40_000L)).verdict)
    }

    @Test
    fun `every check is listed in chain order`() {
        val labels = FixDiagnosis.evaluate(input()).checks.map { it.label }
        assertEquals(
            listOf(
                "Location service", "Position source", "Battery saver", "Data for assistance",
                "Satellites heard", "Usable signals", "Orbital data", "Geometry", "Searching for",
            ),
            labels,
        )
    }

    @Test
    fun `durations read naturally`() {
        assertEquals("8.4 s", formatDuration(8_400L))
        assertEquals("38 s", formatDuration(38_000L))
        assertEquals("1 min 20 s", formatDuration(80_000L))
        assertEquals("12 min", formatDuration(720_000L))
    }
}
