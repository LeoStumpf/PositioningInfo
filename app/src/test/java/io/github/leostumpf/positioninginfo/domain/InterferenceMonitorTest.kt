// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class InterferenceMonitorTest {

    private val l1 = 1575.42e6

    /** Eight L1 signals spread naturally around [meanCn0], plus one L1 AGC reading. */
    private fun epoch(
        atMs: Long,
        agcDb: Double = 50.0,
        meanCn0: Double = 38.0,
        signals: List<SignalReading>? = null,
        driftNsPerS: Double? = null,
        discontinuity: Int? = null,
        leapSecond: Int? = null,
    ) = RawEpoch(
        atMs = atMs,
        agc = listOf(AgcReading(Constellation.GPS, l1, agcDb)),
        signals = signals ?: listOf(-9.0, -6.0, -3.0, -1.0, 1.0, 3.0, 6.0, 9.0).mapIndexed { i, d ->
            SignalReading(Constellation.GPS, i + 1, l1, meanCn0 + d, multipath = false)
        },
        clockDriftNsPerS = driftNsPerS,
        hardwareClockDiscontinuityCount = discontinuity,
        leapSecond = leapSecond,
    )

    /** One epoch per second for the baseline minute and a bit beyond. */
    private fun stable(seconds: Int = 70): InterferenceMonitor =
        (0 until seconds).fold(InterferenceMonitor()) { m, s -> m.onEpoch(epoch(s * 1000L)) }

    @Test
    fun `stable epochs raise no suspicion`() {
        val a = stable().assessment
        assertFalse(a.jammingSuspected)
        assertTrue(a.spoofingIndicators.isEmpty())
        val l1Status = a.bands.single { it.band == Band.L1_E1_B1 }
        assertEquals(50.0, l1Status.agcBaselineDb!!, 1e-9)
        assertEquals(38.0, l1Status.cn0BaselineDbHz!!, 1e-9)
        assertEquals(0.0, l1Status.agcDropDb!!, 1e-9)
        assertEquals(8, l1Status.signals)
        assertEquals(70, a.epochs)
    }

    @Test
    fun `a jammer is suspected after three epochs but not after two`() {
        var m = stable()
        m = m.onEpoch(epoch(70_000, agcDb = 40.0, meanCn0 = 28.0))
        m = m.onEpoch(epoch(71_000, agcDb = 40.0, meanCn0 = 28.0))
        assertFalse(m.assessment.jammingSuspected)
        assertEquals(10.0, m.assessment.bands.single { it.band == Band.L1_E1_B1 }.agcDropDb!!, 1e-9)
        m = m.onEpoch(epoch(72_000, agcDb = 40.0, meanCn0 = 28.0))
        assertTrue(m.assessment.jammingSuspected)
        // It clears as soon as the band recovers.
        m = m.onEpoch(epoch(73_000))
        assertFalse(m.assessment.jammingSuspected)
    }

    @Test
    fun `weak signals without an AGC drop are not jamming`() {
        // Walking indoors: C/N0 falls, the band's power does not rise.
        var m = stable()
        repeat(5) { m = m.onEpoch(epoch(70_000L + it * 1000, meanCn0 = 25.0)) }
        assertFalse(m.assessment.jammingSuspected)
    }

    @Test
    fun `losing most signals with an AGC drop is jamming`() {
        val few = (1..3).map { SignalReading(Constellation.GPS, it, l1, 36.0, false) }
        var m = stable()
        repeat(3) { m = m.onEpoch(epoch(70_000L + it * 1000, agcDb = 43.0, signals = few)) }
        assertTrue(m.assessment.jammingSuspected)
    }

    @Test
    fun `nothing is judged during the baseline minute`() {
        var m = InterferenceMonitor()
        repeat(5) { m = m.onEpoch(epoch(it * 1000L, agcDb = 30.0, meanCn0 = 20.0)) }
        assertFalse(m.assessment.jammingSuspected)
        assertNull(m.assessment.bands.single().agcBaselineDb)
    }

    @Test
    fun `a long gap starts a new baseline`() {
        var m = stable()
        m = m.onEpoch(epoch(200_000, agcDb = 40.0, meanCn0 = 28.0))
        val status = m.assessment.bands.single()
        assertNull(status.agcBaselineDb)
        assertEquals(40.0, status.agcDb!!, 1e-9)
    }

    @Test
    fun `uniformly strong signals are flagged as a spoofing indicator`() {
        val uniform = (1..8).map { SignalReading(Constellation.GPS, it, l1, 45.0 + (it % 2) * 0.5, false) }
        val m = stable().onEpoch(epoch(70_000, signals = uniform))
        val indicators = m.assessment.spoofingIndicators
        assertEquals(1, indicators.size)
        assertTrue(indicators[0].contains("alike"))
    }

    @Test
    fun `uniform but weak or few signals are not flagged`() {
        val weak = (1..8).map { SignalReading(Constellation.GPS, it, l1, 30.0, false) }
        val few = (1..5).map { SignalReading(Constellation.GPS, it, l1, 45.0, false) }
        assertTrue(stable().onEpoch(epoch(70_000, signals = weak)).assessment.spoofingIndicators.isEmpty())
        assertTrue(stable().onEpoch(epoch(70_000, signals = few)).assessment.spoofingIndicators.isEmpty())
    }

    @Test
    fun `more power in the band with stronger signals is flagged`() {
        val m = stable().onEpoch(epoch(70_000, agcDb = 45.0, meanCn0 = 44.0))
        assertFalse(m.assessment.jammingSuspected)
        assertTrue(m.assessment.spoofingIndicators.any { it.contains("stronger than the sky") })
    }

    @Test
    fun `clock drift converts to ppm and its spread is measured`() {
        var m = InterferenceMonitor()
        val drifts = listOf(100.0, 200.0, 300.0, 400.0) // ns/s
        drifts.forEachIndexed { i, d -> m = m.onEpoch(epoch(i * 1000L, driftNsPerS = d)) }
        val a = m.assessment
        assertEquals(0.4, a.clockDriftPpm!!, 1e-12)
        // Population standard deviation of 0.1, 0.2, 0.3, 0.4 ppm.
        assertEquals(sqrt(0.0125), a.clockDriftStdDevPpm!!, 1e-12)
        assertTrue(a.spoofingIndicators.isEmpty())
    }

    @Test
    fun `drift older than a minute leaves the spread`() {
        var m = InterferenceMonitor().onEpoch(epoch(0, driftNsPerS = 400.0))
        for (s in 1..61) m = m.onEpoch(epoch(s * 1000L, driftNsPerS = 100.0))
        assertEquals(0.0, m.assessment.clockDriftStdDevPpm!!, 1e-12)
    }

    @Test
    fun `a drift jump is a spoofing indicator`() {
        val m = InterferenceMonitor()
            .onEpoch(epoch(0, driftNsPerS = 100.0))
            .onEpoch(epoch(1000, driftNsPerS = 700.0))
        assertTrue(m.assessment.spoofingIndicators.any { it.contains("drift jumped") })
    }

    @Test
    fun `hardware clock discontinuities are counted from the first epoch on`() {
        var m = InterferenceMonitor()
        m = m.onEpoch(epoch(0, discontinuity = 7))
        m = m.onEpoch(epoch(1000, discontinuity = 7))
        assertEquals(0, m.assessment.clockDiscontinuities)
        assertTrue(m.assessment.spoofingIndicators.isEmpty())
        m = m.onEpoch(epoch(2000, discontinuity = 8))
        m = m.onEpoch(epoch(3000, discontinuity = 10))
        assertEquals(3, m.assessment.clockDiscontinuities)
        // Routine on phone chips (duty cycling), so counted but not flagged.
        assertTrue(m.assessment.spoofingIndicators.none { it.contains("discontinuit") })
    }

    @Test
    fun `multipath and leap second come from the receiver`() {
        val signals = listOf(
            SignalReading(Constellation.GPS, 1, l1, 40.0, true),
            SignalReading(Constellation.GPS, 2, l1, 38.0, false),
            SignalReading(Constellation.GALILEO, 3, null, 35.0, null),
        )
        val m = InterferenceMonitor()
            .onEpoch(epoch(0, leapSecond = 18))
            .onEpoch(epoch(1000, signals = signals))
        val a = m.assessment
        assertEquals(1, a.multipathSignals)
        assertEquals(3, a.totalSignals)
        assertEquals(18, a.leapSecond)
        assertEquals(2, a.epochs)
    }

    @Test
    fun `bands are classified by carrier frequency`() {
        assertEquals(Band.L1_E1_B1, Band.of(1575.42e6))
        assertEquals(Band.L5_E5A_B2A, Band.of(1176.45e6))
        assertEquals(Band.L1_E1_B1, Band.of(1602.0e6))
        assertEquals(Band.L1_E1_B1, Band.of(1561.098e6))
        assertEquals(Band.OTHER, Band.of(1227.60e6))
    }
}
