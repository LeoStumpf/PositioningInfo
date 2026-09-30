// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import io.github.leostumpf.positioninginfo.domain.Band
import io.github.leostumpf.positioninginfo.domain.SpoofingIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpoofingTextTest {

    @Test
    fun `indicators are worded with their figures and their band`() {
        assertEquals(
            "L1/E1/B1: 8 signals are unusually alike in strength (45 dB-Hz ± 0.3). Real satellites at " +
                "different elevations usually differ more; one transmitter could make them alike.",
            SpoofingIndicator.UniformStrength(Band.L1_E1_B1, 8, 45.2, 0.25).describe(),
        )
        assertTrue(
            SpoofingIndicator.PowerWithStrongerSignals(
                Band.L5_E5A_B2A,
                5.0,
            ).describe().startsWith("L5/E5a/B2a: more power"),
        )
        assertTrue(SpoofingIndicator.DriftJump(0.5).describe().contains("0.5 ppm"))
    }
}
