// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.DiagnosisInput
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisTextTest {

    private fun diagnose(
        heard: Int = 10,
        strong: Int = 8,
        used: Int = 0,
        searchingMs: Long? = 5_000L,
        firstFixMs: Long? = null,
        data: Boolean? = true,
        powerSave: PowerSaveLocation = PowerSaveLocation.UNRESTRICTED,
    ) = FixDiagnosis.evaluate(
        DiagnosisInput(
            gpsEnabled = true, isMock = false, powerSave = powerSave, airplaneMode = false,
            dataConnection = data, satellitesHeard = heard, satellitesStrong = strong, usedInFix = used,
            readiness = AlmanacReadiness.HOT, withEphemeris = 6, pdop = null,
            searchingMs = searchingMs, firstFixMs = firstFixMs,
        ),
    )

    @Test
    fun `counts read naturally`() {
        assertEquals("Only 1 satellite heard", diagnose(heard = 1, strong = 1).title())
        assertEquals("Only 3 satellites heard", diagnose(heard = 3, strong = 3).title())
        assertTrue(diagnose(heard = 9, strong = 2).detail().startsWith("9 satellites are heard, but only 2"))
    }

    @Test
    fun `a fix says how long the first one took`() {
        val d = diagnose(used = 8, searchingMs = null, firstFixMs = 3_000L)
        assertEquals("All checks pass", d.title())
        assertEquals("Fixed on 8 satellites. This session's first fix took 3.0 s.", d.detail())
    }

    @Test
    fun `checks are labelled in chain order, the last by whether there is a fix yet`() {
        val searching = diagnose()
        assertEquals(
            listOf(
                "Location service", "Position source", "Battery saver", "Data for assistance",
                "Satellites heard", "Usable signals", "Orbital data", "Geometry", "Searching for",
            ),
            searching.checks.map { it.label(searching.input) },
        )
        val fixed = diagnose(used = 8, searchingMs = null, firstFixMs = 3_000L)
        assertEquals("Time to first fix", fixed.checks.last().label(fixed.input))
        assertEquals("3.0 s", fixed.checks.last().value(fixed.input))
    }

    @Test
    fun `hints only where there is something to know`() {
        val d = diagnose(data = false, powerSave = PowerSaveLocation.FOREGROUND_ONLY)
        val hints = d.checks.associate { it.kind to it.hint(d.input, d.expectedMs) }
        assertTrue(hints.getValue(io.github.leostumpf.positioninginfo.domain.CheckKind.DATA)!!.contains("A-GNSS"))
        assertTrue(
            hints.getValue(
                io.github.leostumpf.positioninginfo.domain.CheckKind.BATTERY_SAVER,
            )!!.contains("Background mode"),
        )
        assertNull(hints.getValue(io.github.leostumpf.positioninginfo.domain.CheckKind.LOCATION))
    }
}
