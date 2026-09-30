// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.demo

import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.DemoSource
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.PressureReading
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.Constellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * A drive south of Munich under an open, multi-constellation, dual-frequency sky: what a
 * good phone sees on a good day. Deterministic, so every screenshot run looks alike.
 *
 * Loaded by [io.github.leostumpf.positioninginfo.data.DemoMode] by name; debug builds only.
 */
class ScriptedDemoSource : DemoSource {

    private val startMs = SystemClock.elapsedRealtime()

    override fun fixes(): Flow<SpeedFix> = flow {
        // A hot start takes a few seconds, not none.
        delay(FIRST_FIX_MS)
        var lat = START_LAT
        var lon = START_LON
        var lastS = secondsSinceStart()
        while (true) {
            val s = secondsSinceStart()
            val speed = speedAt(s)
            val bearing = bearingAt(s)
            // Dead reckoning along the course: metres to degrees on a sphere.
            val step = speed * (s - lastS)
            lat += step * cos(Math.toRadians(bearing)) / METRES_PER_DEG
            lon += step * sin(Math.toRadians(bearing)) / (METRES_PER_DEG * cos(Math.toRadians(lat)))
            lastS = s
            val msl = heightAt(s)
            emit(
                SpeedFix(
                    speedMps = speed.toFloat(),
                    speedAccuracyMps = SPEED_ACCURACY_MPS,
                    horizontalAccuracyM = (ACCURACY_M + ACCURACY_SWING_M * sin(s / ACCURACY_PERIOD_S)).toFloat(),
                    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                    utcTimeMs = System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lon,
                    ellipsoidAltitudeM = msl + GEOID_HEIGHT_M,
                    mslAltitudeM = msl,
                    verticalAccuracyM = VERTICAL_ACCURACY_M,
                    bearingAccuracyDeg = BEARING_ACCURACY_DEG,
                    bearingDegrees = bearing.toFloat(),
                ),
            )
            delay(untilNextTick())
        }
    }

    override fun snapshots(): Flow<GnssSnapshot> = flow {
        while (true) {
            val minutes = secondsSinceStart() / SECONDS_PER_MIN
            // The sky runs faster than the clock, so a few minutes draw the paths of half an hour.
            emit(GnssSnapshot(SKY.flatMap { it.signalsAt(minutes * SKY_SPEEDUP) }, hasReported = true))
            delay(untilNextTick())
        }
    }

    override fun pressure(): Flow<PressureReading> = flow {
        while (true) {
            // The international barometric formula, on a day with high pressure at sea level.
            val h = heightAt(secondsSinceStart())
            val hPa = SEA_LEVEL_HPA * (1 - BARO_LAPSE_PER_M * h).pow(BARO_EXPONENT)
            emit(PressureReading(hPa.toFloat(), SystemClock.elapsedRealtime()))
            delay(untilNextTick())
        }
    }

    override fun nmea(): Flow<String> = emptyFlow()

    private fun secondsSinceStart() = (SystemClock.elapsedRealtime() - startMs) / MS_PER_S

    /** Time to the next whole second since the start, so updates come at exactly 1 Hz, like a receiver's. */
    private fun untilNextTick(): Long = INTERVAL_MS - (SystemClock.elapsedRealtime() - startMs) % INTERVAL_MS

    /** Country-road speed, 50–70 km/h. */
    private fun speedAt(s: Double) = BASE_SPEED_MPS + SPEED_SWING_MPS * sin(s / SPEED_PERIOD_S) +
        SPEED_RIPPLE_MPS * sin(s / SPEED_RIPPLE_PERIOD_S)

    /** A road that bends gently around its general direction. */
    private fun bearingAt(s: Double) = BASE_BEARING_DEG + BEARING_SWING_DEG * sin(s / BEARING_PERIOD_S)

    /** Rolling hills: some tens of metres up and down. */
    private fun heightAt(s: Double) = BASE_HEIGHT_M + HILL_M * sin(s / HILL_PERIOD_S) +
        KNOLL_M * sin(s / KNOLL_PERIOD_S)

