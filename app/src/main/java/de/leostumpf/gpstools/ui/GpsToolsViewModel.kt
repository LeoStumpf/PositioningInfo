// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.leostumpf.gpstools.background.BackgroundMode
import de.leostumpf.gpstools.data.AssistanceDataSource
import de.leostumpf.gpstools.data.CellInfoDataSource
import de.leostumpf.gpstools.data.GnssCapabilityDataSource
import de.leostumpf.gpstools.data.GnssStatusDataSource
import de.leostumpf.gpstools.data.LocationDataSource
import de.leostumpf.gpstools.data.NetworkLocationDataSource
import de.leostumpf.gpstools.data.WifiScanDataSource
import de.leostumpf.gpstools.data.model.AccessPoint
import de.leostumpf.gpstools.data.model.CellTower
import de.leostumpf.gpstools.data.model.NetworkFix
import de.leostumpf.gpstools.data.model.GnssSnapshot
import de.leostumpf.gpstools.data.model.SpeedFix
import de.leostumpf.gpstools.domain.AlmanacStatus
import de.leostumpf.gpstools.domain.ClockOffset
import de.leostumpf.gpstools.domain.FirstFixTimer
import de.leostumpf.gpstools.domain.FixFreshness
import de.leostumpf.gpstools.domain.SessionStats
import de.leostumpf.gpstools.domain.SkyTracker
import de.leostumpf.gpstools.domain.SpeedReading
import de.leostumpf.gpstools.domain.PositioningQuality
import de.leostumpf.gpstools.domain.SpeedResolver
import de.leostumpf.gpstools.settings.UnitPreference
import de.leostumpf.gpstools.ui.gnss.GnssUiState
import de.leostumpf.gpstools.ui.gnss.TimingUiState
import de.leostumpf.gpstools.ui.network.NetworkUiState
import de.leostumpf.gpstools.ui.signal.SignalUiState
import de.leostumpf.gpstools.ui.sky.SkyUiState
import de.leostumpf.gpstools.ui.speed.SpeedUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the one GNSS session the whole app shares.
 *
 * Both screens are driven from a single receiver subscription rather than a ViewModel
 * each: registering the status callback twice would double the work for identical data,
 * and two independent subscriptions could momentarily disagree about what is overhead.
 */
class GpsToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val locationSource = LocationDataSource(application)
    private val gnssSource = GnssStatusDataSource(application)
    private val unitPreference = UnitPreference(application)
    private val capabilitySource = GnssCapabilityDataSource(application)
    private val assistanceSource = AssistanceDataSource(application)
    private val networkLocationSource = NetworkLocationDataSource(application)
    private val cellSource = CellInfoDataSource(application)
    private val wifiSource = WifiScanDataSource(application)

    /** Static for the life of the device, so it is read once rather than streamed. */
    private val capabilities = capabilitySource.read()

    private val _speedState = MutableStateFlow(SpeedUiState())
    val speedState: StateFlow<SpeedUiState> = _speedState.asStateFlow()

    private val _gnssState = MutableStateFlow(GnssUiState())
    val gnssState: StateFlow<GnssUiState> = _gnssState.asStateFlow()

    private val _signalState = MutableStateFlow(SignalUiState(capabilities = capabilities))
    val signalState: StateFlow<SignalUiState> = _signalState.asStateFlow()

    private val _skyState = MutableStateFlow(SkyUiState())
    val skyState: StateFlow<SkyUiState> = _skyState.asStateFlow()

    private val _networkState = MutableStateFlow(NetworkUiState())
    val networkState: StateFlow<NetworkUiState> = _networkState.asStateFlow()

    private var lastSnapshot = GnssSnapshot.EMPTY

    private var networkFix: NetworkFix? = null
    private var cells: List<CellTower> = emptyList()
    private var accessPoints: List<AccessPoint> = emptyList()

    /** Deliberately kept across stop/start, so the sky view's history survives backgrounding. */
    private var skyTracker = SkyTracker()

    private var stats = SessionStats()
    private var lastFix: SpeedFix? = null
    private var firstFixTimer: FirstFixTimer? = null
    private var clockOffsetMs: Long? = null
    private var assistanceMessage: String? = null
    private val trackingJobs = mutableListOf<Job>()

    /** Position formats, altitude, accuracy test, trip, receiver internals, compass and signal map. */
    val analysis = AnalysisSession(
        application = application,
        scope = viewModelScope,
        speedUnit = { _speedState.value.unit },
        onSkyChanged = { publishSky(SystemClock.elapsedRealtime()) },
    )

    /** Whether the app keeps running when it leaves the screen; see [BackgroundMode]. */
    val backgroundActive: StateFlow<Boolean> = BackgroundMode.active

    private var uiVisible = false

    init {
        viewModelScope.launch {
            unitPreference.unit.collect { unit -> _speedState.update { it.copy(unit = unit) } }
        }
        // Stopped from the notification while the app is out of sight: release the receiver.
        viewModelScope.launch {
            BackgroundMode.active.collect { active -> if (!active && !uiVisible) stopTracking() }
        }
    }

    /** The app came to the front. */
    fun onUiStart() {
        uiVisible = true
        startTracking()
    }

    /**
     * The app left the screen. Tracking stops here unless background mode is on, in which
     * case the foreground service keeps the process and its location access alive.
     */
    fun onUiStop() {
        uiVisible = false
        if (!BackgroundMode.active.value) stopTracking()
    }

    fun setBackgroundMode(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) BackgroundMode.start(context) else BackgroundMode.stop(context)
    }

    override fun onCleared() {
        // The screen is gone for good (the task was closed), so background mode ends with it.
        if (BackgroundMode.active.value) BackgroundMode.stop(getApplication())
        super.onCleared()
    }

    /**
     * Starts consuming GNSS updates. Called on every STARTED lifecycle pass; tracking stops
     * when the app is backgrounded unless the user has switched background mode on, so the
     * app never needs the "allow all the time" location permission.
     *
     * The permission is re-checked here rather than trusted from the caller: it can be
     * revoked from the notification shade while the app sits in the background, and the
     * next ON_START would otherwise register a listener the app is no longer allowed.
     */
    @SuppressLint("MissingPermission")  // guarded by hasLocationPermission() immediately below
    fun startTracking() {
        if (trackingJobs.isNotEmpty()) return
        if (!hasLocationPermission()) return

        // Each start is a new receiver session, so it gets its own time to first fix.
        firstFixTimer = FirstFixTimer(startedAtMs = SystemClock.elapsedRealtime())

        trackingJobs += viewModelScope.launch {
            locationSource.fixes().collect { fix ->
                lastFix = fix
                firstFixTimer = firstFixTimer?.onFix(fix)
                ClockOffset.of(fix, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                    ?.let { clockOffsetMs = it }
                publishSpeed(fix)
                analysis.onFix(fix)
            }
        }
        trackingJobs += viewModelScope.launch {
            gnssSource.snapshots().collect(::publishGnss)
        }
        // Network positioning runs alongside GNSS for the comparison page. All three are
        // cheap: the network provider does one lookup every few seconds, and the cell and
        // Wi-Fi readings mostly return what the radios already know.
        trackingJobs += viewModelScope.launch {
            networkLocationSource.fixes().collect { networkFix = it; publishNetwork() }
        }
        trackingJobs += viewModelScope.launch {
            cellSource.cells().collect { cells = it; publishNetwork() }
        }
        trackingJobs += viewModelScope.launch {
            wifiSource.accessPoints().collect { accessPoints = it; publishNetwork() }
        }
        trackingJobs += analysis.start()
        // A fix ages whether or not a new one arrives, so the reading has to be
        // re-evaluated on a timer as well as on new data — otherwise a lost signal would
        // leave the last number frozen on screen indefinitely.
        trackingJobs += viewModelScope.launch {
            while (true) {
                delay(FRESHNESS_TICK_MS)
                publishSpeed(lastFix)
                publishTiming()
                val now = SystemClock.elapsedRealtime()
                skyTracker = skyTracker.onTick(now)
                publishSky(now)
                publishNetwork()
                analysis.onTick()
            }
        }

        publishSpeed(lastFix)
    }

    fun stopTracking() {
        trackingJobs.forEach(Job::cancel)
        trackingJobs.clear()
        skyTracker = skyTracker.onPause()
    }

    fun cycleUnit() {
        viewModelScope.launch { unitPreference.set(_speedState.value.unit.next()) }
    }

    fun resetSession() {
        stats = SessionStats()
        _speedState.update { it.copy(maxMps = null, averageMps = null) }
    }

    /**
     * Wipes the receiver's aiding data and restarts it, so the next fix is a real cold
     * start. Restarting also begins a new session, so the time to first fix measures it.
     */
    fun coldStart() {
        val accepted = assistanceSource.clearAidingData()
        publishAssistance(
            if (accepted) "Aiding data cleared — cold start running" else "Command rejected by device",
        )
        if (!accepted) return
        stopTracking()
        lastFix = null
        startTracking()
    }

    fun fetchAssistance() {
        publishAssistance(
            if (assistanceSource.injectAssistanceData()) {
                "Download requested — needs Wi-Fi or mobile data"
            } else {
                "Command rejected by device"
            },
        )
    }

    private fun publishAssistance(message: String) {
        assistanceMessage = message
        _gnssState.update { it.copy(assistanceMessage = message) }
    }

    private fun publishSpeed(fix: SpeedFix?) {
        val reading: SpeedReading = SpeedResolver.resolve(fix, SystemClock.elapsedRealtime())

        if (reading.countsTowardsStats && fix != null) {
            stats = stats.accept(reading.speedMps!!.toDouble(), fix.elapsedRealtimeMs)
        }

        _speedState.update {
            it.copy(
                speedMps = reading.speedMps,
                freshness = reading.freshness,
                maxMps = if (stats.hasData) stats.maxMps else null,
                averageMps = stats.averageMps,
                // An expired fix's accuracy is withdrawn along with its speed: quoting a
                // figure from a reading we have just declared untrustworthy would undo the
                // point of withdrawing it.
                horizontalAccuracyM = if (reading.freshness == FixFreshness.EXPIRED) {
                    null
                } else {
                    fix?.horizontalAccuracyM ?: it.horizontalAccuracyM
                },
                hasEverHadFix = it.hasEverHadFix || reading.speedMps != null,
                gpsEnabled = locationSource.isGpsEnabled,
            )
        }

        // The measured accuracy arrives with the fix rather than the satellite sweep, so
        // the signal screen is refreshed from here too.
        publishSignal()
    }

    private fun publishGnss(snapshot: GnssSnapshot) {
        val enabled = locationSource.isGpsEnabled
        lastSnapshot = snapshot

        _speedState.update {
            it.copy(
                satellitesUsed = snapshot.usedInFixCount,
                satellitesVisible = snapshot.visibleCount,
            )
        }
        _gnssState.value = GnssUiState.from(
            status = AlmanacStatus.from(snapshot),
            satellites = snapshot.satellites,
            gpsEnabled = enabled,
            timing = currentTiming(),
            assistanceMessage = assistanceMessage,
        )
        publishSignal()

        analysis.onSnapshot(snapshot)
        val now = SystemClock.elapsedRealtime()
        skyTracker = skyTracker.onSnapshot(snapshot.satellites, now)
        publishSky(now)
    }

    private fun publishNetwork() {
        _networkState.value = NetworkUiState.from(
            providerEnabled = networkLocationSource.isEnabled,
            fix = networkFix,
            gnss = lastFix,
            nowMs = SystemClock.elapsedRealtime(),
            hasTelephony = cellSource.hasTelephony,
            cells = cells,
            wifiAvailable = wifiSource.canScan,
            accessPoints = accessPoints,
        )
    }

    private fun publishSky(nowMs: Long) {
        _skyState.value = analysis.decorateSky(SkyUiState.from(skyTracker, nowMs))
    }

    private fun publishSignal() {
        _signalState.value = analysis.decorateSignal(
            SignalUiState.from(
                quality = PositioningQuality.from(lastSnapshot),
                measuredAccuracyM = _speedState.value.horizontalAccuracyM,
                capabilities = capabilities,
            ),
        )
    }

    /** Re-evaluated on the ticker, so "searching…" counts up while nothing else changes. */
    private fun publishTiming() {
        val enabled = locationSource.isGpsEnabled
        firstFixTimer = firstFixTimer?.holdWhileDisabled(
            nowMs = SystemClock.elapsedRealtime(),
            gpsEnabled = enabled,
        )
        // No satellite sweeps arrive while location is off, so the switch is picked up here.
        _gnssState.update { it.copy(timing = currentTiming(), gpsEnabled = enabled) }
    }

    private fun currentTiming(): TimingUiState {
        val timer = firstFixTimer
        return TimingUiState(
            firstFixMs = timer?.firstFixAfterMs,
            searchingForMs = timer?.searchingForMs(SystemClock.elapsedRealtime()),
            clockOffsetMs = clockOffsetMs,
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FRESHNESS_TICK_MS = 500L
    }
}
