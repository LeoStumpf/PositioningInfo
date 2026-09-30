// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import android.annotation.SuppressLint
import android.app.Application
import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.GnssRawDataSource
import io.github.leostumpf.positioninginfo.data.SensorDataSource
import io.github.leostumpf.positioninginfo.data.model.AssistanceCapabilities
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.BaroAltimeter
import io.github.leostumpf.positioninginfo.domain.ClimbSource
import io.github.leostumpf.positioninginfo.domain.CoordinateFormats
import io.github.leostumpf.positioninginfo.domain.PositionScatter
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.TripPoint
import io.github.leostumpf.positioninginfo.ui.gnss.GnssUiState
import io.github.leostumpf.positioninginfo.ui.position.AccuracyTest
import io.github.leostumpf.positioninginfo.ui.position.CoordinateFormat
import io.github.leostumpf.positioninginfo.ui.position.PositionUiState
import io.github.leostumpf.positioninginfo.ui.receiver.ReceiverUiState
import io.github.leostumpf.positioninginfo.ui.signal.SignalUiState
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.trip.TripRecorder
import io.github.leostumpf.positioninginfo.ui.trip.TripUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The analysis tools layered on top of the basic GNSS session: position formats and
 * altitude, the accuracy test, trip recording, receiver internals, and the compass and
 * signal map for the sky view.
 *
 * Owned by [PositioningInfoViewModel], which feeds it fixes, snapshots and ticks and cancels its
 * jobs together with its own when the app leaves the foreground. The pages' actions go
 * straight to the part they concern: [trip], [accuracyTest] and [sky].
 */