    /**
     * One satellite: where it starts in the sky, how it moves (degrees per minute) and the
     * carrier frequencies it is heard on.
     */
    private class Sat(
        val constellation: Constellation,
        val svid: Int,
        val azimuth: Double,
        val elevation: Double,
        val azimuthRate: Double,
        val elevationRate: Double,
        val bandsHz: List<Float>,
    ) {
        fun signalsAt(minutes: Double): List<SatelliteInfo> {
            val el = (elevation + elevationRate * minutes).coerceIn(0.0, ZENITH_DEG)
            val az = ((azimuth + azimuthRate * minutes) % FULL_TURN_DEG + FULL_TURN_DEG) % FULL_TURN_DEG
            val heard = el >= HEARD_ABOVE_DEG
            return bandsHz.mapIndexed { i, hz ->
                // Stronger overhead; the second band a few dB weaker; a slow, satellite-specific wobble.
                val cn0 = if (heard) {
                    CN0_FLOOR + CN0_PER_DEG * el - i * SECOND_BAND_LOSS_DB +
                        CN0_WOBBLE_DB * sin(minutes * WOBBLE_RATE + svid)
                } else {
                    0.0
                }
                val used = heard && el >= USED_ABOVE_DEG && cn0 >= USED_CN0
                SatelliteInfo(
                    svid = svid,
                    constellation = constellation,
                    cn0DbHz = cn0.toFloat(),
                    elevationDegrees = el.toFloat(),
                    azimuthDegrees = az.toFloat(),
                    usedInFix = used,
                    hasAlmanac = true,
                    hasEphemeris = used || el >= EPHEMERIS_ABOVE_DEG,
                    carrierFrequencyHz = hz,
                )
            }
        }
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
        const val FIRST_FIX_MS = 4_700L
        const val SKY_SPEEDUP = 5.0
        const val MS_PER_S = 1_000.0
        const val SECONDS_PER_MIN = 60.0
        const val METRES_PER_DEG = 111_320.0

        const val START_LAT = 47.9985
        const val START_LON = 11.3400
        const val BASE_SPEED_MPS = 16.5
        const val SPEED_SWING_MPS = 2.2
        const val SPEED_PERIOD_S = 47.0
        const val SPEED_RIPPLE_MPS = 1.1
        const val SPEED_RIPPLE_PERIOD_S = 13.0
        const val BASE_BEARING_DEG = 205.0
        const val BEARING_SWING_DEG = 25.0
        const val BEARING_PERIOD_S = 180.0
        const val BASE_HEIGHT_M = 598.0
        const val HILL_M = 18.0
        const val HILL_PERIOD_S = 240.0
        const val KNOLL_M = 6.0
        const val KNOLL_PERIOD_S = 67.0
        const val GEOID_HEIGHT_M = 47.3

        const val ACCURACY_M = 3.1
        const val ACCURACY_SWING_M = 0.6
        const val ACCURACY_PERIOD_S = 31.0
        const val SPEED_ACCURACY_MPS = 0.21f
        const val VERTICAL_ACCURACY_M = 4.4f
        const val BEARING_ACCURACY_DEG = 2.4f

        const val SEA_LEVEL_HPA = 1_021.4
        const val BARO_LAPSE_PER_M = 2.25577e-5
        const val BARO_EXPONENT = 5.25588

        const val ZENITH_DEG = 90.0
        const val FULL_TURN_DEG = 360.0
        const val HEARD_ABOVE_DEG = 4.0
        const val USED_ABOVE_DEG = 10.0
        const val EPHEMERIS_ABOVE_DEG = 7.0
        const val USED_CN0 = 25.0
        const val CN0_FLOOR = 22.0
        const val CN0_PER_DEG = 0.27
        const val SECOND_BAND_LOSS_DB = 3.0
        const val CN0_WOBBLE_DB = 1.5
        const val WOBBLE_RATE = 0.9

        const val L1 = 1_575.42e6f
        const val L5 = 1_176.45e6f
        const val GLONASS_L1 = 1_602.5625e6f
        const val B1I = 1_561.098e6f

        val GPS = Constellation.GPS
        val GAL = Constellation.GALILEO
        val GLO = Constellation.GLONASS
        val BDS = Constellation.BEIDOU

        /** A plausible sky over southern Germany; the rates are the slow drift a real orbit shows. */
        val SKY = listOf(
            Sat(GPS, 2, 48.0, 63.0, 0.35, -0.12, listOf(L1, L5)),
            Sat(GPS, 5, 131.0, 41.0, 0.22, 0.18, listOf(L1)),
            Sat(GPS, 11, 302.0, 28.0, -0.18, 0.2, listOf(L1)),
            Sat(GPS, 12, 210.0, 71.0, 0.4, 0.1, listOf(L1)),
            Sat(GPS, 18, 262.0, 14.0, 0.12, -0.2, listOf(L1)),
            Sat(GPS, 25, 91.0, 22.0, 0.25, 0.22, listOf(L1, L5)),
            Sat(GPS, 26, 168.0, 9.0, -0.1, 0.15, listOf(L1, L5)),
            Sat(GPS, 29, 334.0, 47.0, -0.3, -0.16, listOf(L1)),
            Sat(GPS, 31, 16.0, 6.0, 0.08, -0.14, listOf(L1)),
            Sat(GAL, 4, 71.0, 38.0, 0.28, 0.15, listOf(L1, L5)),
            Sat(GAL, 9, 197.0, 52.0, 0.3, -0.18, listOf(L1, L5)),
            Sat(GAL, 10, 282.0, 33.0, -0.2, 0.12, listOf(L1, L5)),
            Sat(GAL, 19, 143.0, 18.0, 0.15, 0.21, listOf(L1, L5)),
            Sat(GAL, 21, 355.0, 66.0, -0.35, 0.08, listOf(L1, L5)),
            Sat(GAL, 27, 236.0, 11.0, 0.1, -0.12, listOf(L1)),
            Sat(GLO, 3, 104.0, 57.0, 0.33, -0.15, listOf(GLONASS_L1)),
            Sat(GLO, 13, 318.0, 24.0, -0.2, 0.19, listOf(GLONASS_L1)),
            Sat(GLO, 14, 22.0, 35.0, 0.25, 0.14, listOf(GLONASS_L1)),
            Sat(GLO, 22, 247.0, 44.0, 0.18, -0.2, listOf(GLONASS_L1)),
            Sat(GLO, 23, 176.0, 5.0, -0.06, -0.1, listOf(GLONASS_L1)),
            Sat(BDS, 19, 128.0, 25.0, 0.24, 0.17, listOf(B1I, L5)),
            Sat(BDS, 22, 58.0, 16.0, 0.16, 0.2, listOf(B1I, L5)),
            Sat(BDS, 29, 220.0, 38.0, -0.22, 0.13, listOf(B1I)),
            Sat(BDS, 34, 289.0, 58.0, -0.3, -0.1, listOf(B1I, L5)),
            Sat(BDS, 45, 5.0, 12.0, 0.1, 0.16, listOf(B1I)),
            Sat(Constellation.SBAS, 123, 172.0, 31.0, 0.0, 0.0, listOf(L1)),
            Sat(Constellation.SBAS, 136, 197.0, 29.0, 0.0, 0.0, listOf(L1)),
        )
    }
}
