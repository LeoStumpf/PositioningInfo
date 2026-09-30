// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.leostumpf.positioninginfo.background.BackgroundMode
import io.github.leostumpf.positioninginfo.data.AssistanceDataSource
import io.github.leostumpf.positioninginfo.data.CellInfoDataSource
import io.github.leostumpf.positioninginfo.data.DemoMode
import io.github.leostumpf.positioninginfo.data.GnssCapabilityDataSource
import io.github.leostumpf.positioninginfo.data.GnssStatusDataSource
import io.github.leostumpf.positioninginfo.data.LocationDataSource
import io.github.leostumpf.positioninginfo.data.LocationProviderDataSource
import io.github.leostumpf.positioninginfo.data.NetworkLocationDataSource
import io.github.leostumpf.positioninginfo.data.SystemStatusDataSource
import io.github.leostumpf.positioninginfo.data.TtffLogStore
import io.github.leostumpf.positioninginfo.data.WifiScanDataSource
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.domain.FixFreshness
import io.github.leostumpf.positioninginfo.domain.History
import io.github.leostumpf.positioninginfo.domain.HistorySample
import io.github.leostumpf.positioninginfo.domain.PositioningQuality
import io.github.leostumpf.positioninginfo.domain.SkyTracker
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.UpdateRate
import io.github.leostumpf.positioninginfo.settings.UnitPreference
import io.github.leostumpf.positioninginfo.ui.common.DataInventory
import io.github.leostumpf.positioninginfo.ui.gnss.FirstFixSession
import io.github.leostumpf.positioninginfo.ui.gnss.GnssUiState
import io.github.leostumpf.positioninginfo.ui.gnss.TimingUiState
import io.github.leostumpf.positioninginfo.ui.gnss.diagnosisInput
import io.github.leostumpf.positioninginfo.ui.network.NetworkSession
import io.github.leostumpf.positioninginfo.ui.network.NetworkUiState
import io.github.leostumpf.positioninginfo.ui.signal.SignalUiState
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.speed.SpeedSession
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Owns the one GNSS session the whole app shares.
 *
 * Every page is driven from a single receiver subscription rather than a ViewModel
 * each: registering the status callback twice would double the work for identical data,
 * and two independent subscriptions could momentarily disagree about what is overhead.
 */
class PositioningInfoViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Every coroutine of the session runs here. A platform callback or a file operation that
     * throws ends only its own stream — that card stops updating until the next start —
     * rather than taking the whole app down with it.
     */
    private val scope = viewModelScope + CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "A data stream failed and was stopped", e)
    }

    private val locationSource = LocationDataSource(application)
    private val gnssSource = GnssStatusDataSource(application)
    private val unitPreference = UnitPreference(application)
    private val assistanceSource = AssistanceDataSource(application)
    private val networkLocationSource = NetworkLocationDataSource(application)

    // "fused" is LocationManager.FUSED_PROVIDER, which only exists from Android 12; older
    // phones simply do not list it, and the source reports itself absent.
    private val fusedLocationSource = NetworkLocationDataSource(application, "fused")
    private val cellSource = CellInfoDataSource(application)
    private val wifiSource = WifiScanDataSource(application)
    private val facts = PhoneFacts(
        locationSource,
        networkLocationSource,
        fusedLocationSource,
        cellSource,
        wifiSource,
        SystemStatusDataSource(application),
        LocationProviderDataSource(application),
    )

    /** Static for the life of the device, so it is read once rather than streamed. */
    private val capabilities = GnssCapabilityDataSource(application).read()

    /** The speed page, and the unit every page shows speeds in. */
    val speed = SpeedSession(scope, unitPreference)

    private val _gnssState = MutableStateFlow(GnssUiState())
    val gnssState: StateFlow<GnssUiState> = _gnssState.asStateFlow()

    private val _signalState = MutableStateFlow(SignalUiState())
    val signalState: StateFlow<SignalUiState> = _signalState.asStateFlow()

    private val _skyState = MutableStateFlow(SkyUiState())
    val skyState: StateFlow<SkyUiState> = _skyState.asStateFlow()

    private val network = NetworkSession(
        scope,
        networkLocationSource,
        fusedLocationSource,
        cellSource,
        wifiSource,
        facts,
        gnssFix = { lastFix },
        visible = { uiVisible },
    )
    val networkState: StateFlow<NetworkUiState> = network.state

    private val firstFix = FirstFixSession(scope, TtffLogStore(application)) { log ->
        _gnssState.update { it.copy(ttffLog = log) }
    }

    private var lastSnapshot = GnssSnapshot.EMPTY

    /** Deliberately kept across stop/start, so the sky view's history survives backgrounding. */
    private var skyTracker = SkyTracker()

    private var lastFix: SpeedFix? = null
    private var updateRate = UpdateRate()

    /** Held in memory only; see [History]. */
    private var history = History()

    private var ticks = 0
    private var assistanceMessage: String? = null
    private val trackingJobs = mutableListOf<Job>()

    /** Position formats, altitude, accuracy test, trip, receiver internals, compass and signal map. */
    val analysis: AnalysisSession = AnalysisSession(
        application = application,
        scope = scope,
        capabilities = capabilities,
        speedUnit = { speed.state.value.unit },
        // Heading, compass, map and path toggles only change the decoration; the satellite
        // tracks underneath stay as built. In compass mode this runs at sensor rate.
        onSkyChanged = { if (uiVisible) _skyState.update { analysis.decorateSky(it) } },
    )

    /** Whether the app keeps running when it leaves the screen; see [BackgroundMode]. */
    val backgroundActive: StateFlow<Boolean> = BackgroundMode.active

    private var uiVisible = false

    /** What the app keeps right now, for the "Data on this phone" section. */
    val dataInventory: StateFlow<DataInventory> by lazy {
        combine(analysis.tripState, _gnssState, speed.state) { trip, gnss, speed ->
            DataInventory(
                tripPoints = trip.stats?.points ?: 0,
                firstFixEntries = gnss.ttffLog.size,
                unitChanged = speed.unit != SpeedUnit.DEFAULT,
                historySamples = gnss.history.size,
            )
        }.stateIn(scope, SharingStarted.Eagerly, DataInventory())
    }

    /**
     * Deletes everything stored and resets everything collected. The live readings carry
     * on, so the pages fill again from scratch.
     */
    fun clearAllData() {
        speed.reset()
        history = History()
        firstFix.clearLog()
        skyTracker = SkyTracker()
        analysis.clearAll()
        scope.launch { unitPreference.clear() }
        _gnssState.update { it.copy(history = emptyList(), ttffLog = emptyList()) }
        publishSky(SystemClock.elapsedRealtime())
    }

    init {
        // Stopped from the notification while the app is out of sight: release the receiver.
        scope.launch {
            BackgroundMode.active.collect { active -> if (!active && !uiVisible) stopTracking() }
        }
    }

    /** The app came to the front. */
    fun onUiStart() {
        uiVisible = true
        analysis.setVisible(true)
        startTracking()
        // Nothing was published while the app was out of sight; catch every page up at once.
        publishAll()
    }

    /**
     * The app left the screen. Tracking stops here unless background mode is on, in which
     * case the foreground service keeps the process and its location access alive.
     */
    fun onUiStop() {
        uiVisible = false
        analysis.setVisible(false)
        if (!BackgroundMode.active.value) stopTracking()
    }

    /** Starts or stops background mode, the foreground service that keeps tracking out of sight. */
    fun setBackgroundMode(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) BackgroundMode.start(context) else BackgroundMode.stop(context)
    }

    override fun onCleared() {
        // The screen is gone for good (the task was closed), so background mode ends with it.
        if (BackgroundMode.active.value) BackgroundMode.stop(getApplication())
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
    @SuppressLint("MissingPermission") // guarded by hasLocationPermission() immediately below
    private fun startTracking() {
        if (trackingJobs.isNotEmpty()) return
        if (!hasLocationPermission()) return
        facts.refreshGps()
        firstFix.onStart()

        val demo = DemoMode.source
        trackingJobs += scope.launch { (demo?.fixes() ?: locationSource.fixes()).collect(::onFix) }
        trackingJobs += scope.launch { (demo?.snapshots() ?: gnssSource.snapshots()).collect(::onSnapshot) }
        // Network positioning runs alongside GNSS for the comparison page.
        trackingJobs += network.start()
        trackingJobs += analysis.start()
        // A fix ages whether or not a new one arrives, so the reading has to be
        // re-evaluated on a timer as well as on new data — otherwise a lost signal would
        // leave the last number frozen on screen indefinitely.
        trackingJobs += scope.launch {
            while (true) {
                // Settings, providers and the system clocks change rarely; a few seconds is fresh enough.
                if (ticks++ % SLOW_TICKS == 0) readPhoneState()
                delay(FRESHNESS_TICK_MS)
                onTick()
            }
        }

        publishSpeed()
    }

    private fun onFix(fix: SpeedFix) {
        lastFix = fix
        if (!fix.isCached) updateRate = updateRate.onFix(fix.elapsedRealtimeMs)
        firstFix.onFix(fix)
        publishSpeed()
        analysis.onFix(fix)
    }

    /** Out of sight (background mode) only the statistics and the timer move on. */
    private fun onTick() {
        facts.refreshGps()
        val now = SystemClock.elapsedRealtime()
        skyTracker = skyTracker.onTick(now)
        publishSpeed()
        publishTiming()
        publishSky(now)
        network.publish()
        analysis.onTick()
    }

    private fun readPhoneState() {
        facts.refreshSlow()
        _gnssState.update { it.copy(settings = facts.phoneSettings) }
    }

    private fun stopTracking() {
        if (trackingJobs.isNotEmpty()) firstFix.onRelease()
        trackingJobs.forEach(Job::cancel)
        trackingJobs.clear()
        skyTracker = skyTracker.onPause()
    }

    /**
     * Wipes the receiver's aiding data and restarts it, so the next fix is a real cold
     * start. Restarting also begins a new session, so the time to first fix measures it.
     */
    fun coldStart() {
        val accepted = assistanceSource.clearAidingData()
        publishAssistance(if (accepted) "Aiding data cleared — cold start running" else "Command rejected by device")
        if (!accepted) return
        stopTracking()
        lastFix = null
        firstFix.forgetRelease()
        startTracking()
    }

    /**
     * Asks the receiver to download predicted orbits and inject the time; whether the phone
     * accepted the request shows on the GNSS page.
     */
    fun fetchAssistance() {
        publishAssistance(
            if (assistanceSource.injectAssistanceData()) {
                "Download requested — needs Wi-Fi or mobile data"
            } else {
                "Command rejected by device"
            },
        )
    }

    /** Starts the sky paths and the event list afresh, e.g. after fragments from earlier sessions. */
    fun clearSkyPaths() {
        skyTracker = SkyTracker()
        publishSky(SystemClock.elapsedRealtime())
    }

    private fun onSnapshot(snapshot: GnssSnapshot) {
        facts.refreshGps()
        lastSnapshot = snapshot
        firstFix.onSnapshot(snapshot)
        recordHistory(snapshot)
        analysis.onSnapshot(snapshot)
        val now = SystemClock.elapsedRealtime()
        skyTracker = skyTracker.onSnapshot(snapshot.satellites, now)
        publishGnss(now)
    }

    private fun recordHistory(snapshot: GnssSnapshot) {
        if (!snapshot.hasReported) return
        // One value per physical satellite: its strongest band.
        val heardCn0 = snapshot.satellites.filter { it.cn0DbHz > 0f }
            .groupBy { it.constellation to it.svid }.values.map { sigs -> sigs.maxOf { it.cn0DbHz } }
        val now = SystemClock.elapsedRealtime()
        val fresh = lastFix?.takeIf { !it.isCached && now - it.elapsedRealtimeMs < HISTORY_FIX_FRESH_MS }
        history = history.add(
            HistorySample(
                atMs = now,
                usedInFix = snapshot.usedInFixCount,
                heard = heardCn0.size,
                meanCn0 = heardCn0.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                accuracyM = fresh?.horizontalAccuracyM,
            ),
        )
    }

    // --- publishing: each page is rebuilt only while the app is on screen ------------------

    /** Every page at once, e.g. when the app returns from the background. */
    private fun publishAll() {
        publishSpeed()
        publishGnss(SystemClock.elapsedRealtime())
        publishTiming()
        network.publish()
    }

    private fun publishSpeed() {
        speed.update(lastFix, facts.gpsEnabled, publish = uiVisible)
        // The measured accuracy arrives with the fix rather than the satellite sweep, so
        // the signal screen is refreshed from here too.
        publishSignal()
    }

    private fun publishAssistance(message: String) {
        assistanceMessage = message
        _gnssState.update { it.copy(assistanceMessage = message) }
    }

    private fun publishGnss(now: Long) {
        if (!uiVisible) return
        val snapshot = lastSnapshot
        speed.onSnapshot(snapshot)
        _gnssState.value = analysis.decorateGnss(
            GnssUiState.from(
                status = AlmanacStatus.from(snapshot),
                satellites = snapshot.satellites,
                gpsEnabled = facts.gpsEnabled,
                timing = currentTiming(),
                assistanceMessage = assistanceMessage,
            ),
        ).copy(history = history.samples, ttffLog = firstFix.log, settings = facts.phoneSettings)
        publishSignal()
        publishDiagnosis()
        publishSky(now)
    }

    /** Re-run on every sweep and tick, so "searching for" and the settings stay current. */
    private fun publishDiagnosis() {
        if (!uiVisible) return
        val timing = currentTiming()
        val diagnosis = FixDiagnosis.evaluate(
            diagnosisInput(
                snapshot = lastSnapshot,
                fix = lastFix,
                gpsEnabled = facts.gpsEnabled,
                powerSave = facts.powerSave,
                airplaneMode = facts.airplaneMode,
                dataConnection = facts.dataConnection,
                searchingMs = timing.searchingForMs,
                firstFixMs = timing.firstFixMs,
            ),
        )
        _gnssState.update { it.copy(diagnosis = diagnosis) }
    }

    private fun publishSky(nowMs: Long) {
        if (!uiVisible) return
        _skyState.value = analysis.decorateSky(SkyUiState.from(skyTracker, nowMs))
    }

    private fun publishSignal() {
        if (!uiVisible) return
        val speedState = speed.state.value
        val fix = lastFix?.takeIf { speedState.freshness != FixFreshness.EXPIRED }
        _signalState.value = analysis.decorateSignal(
            SignalUiState.from(
                quality = PositioningQuality.from(lastSnapshot),
                measuredAccuracyM = speedState.horizontalAccuracyM,
            ).copy(
                verticalAccuracyM = fix?.verticalAccuracyM,
                speedAccuracyMps = fix?.speedAccuracyMps,
                bearingAccuracyDeg = fix?.bearingAccuracyDeg,
                updateIntervalMs = updateRate.meanIntervalMs,
                timeUncertaintyMs = fix?.timeUncertaintyMs,
                isMock = fix?.isMock == true,
            ),
        )
    }

    /** Re-evaluated on the ticker, so "searching…" counts up while nothing else changes. */
    private fun publishTiming() {
        firstFix.holdWhileDisabled(facts.gpsEnabled)
        if (!uiVisible) return
        // No satellite sweeps arrive while location is off, so the switch is picked up here.
        _gnssState.update { it.copy(timing = currentTiming(), gpsEnabled = facts.gpsEnabled) }
        publishDiagnosis()
    }

    private fun currentTiming(): TimingUiState = firstFix.timing(facts.networkOffsetMs, facts.systemGnssOffsetMs)

    private fun hasLocationPermission(): Boolean = ContextCompat.checkSelfPermission(
        getApplication(),
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PositioningInfo"
        const val FRESHNESS_TICK_MS = 500L
        const val SLOW_TICKS = 10

        /** A fix older than this is not counted as the accuracy of a history sample. */
        const val HISTORY_FIX_FRESH_MS = 5_000L
    }
}