class AnalysisSession(
    application: Application,
    private val scope: CoroutineScope,
    capabilities: AssistanceCapabilities,
    speedUnit: () -> SpeedUnit,
    /** Called when something only the sky view shows has changed (heading, map). */
    private val onSkyChanged: () -> Unit,
) {
    private val rawSource = GnssRawDataSource(application)
    private val sensors = SensorDataSource(application)
    private val hasBarometer = sensors.hasBarometer

    private val _positionState = MutableStateFlow(PositionUiState(hasBarometer = hasBarometer))
    val positionState: StateFlow<PositionUiState> = _positionState.asStateFlow()

    val trip = TripRecorder(application, scope, hasBarometer, speedUnit)
    val tripState: StateFlow<TripUiState> = trip.state

    val accuracyTest = AccuracyTest(onChanged = ::publishPosition)
    val sky = SkyAnalysis(scope, sensors, onSkyChanged)
    private val receiver = ReceiverAnalysis(capabilities)

    private val _receiverState = MutableStateFlow(ReceiverUiState())
    val receiverState: StateFlow<ReceiverUiState> = _receiverState.asStateFlow()

    private var lastFix: SpeedFix? = null
    private var baro = BaroAltimeter()

    /** Whether the app is on screen; out of sight the pages are not rebuilt. */
    private var visible = true

    /** When tracking started, while it runs. */
    private var trackingSinceMs: Long? = null

    /** Starts the raw streams and sensors; the returned jobs are cancelled by the owner. */
    @SuppressLint("MissingPermission") // only called by startTracking, after its permission check
    fun start(): List<Job> {
        trackingSinceMs = SystemClock.elapsedRealtime()
        val jobs = mutableListOf<Job>()
        jobs += scope.launch { rawSource.nmea().collect(receiver::onNmea) }
        jobs += scope.launch { rawSource.measurements().collect(receiver::onMeasurements) }
        jobs += scope.launch { rawSource.navigationMessages().collect(receiver::onNavigation) }
        if (hasBarometer) {
            jobs += scope.launch {
                sensors.pressure().collect { baro = baro.onPressure(it.hPa, it.elapsedRealtimeMs) }
            }
        }
        jobs += scope.launch {
            try {
                awaitCancellation()
            } finally {
                trackingSinceMs = null
                sky.setActive(false)
            }
        }
        sky.setActive(visible)
        return jobs
    }

    fun onFix(fix: SpeedFix) {
        lastFix = fix
        if (fix.isCached) return publishPosition()
        val msl = seaLevelAltitude(fix)
        if (msl != null) baro = baro.onGnssAltitude(msl.first, fix.verticalAccuracyM, SystemClock.elapsedRealtime())
        val lat = fix.latitude
        val lon = fix.longitude
        if (lat != null && lon != null) {
            val mslM = msl?.first
            accuracyTest.add(
                PositionScatter.Sample(lat, lon, mslM ?: fix.ellipsoidAltitudeM, fix.horizontalAccuracyM),
            )
            if (trip.recording) recordTripPoint(fix, lat, lon, mslM)
        }
        publishPosition()
    }

    /**
     * Adds the fix to the trip, with the best altitude for the climb: calibrated barometer,
     * else standard-atmosphere barometer, else GNSS.
     */
    private fun recordTripPoint(fix: SpeedFix, lat: Double, lon: Double, mslM: Double?) {
        val point = TripPoint(
            timeUtcMs = fix.utcTimeMs ?: System.currentTimeMillis(),
            latitude = lat,
            longitude = lon,
            altitudeM = baro.calibratedAltitudeM ?: mslM,
            speedMps = fix.speedMps,
            accuracyM = fix.horizontalAccuracyM,
        )
        val (climbAltitude, climbSource) = baro.calibratedAltitudeM?.let { it to ClimbSource.BAROMETER }
            ?: baro.standardAltitudeM?.let { it to ClimbSource.BAROMETER_STANDARD }
            ?: (mslM to ClimbSource.GNSS)
        trip.add(point, climbAltitude, climbSource)
    }

    fun onSnapshot(snapshot: GnssSnapshot) {
        receiver.onSnapshot(snapshot)
        sky.onSnapshot(snapshot)
    }

    fun onTick() {
        publishPosition()
        publishReceiver()
    }

    /** On screen or not; out of sight nothing is published and the compass stops. */
    fun setVisible(value: Boolean) {
        visible = value
        trip.visible = value
        sky.setActive(value && trackingSinceMs != null)
        if (value) {
            publishPosition()
            publishReceiver()
        }
    }

    /**
     * Forgets everything this session has collected or stored: the trip on disk, and in
     * memory the accuracy test, signal map, barometer calibration, interference baselines,
     * decoded NMEA and navigation messages. The live streams keep running.
     */
    fun clearAll() {
        trip.clear(note = null)
        accuracyTest.reset()
        sky.clearMap()
        baro = BaroAltimeter()
        receiver.clear()
        publishPosition()
        publishReceiver()
        onSkyChanged()
    }

    // --- decoration of the pages the main ViewModel builds ------------------------------

    fun decorateSky(state: SkyUiState): SkyUiState = sky.decorate(state, lastFix)

    fun decorateGnss(state: GnssUiState): GnssUiState = receiver.decorateGnss(state)

    fun decorateSignal(state: SignalUiState): SignalUiState = receiver.decorateSignal(state)

    // --- publishing --------------------------------------------------------------------

    private fun publishPosition() {
        if (!visible) return
        val fix = lastFix
        val lat = fix?.latitude
        val lon = fix?.longitude
        val msl = fix?.let(::seaLevelAltitude)
        _positionState.value = PositionUiState(
            hasFix = lat != null && lon != null,
            isMock = fix?.isMock == true,
            fixAgeMs = fix?.let { SystemClock.elapsedRealtime() - it.elapsedRealtimeMs },
            horizontalAccuracyM = fix?.horizontalAccuracyM,
            coordinates = if (lat != null && lon != null) coordinatesOf(lat, lon) else emptyList(),
            gnssMslM = msl?.first,
            mslSource = msl?.second,
            gnssEllipsoidM = fix?.ellipsoidAltitudeM,
            verticalAccuracyM = fix?.verticalAccuracyM,
            geoidHeightM = msl?.first?.let { m -> fix.ellipsoidAltitudeM?.let { it - m } },
            hasBarometer = hasBarometer,
            pressureHpa = baro.pressureHpa,
            baroStandardM = baro.standardAltitudeM,
            baroCalibratedM = baro.calibratedAltitudeM,
            seaLevelPressureHpa = baro.seaLevelPressureHpa,
            verticalSpeedMps = baro.verticalSpeedMps,
            calibrationSamples = baro.calibrationSamples,
            scatterRunning = accuracyTest.running,
            scatter = accuracyTest.stats(),
        )
    }

    private fun publishReceiver() {
        if (!visible) return
        _receiverState.value = receiver.state(trackingSinceMs?.let { SystemClock.elapsedRealtime() - it })
    }

    /**
     * Height above sea level and where it came from. Android reports height above the
     * ellipsoid; the chip's NMEA carries its own geoid model, which is the best source
     * available offline.
     */
    private fun seaLevelAltitude(fix: SpeedFix): Pair<Double, String>? {
        val gga = receiver.freshGga()
        val ggaMsl = gga?.altitudeMslM?.takeIf { (gga.fixQuality ?: 0) > 0 }
        val separation = gga?.geoidSeparationM
        val ellipsoid = fix.ellipsoidAltitudeM
        return when {
            ggaMsl != null -> ggaMsl to "chip's geoid model (NMEA GGA)"
            fix.mslAltitudeM != null -> fix.mslAltitudeM to "Android geoid model"
            separation != null && ellipsoid != null -> (ellipsoid - separation) to "ellipsoid − chip's geoid height"
            else -> null
        }
    }
}

/** The position in every format the page offers. */
private fun coordinatesOf(lat: Double, lon: Double): List<Pair<CoordinateFormat, String>> = listOf(
    CoordinateFormat.DECIMAL to CoordinateFormats.decimal(lat, lon),
    CoordinateFormat.DMS to CoordinateFormats.dms(lat, lon),
    CoordinateFormat.UTM to (CoordinateFormats.utm(lat, lon)?.toString() ?: "outside UTM"),
    CoordinateFormat.MGRS to (CoordinateFormats.mgrs(lat, lon) ?: "outside MGRS"),
    CoordinateFormat.PLUS_CODE to CoordinateFormats.plusCode(lat, lon),
    CoordinateFormat.MAIDENHEAD to CoordinateFormats.maidenhead(lat, lon),
)
