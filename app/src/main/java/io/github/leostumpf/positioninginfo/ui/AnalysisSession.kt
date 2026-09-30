// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import io.github.leostumpf.positioninginfo.domain.counted
import android.annotation.SuppressLint
import android.app.Application
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.GnssRawDataSource
import io.github.leostumpf.positioninginfo.data.SensorDataSource
import io.github.leostumpf.positioninginfo.data.TripStore
import io.github.leostumpf.positioninginfo.data.model.AssistanceCapabilities
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.HeadingReading
import io.github.leostumpf.positioninginfo.data.model.NavigationUpdate
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementEpoch
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementUpdate
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import io.github.leostumpf.positioninginfo.domain.BaroAltimeter
import io.github.leostumpf.positioninginfo.domain.CompassTrust
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.SignalBand
import io.github.leostumpf.positioninginfo.ui.gnss.GnssUiState
import io.github.leostumpf.positioninginfo.ui.gnss.SignalDetail
import io.github.leostumpf.positioninginfo.domain.CoordinateFormats
import io.github.leostumpf.positioninginfo.domain.DopCalculator
import io.github.leostumpf.positioninginfo.domain.GpsNavState
import io.github.leostumpf.positioninginfo.domain.InterferenceMonitor
import io.github.leostumpf.positioninginfo.domain.NmeaState
import io.github.leostumpf.positioninginfo.domain.ObstructionMap
import io.github.leostumpf.positioninginfo.domain.PositionScatter
import io.github.leostumpf.positioninginfo.domain.ScatterStats
import io.github.leostumpf.positioninginfo.domain.SkyPoint
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.TripAccumulator
import io.github.leostumpf.positioninginfo.domain.ClimbSource
import io.github.leostumpf.positioninginfo.domain.TripPoint
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.position.PositionUiState
import io.github.leostumpf.positioninginfo.ui.receiver.ReceiverUiState
import io.github.leostumpf.positioninginfo.ui.signal.SignalUiState
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.trip.TripUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The analysis tools layered on top of the basic GNSS session: position formats and
 * altitude, the accuracy test, trip recording, receiver internals, and the compass and
 * signal map for the sky view.
 *
 * Owned by [PositioningInfoViewModel], which feeds it fixes, snapshots and ticks and cancels its
 * jobs together with its own when the app leaves the foreground.
 */
