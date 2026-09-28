// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui

import android.annotation.SuppressLint
import android.app.Application
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import de.leostumpf.gpstools.data.GnssRawDataSource
import de.leostumpf.gpstools.data.SensorDataSource
import de.leostumpf.gpstools.data.TripStore
import de.leostumpf.gpstools.data.model.GnssSnapshot
import de.leostumpf.gpstools.data.model.HeadingReading
import de.leostumpf.gpstools.data.model.NavigationUpdate
import de.leostumpf.gpstools.data.model.RawMeasurementEpoch
import de.leostumpf.gpstools.data.model.RawMeasurementUpdate
import de.leostumpf.gpstools.data.model.RawStreamStatus
import de.leostumpf.gpstools.data.model.SpeedFix
import de.leostumpf.gpstools.domain.BaroAltimeter
import de.leostumpf.gpstools.domain.CoordinateFormats
import de.leostumpf.gpstools.domain.DopCalculator
import de.leostumpf.gpstools.domain.GpsNavState
import de.leostumpf.gpstools.domain.InterferenceMonitor
import de.leostumpf.gpstools.domain.NmeaState
import de.leostumpf.gpstools.domain.ObstructionMap
import de.leostumpf.gpstools.domain.PositionScatter
import de.leostumpf.gpstools.domain.SkyPoint
import de.leostumpf.gpstools.domain.SpeedUnit
import de.leostumpf.gpstools.domain.TripAccumulator
import de.leostumpf.gpstools.domain.TripPoint
import de.leostumpf.gpstools.ui.common.fmt
import de.leostumpf.gpstools.ui.position.PositionUiState
import de.leostumpf.gpstools.ui.receiver.ReceiverUiState
import de.leostumpf.gpstools.ui.signal.SignalUiState
import de.leostumpf.gpstools.ui.sky.SkyUiState
import de.leostumpf.gpstools.ui.trip.TripUiState
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
 * Owned by [GpsToolsViewModel], which feeds it fixes, snapshots and ticks and cancels its
 * jobs together with its own when the app leaves the foreground.
 */
class AnalysisSession(
    application: Application,
    private val scope: CoroutineScope,
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
    private var navStatus = RawStreamStatus.UNKNOWN
    private var navFrames = mapOf<String, Int>()
    private var gpsNav = GpsNavState()

    private var baro = BaroAltimeter()
    private var heading: HeadingReading? = null
    private var headingJob: Job? = null
    private var tracking = false
    private var trackingStartedAtMs = 0L
    private var compassMode = false
    private var mapMode = false
    private var obstruction = ObstructionMap()

    private var scatter = PositionScatter()
    private var scatterRunning = false

    private var trip = TripAccumulator()
    private var tripRecording = false
    private var tripMessage: String? = null

    init {
        scope.launch {
            val saved = tripStore.load()
            trip = saved.fold(TripAccumulator()) { acc, p -> acc.add(p, p.altitudeM) }
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
            if (tripRecording) {
                val point = TripPoint(
                    timeUtcMs = fix.utcTimeMs ?: System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lon,
                    altitudeM = baro.calibratedAltitudeM ?: msl?.first,
                    speedMps = fix.speedMps,
                    accuracyM = fix.horizontalAccuracyM,
                )
                trip = trip.add(point, baro.calibratedAltitudeM ?: baro.standardAltitudeM ?: msl?.first)
                scope.launch { tripStore.append(point) }
                publishTrip()
            }
        }
        publishPosition()
    }

    fun onSnapshot(snapshot: GnssSnapshot) {
        lastSnapshot = snapshot
        obstruction = obstruction.onSnapshot(snapshot.satellites)
    }

    fun onTick() {
        publishPosition()
        publishReceiver()
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
        tripRecording = !tripRecording
        tripMessage = null
        publishTrip()
    }

    fun clearTrip() {
        tripRecording = false
        trip = TripAccumulator()
        tripMessage = "Trip deleted."
        scope.launch { tripStore.clear() }
        publishTrip()
    }

    fun exportTrip(uri: Uri) {
        scope.launch {
            val ok = tripStore.exportGpx(uri, "GpsTools trip ${suggestedDate()}")
            tripMessage = if (ok) "Exported ${trip.stats.points} points as GPX." else "Export failed."
            publishTrip()
        }
    }

    fun suggestedTripFileName(): String = "trip-${suggestedDate().replace(' ', '_').replace(":", "")}.gpx"

    fun toggleCompass() {
        compassMode = !compassMode
        updateHeadingJob()
        onSkyChanged()
    }

    fun toggleMap() {
        mapMode = !mapMode
        onSkyChanged()
    }

    // --- decoration of the pages the main ViewModel builds ------------------------------

    fun decorateSky(state: SkyUiState): SkyUiState {
        val fix = lastFix
        val declination = fix?.let { f ->
            val lat = f.latitude
            val lon = f.longitude
            if (lat == null || lon == null) null
            else SensorDataSource.declinationDegrees(lat, lon, f.ellipsoidAltitudeM ?: 0.0, System.currentTimeMillis())
        }
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
        return state.copy(
            compassMode = compassMode,
            mapMode = mapMode,
            headingDegrees = if (compassMode) trueHeading else null,
            headingText = parts.joinToString(" · ").ifEmpty { null },
            compassUnreliable = compassMode && h != null && h.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM,
            obstruction = if (mapMode) obstruction.cells else emptyList(),
            obstructionSamples = obstruction.totalSamples,
        )
    }

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
        val fix = lastFix
        val lat = fix?.latitude
        val lon = fix?.longitude
        val msl = fix?.let(::seaLevelAltitude)
        _positionState.value = PositionUiState(
            hasFix = lat != null && lon != null,
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
            scatter = scatter.stats(),
        )
    }

    private fun publishTrip() {
        _tripState.value = TripUiState(
            recording = tripRecording,
            stats = trip.stats,
            unit = speedUnit(),
            climbFromBarometer = hasBarometer,
            message = tripMessage,
        )
    }

    private fun publishReceiver() {
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
            navSilentMs = if (navFrames.isEmpty() && tracking) SystemClock.elapsedRealtime() - trackingStartedAtMs else 0L,
        )
    }

    // --- helpers -----------------------------------------------------------------------

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
        val wanted = compassMode && tracking && sensors.hasCompass
        if (wanted && headingJob == null) {
            headingJob = scope.launch {
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
        } else if (!wanted) {
            headingJob?.cancel()
            headingJob = null
            heading = null
        }
    }

    private fun suggestedDate(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private companion object {
        const val GGA_FRESH_MS = 3_000L
        const val GPS_EPOCH_MS = 315_964_800_000L
        const val WEEK_MS = 604_800_000L
    }
}
