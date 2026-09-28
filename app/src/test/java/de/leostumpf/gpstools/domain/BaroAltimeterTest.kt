// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class BaroAltimeterTest {

    @Test
    fun `formula round trips`() {
        val p0 = 1021.3
        for (alt in listOf(-100.0, 0.0, 540.0, 2962.0, 8848.0)) {
            // Pressure at this altitude under p0, then back to altitude and to p0.
            val pressure = p0 * Math.pow(1 - alt / 44_330.0, 5.255)
            assertEquals(alt, BaroAltimeter.altitudeM(pressure, p0), 1e-6)
            assertEquals(p0, BaroAltimeter.seaLevelPressureHpa(pressure, alt), 1e-9)
        }
    }

    @Test
    fun `standard pressure is sea level in the standard atmosphere`() {
        val baro = BaroAltimeter().onPressure(1013.25f, atMs = 0)
        assertEquals(0.0, baro.standardAltitudeM!!, 1e-6)
        assertNull(baro.seaLevelPressureHpa)
        assertNull(baro.calibratedAltitudeM)
        // Textbook value: about 111 m for 1000 hPa.
        assertEquals(110.9, BaroAltimeter.altitudeM(1000.0, 1013.25), 0.1)
    }

    @Test
    fun `pressure is smoothed with a time constant`() {
        val baro = BaroAltimeter()
            .onPressure(1000f, atMs = 0)
            .onPressure(1010f, atMs = 2_000)
        // One time constant: 1 - 1/e of the step.
        assertEquals(1000.0 + 10 * (1 - Math.exp(-1.0)), baro.pressureHpa!!, 1e-4)
    }

    @Test
    fun `calibration converges to the GNSS altitude`() {
        val random = Random(7)
        var baro = BaroAltimeter().onPressure(950f, atMs = 0)
        for (i in 1..120) {
            val t = i * 1_000L
            baro = baro.onPressure(950f, t)
                .onGnssAltitude(540.0 + random.nextDouble(-4.0, 4.0), verticalAccuracyM = 4f, atMs = t)
        }
        assertEquals(120, baro.calibrationSamples)
        assertEquals(540.0, baro.calibratedAltitudeM!!, 1.0)
        assertEquals(BaroAltimeter.seaLevelPressureHpa(950.0, 540.0), baro.seaLevelPressureHpa!!, 0.15)
    }

    @Test
    fun `precise samples outweigh vague ones`() {
        val baro = BaroAltimeter().onPressure(950f, atMs = 0)
            .onGnssAltitude(500.0, verticalAccuracyM = 1f, atMs = 0)
            .onGnssAltitude(530.0, verticalAccuracyM = 10f, atMs = 0)
        // Weights 1 and 0.01: the result stays within a metre of the precise sample.
        assertEquals(500.3, baro.calibratedAltitudeM!!, 0.1)
    }

    @Test
    fun `poor or missing vertical accuracy is ignored`() {
        val baro = BaroAltimeter().onPressure(950f, atMs = 0)
            .onGnssAltitude(540.0, verticalAccuracyM = null, atMs = 0)
            .onGnssAltitude(540.0, verticalAccuracyM = 25f, atMs = 1_000)
        assertEquals(0, baro.calibrationSamples)
        assertNull(baro.seaLevelPressureHpa)
    }

    @Test
    fun `GNSS before any pressure is ignored`() {
        val baro = BaroAltimeter().onGnssAltitude(540.0, verticalAccuracyM = 2f, atMs = 0)
        assertEquals(0, baro.calibrationSamples)
    }

    @Test
    fun `old calibration fades so weather drift is followed`() {
        var baro = BaroAltimeter().onPressure(950f, atMs = 0)
        for (i in 0 until 10) baro = baro.onGnssAltitude(540.0, 3f, atMs = i * 1_000L)
        // An hour later the weather changed: the same pressure now means 560 m.
        for (i in 0 until 10) baro = baro.onGnssAltitude(560.0, 3f, atMs = 3_600_000L + i * 1_000L)
        assertEquals(560.0, baro.calibratedAltitudeM!!, 0.5)
    }

    @Test
    fun `vertical speed of a steady climb`() {
        val p0 = 1013.25
        var baro = BaroAltimeter()
        // Climbing at 1.5 m/s, a pressure reading every 200 ms for 30 s.
        for (i in 0..150) {
            val t = i * 200L
            val alt = 300.0 + 1.5 * t / 1000.0
            val p = p0 * Math.pow(1 - alt / 44_330.0, 5.255)
            baro = baro.onPressure(p.toFloat(), t)
        }
        assertEquals(1.5, baro.verticalSpeedMps!!, 0.05)
    }

    @Test
    fun `vertical speed is zero at rest and null without history`() {
        assertNull(BaroAltimeter().onPressure(990f, 0).verticalSpeedMps)
        var baro = BaroAltimeter()
        for (i in 0..50) baro = baro.onPressure(990f, i * 200L)
        assertEquals(0.0, baro.verticalSpeedMps!!, 1e-6)
    }
}