class AnalysisSession(
    application: Application,
    private val scope: CoroutineScope,
    private val capabilities: AssistanceCapabilities,
    private val speedUnit: () -> SpeedUnit,
    /** Called when something only the sky view shows has changed (heading, map). */
    private val onSkyChanged: () -> Unit,
) {
    private val rawSource = GnssRawDataSource(application)
    private val sensors = SensorDataSource(application)
    private val tripStore = TripStore(application)
    private val hasBarometer = sensors.hasBarometer

    private val _positionState = MutableStateFlow(PositionUiState(hasBarometer = hasBarometer))
    val positionState: StateFlow<PositionUiState> = _positionState.asStateFlow()

    private val _tripState = MutableStateFlow(TripUiState(climbFromBarometer = hasBarometer))
    val tripState: StateFlow<TripUiState> = _tripState.asStateFlow()

    private val _receiverState = MutableStateFlow(ReceiverUiState())
    val receiverState: StateFlow<ReceiverUiState> = _receiverState.asStateFlow()

    private var lastFix: SpeedFix? = null
    private var lastSnapshot = GnssSnapshot.EMPTY

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

    private var baro = BaroAltimeter()
    private var heading: HeadingReading? = null
    /** Smoothed magnetometer field strength, while compass mode runs. */
    private var magneticUt: Double? = null
    private var headingJob: Job? = null
    private var tracking = false
    /** Whether the app is on screen; out of sight the pages are not rebuilt. */
    private var visible = true
    private var trackingStartedAtMs = 0L
    /** Built from [scatter] only when it changes, not on every tick. */
    private var scatterStats: ScatterStats? = null
    private var scatterStatsFor: PositionScatter? = null
    /** The World Magnetic Model barely changes over a few kilometres; see [geomagnetic]. */
    private var geomagnetic: Geomagnetic? = null
    private var compassMode = false
    private var mapMode = false
    private var showPaths = true
    private var obstruction = ObstructionMap()

    private var scatter = PositionScatter()
    private var scatterRunning = false

    private var trip = TripAccumulator()
    /** Appended in place: copying the list on every fix cost O(n²) over a long trip. */
    private val tripAltitudes = ArrayList<Double>()
    private var tripRecording = false
    /** Recording waits until the saved trip is loaded, so the load cannot overwrite new points. */
    private var tripLoaded = false
    /** Bumped by every delete, so a load that was already under way cannot bring a trip back. */
    private var tripGeneration = 0
    private var tripMessage: String? = null

    init {
        scope.launch {
            val generation = tripGeneration
            val saved = tripStore.load()
            if (generation == tripGeneration) {
                // The file keeps the calibrated barometric height where there was one, else GNSS.
                val source = if (hasBarometer) ClimbSource.BAROMETER else ClimbSource.GNSS
                trip = saved.fold(TripAccumulator()) { acc, p -> acc.add(p, p.altitudeM, source) }
                tripAltitudes.clear()
                saved.mapNotNullTo(tripAltitudes) { it.altitudeM }
            }
            tripLoaded = true
            publishTrip()
        }
    }

    /** Starts the raw streams and sensors; the returned jobs are cancelled by the owner. */
    @SuppressLint("MissingPermission")  // only called by startTracking, after its permission check
    fun start(): List<Job> {
        tracking = true
        trackingStartedAtMs = SystemClock.elapsedRealtime()
        val jobs = mutableListOf<Job>()
        jobs += scope.launch {
            rawSource.nmea().collect { line ->
                val before = nmea.gga
                nmea = nmea.onLine(line)
                if (nmea.gga !== before) lastGgaAtMs = SystemClock.elapsedRealtime()
            }
        }
        jobs += scope.launch {
            rawSource.measurements().collect { update ->
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
        }
        jobs += scope.launch {
            rawSource.navigationMessages().collect { update ->
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
        }
        if (hasBarometer) {
            jobs += scope.launch {
                sensors.pressure().collect { baro = baro.onPressure(it.hPa, it.elapsedRealtimeMs) }
            }
        }
        jobs += scope.launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                tracking = false
                headingJob?.cancel()
                headingJob = null
            }
        }
        updateHeadingJob()
        return jobs
    }

    fun onFix(fix: SpeedFix) {
        lastFix = fix
        if (fix.isCached) return publishPosition()
        val now = SystemClock.elapsedRealtime()
        val msl = seaLevelAltitude(fix)
        if (msl != null) baro = baro.onGnssAltitude(msl.first, fix.verticalAccuracyM, now)

        val lat = fix.latitude
        val lon = fix.longitude
        if (lat != null && lon != null) {
            if (scatterRunning) {
                scatter = scatter.add(
                    PositionScatter.Sample(lat, lon, msl?.first ?: fix.ellipsoidAltitudeM, fix.horizontalAccuracyM),
                )
            }
            if (tripRecording && trip.stats.points >= MAX_TRIP_POINTS) {
                tripRecording = false
                tripMessage = "The trip is full ($MAX_TRIP_POINTS points). Export it and delete it to record a new one."
                publishTrip()
            } else if (tripRecording) {
                val point = TripPoint(
                    timeUtcMs = fix.utcTimeMs ?: System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lon,
                    altitudeM = baro.calibratedAltitudeM ?: msl?.first,
                    speedMps = fix.speedMps,
                    accuracyM = fix.horizontalAccuracyM,
                )
                val (climbAltitude, climbSource) = baro.calibratedAltitudeM?.let { it to ClimbSource.BAROMETER }
                    ?: baro.standardAltitudeM?.let { it to ClimbSource.BAROMETER_STANDARD }
                    ?: (msl?.first to ClimbSource.GNSS)
                trip = trip.add(point, climbAltitude, climbSource)
                point.altitudeM?.let { tripAltitudes += it }
                scope.launch { tripStore.append(point) }
                publishTrip()
            }
        }
        publishPosition()
    }

    fun onSnapshot(snapshot: GnssSnapshot) {
        lastSnapshot = snapshot
        val now = SystemClock.elapsedRealtime()
        snapshot.satellites.filter { it.cn0DbHz > 0f }.forEach { firstHeardMs.putIfAbsent(it.constellation to it.svid, now) }
        obstruction = obstruction.onSnapshot(snapshot.satellites)
    }

    fun onTick() {
        publishPosition()
        publishReceiver()
    }

    /** On screen or not; out of sight nothing is published and the compass stops. */
    fun setVisible(value: Boolean) {
        visible = value
        updateHeadingJob()
        if (value) {
            publishPosition()
            publishTrip()
            publishReceiver()
        }
    }

    // --- actions ---------------------------------------------------------------------

    fun toggleScatter() {
        scatterRunning = !scatterRunning
        publishPosition()
    }

    fun resetScatter() {
        scatter = PositionScatter()
        scatterRunning = false
        publishPosition()
    }

    fun toggleTrip() {
        if (!tripLoaded) return
        tripRecording = !tripRecording
        tripMessage = null
        publishTrip()
    }

    fun clearTrip() {
        tripRecording = false
        trip = TripAccumulator()
        tripAltitudes.clear()
        tripGeneration++
        tripMessage = "Trip deleted."
        scope.launch { tripStore.clear() }
        publishTrip()
    }

    fun exportTrip(uri: Uri) {
        scope.launch {
            val ok = tripStore.exportGpx(uri, "Positioning Info trip ${suggestedDate()}")
            tripMessage = if (ok) "Exported ${trip.stats.points.counted("point")} as GPX." else "Export failed."
            publishTrip()
        }
    }

    fun suggestedTripFileName(): String = "trip-${suggestedDate().replace(' ', '_').replace(":", "")}.gpx"

    fun toggleCompass() {
        compassMode = !compassMode
        updateHeadingJob()
        onSkyChanged()
    }

    fun toggleShowPaths() {
        showPaths = !showPaths
        onSkyChanged()
    }

    fun toggleMap() {
        mapMode = !mapMode
        onSkyChanged()
    }

    /**
     * Forgets everything this session has collected or stored: the trip on disk, and in
     * memory the accuracy test, signal map, barometer calibration, interference baselines,
     * decoded NMEA and navigation messages. The live streams keep running.
     */
    fun clearAll() {
        tripRecording = false
        trip = TripAccumulator()
        tripAltitudes.clear()
        tripGeneration++
        tripMessage = null
        scope.launch { tripStore.clear() }
        scatter = PositionScatter()
        scatterRunning = false
        obstruction = ObstructionMap()
        baro = BaroAltimeter()
        interference = InterferenceMonitor()
        nmea = NmeaState()
        gpsNav = GpsNavState()
        navFrames = emptyMap()
        rawEpochs = 0
        signalDetails = emptyMap()
        firstHeardMs.clear()
        publishTrip()
        publishPosition()
        publishReceiver()
        onSkyChanged()
    }

    // --- decoration of the pages the main ViewModel builds ------------------------------

    fun decorateSky(state: SkyUiState): SkyUiState {
        val fix = lastFix
        val field = fix?.let(::geomagneticAt)
        val declination = field?.declinationDegrees
        val h = heading
        val trueHeading = h?.let { ((it.magneticAzimuthDegrees + (declination ?: 0f)) % 360f + 360f) % 360f }
        val moving = (fix?.speedMps ?: 0f) > 1f && fix?.bearingDegrees != null
        val parts = mutableListOf<String>()
        if (compassMode && trueHeading != null) {
            parts += if (declination != null) "Heading ${trueHeading.roundToInt()}° true" else "Heading ${trueHeading.roundToInt()}° magnetic"
        }
        if (declination != null && (compassMode || moving)) {
            parts += "declination ${abs(declination).fmt(1)}° ${if (declination >= 0) "E" else "W"}"
        }
        if (moving) parts += "course ${fix!!.bearingDegrees!!.roundToInt()}°"
        val expectedUt = field?.fieldUt
        return state.copy(
            magneticUt = if (compassMode) magneticUt else null,
            compassTrust = if (compassMode) magneticUt?.let { m -> expectedUt?.let { CompassTrust(m, it) } } else null,
            compassMode = compassMode,
            mapMode = mapMode,
            showPaths = showPaths,
            headingDegrees = if (compassMode) trueHeading else null,
            headingText = parts.joinToString(" · ").ifEmpty { null },
            compassUnreliable = compassMode && h != null && h.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            obstruction = if (mapMode) obstruction.cells else emptyList(),
            obstructionSamples = obstruction.totalSamples,
        )
    }

    fun decorateGnss(state: GnssUiState): GnssUiState = state.copy(
        details = state.signals.associate { row ->
            val m = signalDetails[row.baseKey]
            // The chip reports raw measurements for only some of the signals it tracks. One in
            // the fix has necessarily completed every step; for the rest the stage is unknown.
            row.baseKey to SignalDetail(
                stage = m?.let { AcquisitionStage.from(it.state) }
                    ?: AcquisitionStage.TIME_DECODED.takeIf { row.satellite.usedInFix },
                stageInferred = m == null && row.satellite.usedInFix,
                dopplerHz = m?.let { AcquisitionStage.dopplerHz(it.pseudorangeRateMps, it.carrierFrequencyHz ?: L1_HZ) },
                multipath = m?.multipath,
                firstHeardMs = firstHeardMs[row.satellite.constellation to row.satellite.svid],
                raw = m,
            )
        },
    )

    fun decorateSignal(state: SignalUiState): SignalUiState {
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
            coordinates = if (lat != null && lon != null) {
                listOf(
                    "Decimal" to CoordinateFormats.decimal(lat, lon),
                    "Degrees, minutes, seconds" to CoordinateFormats.dms(lat, lon),
                    "UTM" to (CoordinateFormats.utm(lat, lon)?.toString() ?: "outside UTM"),
                    "MGRS" to (CoordinateFormats.mgrs(lat, lon) ?: "outside MGRS"),
                    "Plus Code" to CoordinateFormats.plusCode(lat, lon),
                    "Maidenhead" to CoordinateFormats.maidenhead(lat, lon),
                )
            } else {
                emptyList()
            },
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
            scatterRunning = scatterRunning,
            scatter = scatterStatsOf(scatter),
        )
    }

    private fun publishTrip() {
        if (!visible) return
        _tripState.value = TripUiState(
            recording = tripRecording,
            stats = trip.stats,
            unit = speedUnit(),
            climbFromBarometer = hasBarometer,
            message = tripMessage,
            elevationProfile = thin(tripAltitudes, PROFILE_POINTS),
        )
    }

    private fun publishReceiver() {
        if (!visible) return
        _receiverState.value = ReceiverUiState(
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
            navSilentMs = if (navFrames.isEmpty() && tracking) SystemClock.elapsedRealtime() - trackingStartedAtMs else 0L,
        )
    }

    // --- helpers -----------------------------------------------------------------------

    private fun scatterStatsOf(current: PositionScatter): ScatterStats? {
        if (current !== scatterStatsFor) {
            scatterStats = current.stats()
            scatterStatsFor = current
        }
        return scatterStats
    }

    /** The magnetic model at the fix, re-evaluated only after moving about 10 km. */
    private class Geomagnetic(val latitude: Double, val longitude: Double, val declinationDegrees: Float, val fieldUt: Double)

    private fun geomagneticAt(fix: SpeedFix): Geomagnetic? {
        val lat = fix.latitude ?: return null
        val lon = fix.longitude ?: return null
        geomagnetic?.let { if (abs(it.latitude - lat) < 0.1 && abs(it.longitude - lon) < 0.1) return it }
        val alt = fix.ellipsoidAltitudeM ?: 0.0
        val now = System.currentTimeMillis()
        return Geomagnetic(
            latitude = lat,
            longitude = lon,
            declinationDegrees = SensorDataSource.declinationDegrees(lat, lon, alt, now),
            fieldUt = SensorDataSource.expectedFieldUt(lat, lon, alt, now),
        ).also { geomagnetic = it }
    }

    /**
     * Height above sea level and where it came from. Android reports height above the
     * ellipsoid; the chip's NMEA carries its own geoid model, which is the best source
     * available offline.
     */
    private fun seaLevelAltitude(fix: SpeedFix): Pair<Double, String>? {
        val gga = nmea.gga
        val ggaFresh = lastGgaAtMs?.let { SystemClock.elapsedRealtime() - it < GGA_FRESH_MS } == true
        if (ggaFresh && gga?.altitudeMslM != null && (gga.fixQuality ?: 0) > 0) {
            return gga.altitudeMslM to "chip's geoid model (NMEA GGA)"
        }
        fix.mslAltitudeM?.let { return it to "Android geoid model" }
        val separation = gga?.geoidSeparationM
        val ellipsoid = fix.ellipsoidAltitudeM
        if (separation != null && ellipsoid != null) return (ellipsoid - separation) to "ellipsoid − chip's geoid height"
        return null
    }

    private fun updateHeadingJob() {
        val wanted = compassMode && tracking && visible && sensors.hasCompass
        if (wanted && headingJob == null) {
            headingJob = scope.launch {
                launch {
                    sensors.heading().collect { reading ->
                        val previous = heading
                        heading = reading
                        if (previous == null ||
                            abs(previous.magneticAzimuthDegrees - reading.magneticAzimuthDegrees) >= 1f ||
                            previous.accuracy != reading.accuracy
                        ) {
                            onSkyChanged()
                        }
                    }
                }
                launch {
                    // Smoothed, because the raw magnitude jitters by a few µT sample to sample.
                    sensors.magneticFieldUt().collect { ut ->
                        magneticUt = magneticUt?.let { it + (ut - it) * FIELD_SMOOTHING } ?: ut
                    }
                }
            }
        } else if (!wanted) {
            headingJob?.cancel()
            headingJob = null
            heading = null
            magneticUt = null
        }
    }

    /** Every n-th value, so a long trip still draws cheaply. */
    private fun thin(values: List<Double>, max: Int): List<Double> {
        if (values.size <= max) return values.toList()
        val step = values.size / max + 1
        return List((values.size + step - 1) / step) { values[it * step] }
    }

    private fun suggestedDate(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private companion object {
        const val GGA_FRESH_MS = 3_000L
        const val PROFILE_POINTS = 300
        /** About 55 hours at one fix a second, some 12 MB on disk; the whole file is read at start. */
        const val MAX_TRIP_POINTS = 200_000
        const val FIELD_SMOOTHING = 0.1
        const val L1_HZ = 1_575.42e6
        const val GPS_EPOCH_MS = 315_964_800_000L
        const val WEEK_MS = 604_800_000L
    }
}
