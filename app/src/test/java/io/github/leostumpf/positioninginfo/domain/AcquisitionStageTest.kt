// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AcquisitionStageTest {

    @Test
    fun `no flags means still searching`() {
        assertEquals(AcquisitionStage.SEARCHING, AcquisitionStage.from(0))
        // MSEC_AMBIGUOUS alone says nothing about progress.
        assertEquals(AcquisitionStage.SEARCHING, AcquisitionStage.from(16))
    }

    @Test
    fun `the highest stage wins`() {
        assertEquals(AcquisitionStage.CODE_LOCK, AcquisitionStage.from(1))
        assertEquals(AcquisitionStage.BIT_SYNC, AcquisitionStage.from(1 or 2))
        assertEquals(AcquisitionStage.FRAME_SYNC, AcquisitionStage.from(1 or 2 or 4))
        assertEquals(AcquisitionStage.TIME_DECODED, AcquisitionStage.from(1 or 2 or 4 or 8))
    }

    @Test
    fun `constellation specific flags count too`() {
        assertEquals(AcquisitionStage.CODE_LOCK, AcquisitionStage.from(1024))       // Galileo E1BC code lock
        assertEquals(AcquisitionStage.FRAME_SYNC, AcquisitionStage.from(64))        // GLONASS string sync
        assertEquals(AcquisitionStage.TIME_DECODED, AcquisitionStage.from(128))     // GLONASS time of day
        assertEquals(AcquisitionStage.TIME_DECODED, AcquisitionStage.from(16384))   // time of week known
    }

    @Test
    fun `an approaching satellite has a positive doppler`() {
        // −500 m/s range rate on L1 is about +2.6 kHz.
        assertEquals(2_627.5, AcquisitionStage.dopplerHz(-500.0, 1_575.42e6), 1.0)
    }
}
