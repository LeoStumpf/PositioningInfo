// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.model.AssistanceCapabilities
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.NavigationUpdate
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementEpoch
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementUpdate
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.DopCalculator
import io.github.leostumpf.positioninginfo.domain.Gga
import io.github.leostumpf.positioninginfo.domain.GpsNavState
import io.github.leostumpf.positioninginfo.domain.InterferenceMonitor
import io.github.leostumpf.positioninginfo.domain.NmeaState
import io.github.leostumpf.positioninginfo.domain.SignalBand
import io.github.leostumpf.positioninginfo.domain.SkyPoint
import io.github.leostumpf.positioninginfo.ui.gnss.GnssUiState
import io.github.leostumpf.positioninginfo.ui.gnss.SignalDetail
import io.github.leostumpf.positioninginfo.ui.receiver.ReceiverUiState
import io.github.leostumpf.positioninginfo.ui.signal.SignalUiState

/**
 * What the receiver says about itself beyond the fix: NMEA sentences, raw measurements,
 * navigation messages and the interference checks built on them. Fed by [AnalysisSession]
 * from the raw streams; it builds the receiver page and adds its details to the GNSS and
 * signal pages.
 */
internal class ReceiverAnalysis(private val capabilities: AssistanceCapabilities) {

    private var nmea = NmeaState()
    private var lastGgaAtMs: Long? = null
    private var rawStatus = RawStreamStatus.UNKNOWN
    private var rawEpochs = 0
    private var lastRaw: RawMeasurementEpoch? = null
    private var interference = InterferenceMonitor()

    /** Latest raw values per signal, and when each satellite was first heard this session. */
    private var signalDetails = mapOf<String, SignalMeasurement>()
    private val firstHeardMs = mutableMapOf<Pair<Constellation, Int>, Long>()
    private var navStatus = RawStreamStatus.UNKNOWN
    private var navFrames = mapOf<String, Int>()
    private var gpsNav = GpsNavState()
    private var lastSnapshot = GnssSnapshot.EMPTY

    fun onNmea(line: String) {
        val before = nmea.gga
        nmea = nmea.onLine(line)
        if (nmea.gga !== before) lastGgaAtMs = SystemClock.elapsedRealtime()
    }

    fun onMeasurements(update: RawMeasurementUpdate) {
        when (update) {
            is RawMeasurementUpdate.Status -> rawStatus = update.status

            is RawMeasurementUpdate.Epoch -> {
                rawStatus = RawStreamStatus.READY
                rawEpochs++
                lastRaw = update.value
                interference = interference.onEpoch(update.value.epoch)
                signalDetails = update.value.measurements.associateBy { m ->
                    val band = m.carrierFrequencyHz?.let { SignalBand.fromCarrierFrequencyHz(it.toFloat()) }
                    "${m.constellation.name}-${m.svid}-${band?.name ?: "?"}"
                }
            }
        }
    }

    fun onNavigation(update: NavigationUpdate) {
        when (update) {
            is NavigationUpdate.Status -> navStatus = update.status

            is NavigationUpdate.Frame -> {
                navStatus = RawStreamStatus.READY
                val frame = update.value
                navFrames = navFrames + (frame.signal to (navFrames[frame.signal] ?: 0) + 1)
                if (frame.isGpsL1Ca) gpsNav = gpsNav.onSubframe(frame.svid, frame.data)
            }
        }
    }

    /** Notes when each satellite was first heard, and keeps the satellites for the DOP. */
    fun onSnapshot(snapshot: GnssSnapshot) {
        lastSnapshot = snapshot
        val now = SystemClock.elapsedRealtime()
        snapshot.satellites.filter { it.cn0DbHz > 0f }.forEach {
            firstHeardMs.putIfAbsent(it.constellation to it.svid, now)
        }
    }

    /** The chip's last GGA sentence, if it came in the last few seconds. */
    fun freshGga(): Gga? =
        nmea.gga?.takeIf { lastGgaAtMs?.let { SystemClock.elapsedRealtime() - it < GGA_FRESH_MS } == true }

    /**
     * The receiver page. [trackingForMs] is how long tracking has run, null while it is off:
     * the page explains a long silence of the navigation-message stream.
     */
    fun state(trackingForMs: Long?): ReceiverUiState = ReceiverUiState(
        nmea = nmea,
        rawStatus = rawStatus,
        rawEpochs = rawEpochs,
        carrierPhaseValid = lastRaw?.carrierPhaseValid ?: 0,
        hasFullBias = lastRaw?.hasFullBias,
        assessment = if (rawEpochs > 0) interference.assessment else null,
        navStatus = navStatus,
        navFrames = navFrames,
        gps = gpsNav,
        currentGpsWeek = ((System.currentTimeMillis() - GPS_EPOCH_MS) / WEEK_MS).toInt(),
        capabilities = capabilities,
        navSilentMs = if (navFrames.isEmpty()) trackingForMs ?: 0L else 0L,
    )

    /** Adds acquisition stage, Doppler and raw values to every signal on the GNSS page. */
    fun decorateGnss(state: GnssUiState): GnssUiState = state.copy(
        details = state.signals.associate { row ->
            val m = signalDetails[row.baseKey]
            // The chip reports raw measurements for only some of the signals it tracks. One in
            // the fix has necessarily completed every step; for the rest the stage is unknown.
            row.baseKey to SignalDetail(
                stage = m?.let { AcquisitionStage.from(it.state) }
                    ?: AcquisitionStage.TIME_DECODED.takeIf { row.satellite.usedInFix },
                stageInferred = m == null && row.satellite.usedInFix,
                dopplerHz = m?.let {
                    AcquisitionStage.dopplerHz(it.pseudorangeRateMps, it.carrierFrequencyHz ?: L1_HZ)
                },
                multipath = m?.multipath,
                firstHeardMs = firstHeardMs[row.satellite.constellation to row.satellite.svid],
                raw = m,
            )
        },
    )

    /** Adds the app's own DOP from the satellites in the fix, and the chip's from GSA. */
    fun decorateSignal(state: SignalUiState): SignalUiState {
        // A satellite at exactly 0°/0° has no known position and cannot count towards the geometry.
        val used = lastSnapshot.satellites
            .filter { it.usedInFix && !(it.azimuthDegrees == 0f && it.elevationDegrees == 0f) }
            .distinctBy { it.constellation to it.svid }
        val gsa = nmea.gsa
        return state.copy(
            dop = DopCalculator.of(used.map { SkyPoint(it.azimuthDegrees, it.elevationDegrees) }),
            dopSatellites = used.size,
            chipPdop = gsa?.pdop,
            chipHdop = gsa?.hdop,
            chipVdop = gsa?.vdop,
        )
    }

    /** Forgets everything decoded so far; the streams keep running. */
    fun clear() {
        interference = InterferenceMonitor()
        nmea = NmeaState()
        gpsNav = GpsNavState()
        navFrames = emptyMap()
        rawEpochs = 0
        signalDetails = emptyMap()
        firstHeardMs.clear()
    }

    private companion object {
        const val GGA_FRESH_MS = 3_000L
        const val L1_HZ = 1_575.42e6
        const val GPS_EPOCH_MS = 315_964_800_000L
        const val WEEK_MS = 604_800_000L
    }
}
