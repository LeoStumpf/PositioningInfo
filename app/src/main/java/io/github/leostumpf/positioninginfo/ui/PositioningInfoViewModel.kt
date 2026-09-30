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
import io.github.leostumpf.positioninginfo.data.GnssCapabilityDataSource
import io.github.leostumpf.positioninginfo.data.GnssStatusDataSource
import io.github.leostumpf.positioninginfo.data.LocationProviderDataSource
import io.github.leostumpf.positioninginfo.data.LocationDataSource
import io.github.leostumpf.positioninginfo.data.NetworkLocationDataSource
import io.github.leostumpf.positioninginfo.data.SystemStatusDataSource
import io.github.leostumpf.positioninginfo.data.TtffLogStore
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.History
import io.github.leostumpf.positioninginfo.domain.HistorySample
import io.github.leostumpf.positioninginfo.domain.TtffEntry
import io.github.leostumpf.positioninginfo.data.WifiScanDataSource
import io.github.leostumpf.positioninginfo.data.model.countSatellites
import io.github.leostumpf.positioninginfo.domain.DiagnosisInput
import io.github.leostumpf.positioninginfo.domain.DopCalculator
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.domain.SkyPoint
import io.github.leostumpf.positioninginfo.data.model.AccessPoint
import io.github.leostumpf.positioninginfo.data.model.CellTower
import io.github.leostumpf.positioninginfo.data.model.NetworkFix
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.domain.LocationProviderInfo
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.ClockOffset
import io.github.leostumpf.positioninginfo.domain.FirstFixTimer
import io.github.leostumpf.positioninginfo.domain.FixFreshness
import io.github.leostumpf.positioninginfo.domain.SessionStats
import io.github.leostumpf.positioninginfo.domain.SkyTracker
import io.github.leostumpf.positioninginfo.domain.SpeedReading
import io.github.leostumpf.positioninginfo.domain.SpeedHistory
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.UpdateRate
import io.github.leostumpf.positioninginfo.domain.PositioningQuality
import io.github.leostumpf.positioninginfo.domain.SpeedResolver
import io.github.leostumpf.positioninginfo.settings.UnitPreference
import io.github.leostumpf.positioninginfo.ui.gnss.GnssUiState
import io.github.leostumpf.positioninginfo.ui.gnss.TimingUiState
import io.github.leostumpf.positioninginfo.ui.network.NetworkUiState
import io.github.leostumpf.positioninginfo.ui.network.SourceRow
import io.github.leostumpf.positioninginfo.domain.NetworkComparison
import io.github.leostumpf.positioninginfo.ui.signal.SignalUiState
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.speed.SpeedUiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import io.github.leostumpf.positioninginfo.ui.common.DataInventory
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * Owns the one GNSS session the whole app shares.
 *
 * Both screens are driven from a single receiver subscription rather than a ViewModel
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
    private val capabilitySource = GnssCapabilityDataSource(application)
    private val assistanceSource = AssistanceDataSource(application)
    private val networkLocationSource = NetworkLocationDataSource(application)
    // "fused" is LocationManager.FUSED_PROVIDER, which only exists from Android 12; older
    // phones simply do not list it, and the source reports itself absent.
    private val fusedLocationSource = NetworkLocationDataSource(application, "fused")
    private val cellSource = CellInfoDataSource(application)
    private val wifiSource = WifiScanDataSource(application)
    private val systemStatus = SystemStatusDataSource(application)
    private val ttffStore = TtffLogStore(application)
    private val providerSource = LocationProviderDataSource(application)

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
    private var fusedFix: NetworkFix? = null
    private var cells: List<CellTower> = emptyList()
    private var accessPoints: List<AccessPoint> = emptyList()

    /** Deliberately kept across stop/start, so the sky view's history survives backgrounding. */
    private var skyTracker = SkyTracker()

    private var stats = SessionStats()
    private var lastFix: SpeedFix? = null
    private var updateRate = UpdateRate()
    /** Speed since the last reset, for the plot on the speed page; memory only. */
    private var speedHistory = SpeedHistory()
    /** Held in memory only; see [History]. */
    private var history = History()
    private var ttffLog = listOf<TtffEntry>()
    /** What the receiver held when this session started: hot, warm or cold. */
    private var sessionStartType: AlmanacReadiness? = null
    private var firstFixTimer: FirstFixTimer? = null
    /** When the receiver was last released; null before the first start, or after a cold start. */
    private var releasedAtMs: Long? = null
    /** Whether this session's first fix goes into the log; see [startTracking]. */
    private var logThisFirstFix = true
    private var clockOffsetMs: Long? = null
    private var networkOffsetMs: Long? = null
    private var systemGnssOffsetMs: Long? = null
    private var phoneSettings = PhoneSettings()
    private var providers = listOf<LocationProviderInfo>()
    private var ticks = 0
    private var assistanceMessage: String? = null
    private val trackingJobs = mutableListOf<Job>()

    /** Position formats, altitude, accuracy test, trip, receiver internals, compass and signal map. */
    val analysis = AnalysisSession(
        application = application,
        scope = scope,
        capabilities = capabilities,
        speedUnit = { _speedState.value.unit },
        onSkyChanged = { publishSky(SystemClock.elapsedRealtime()) },
    )

    /** Whether the app keeps running when it leaves the screen; see [BackgroundMode]. */
    val backgroundActive: StateFlow<Boolean> = BackgroundMode.active

    private var uiVisible = false

    /** What the app keeps right now, for the "Data on this phone" section. */
    val dataInventory: StateFlow<DataInventory> by lazy {
        combine(analysis.tripState, _gnssState, _speedState) { trip, gnss, speed ->
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
        resetSession()
        history = History()
        ttffLog = emptyList()
        skyTracker = SkyTracker()
        analysis.clearAll()
        scope.launch {
            ttffStore.clear()
            unitPreference.clear()
        }
        _gnssState.update { it.copy(history = emptyList(), ttffLog = emptyList()) }
        publishSky(SystemClock.elapsedRealtime())
    }

    init {
        scope.launch {
            ttffLog = ttffStore.load()
            _gnssState.update { it.copy(ttffLog = ttffLog) }
        }
        scope.launch {
            unitPreference.unit.collect { unit -> _speedState.update { it.copy(unit = unit) } }
        }
        // Stopped from the notification while the app is out of sight: release the receiver.
        scope.launch {
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

        // Each start is a new receiver session, so it gets its own time to first fix. Only
        // a receiver that was really off gets its first fix logged, though: a glance at
        // another app or a rotation restarts the session within seconds, and logging each
        // of those near-instant "hot starts" would push the real measurements out of the log.
        val startedAt = SystemClock.elapsedRealtime()
        firstFixTimer = FirstFixTimer(startedAtMs = startedAt)
        sessionStartType = null
        logThisFirstFix = releasedAtMs.let { it == null || startedAt - it >= MIN_RELEASE_FOR_TTFF_LOG_MS }

        trackingJobs += scope.launch {
            locationSource.fixes().collect { fix ->
                lastFix = fix
                if (!fix.isCached) updateRate = updateRate.onFix(fix.elapsedRealtimeMs)
                val hadFirstFix = firstFixTimer?.hasFix == true
                firstFixTimer = firstFixTimer?.onFix(fix)
                val ttff = firstFixTimer?.firstFixAfterMs
                if (!hadFirstFix && ttff != null && logThisFirstFix) logFirstFix(ttff)
                ClockOffset.of(fix, System.currentTimeMillis(), SystemClock.elapsedRealtime())
                    ?.let { clockOffsetMs = it }
                publishSpeed(fix)
                analysis.onFix(fix)
            }
        }
        trackingJobs += scope.launch {
            gnssSource.snapshots().collect(::publishGnss)
        }
        // Network positioning runs alongside GNSS for the comparison page. All three are
        // cheap: the network provider does one lookup every few seconds, and the cell and
        // Wi-Fi readings mostly return what the radios already know.
        trackingJobs += scope.launch {
            networkLocationSource.fixes().collect { networkFix = it; publishNetwork() }
        }
        if (fusedLocationSource.exists) {
            trackingJobs += scope.launch {
                fusedLocationSource.fixes().collect { fusedFix = it; publishNetwork() }
            }
        }
        if (cellSource.hasTelephony) {
            trackingJobs += scope.launch {
                cellSource.cells().collect { cells = it; publishNetwork() }
            }
        }
        trackingJobs += scope.launch {
            wifiSource.accessPoints().collect { accessPoints = it; publishNetwork() }
        }
        trackingJobs += analysis.start()
        // A fix ages whether or not a new one arrives, so the reading has to be
        // re-evaluated on a timer as well as on new data — otherwise a lost signal would
        // leave the last number frozen on screen indefinitely.
        trackingJobs += scope.launch {
            while (true) {
                // Settings, providers and the system clocks change rarely; a few seconds is fresh enough.
                if (ticks++ % SLOW_TICKS == 0) readPhoneState()
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

    private fun readPhoneState() {
        phoneSettings = systemStatus.settings()
        providers = providerSource.read()
        networkOffsetMs = systemStatus.networkTimeOffsetMs()
        systemGnssOffsetMs = systemStatus.gnssTimeOffsetMs()
        _gnssState.update { it.copy(settings = phoneSettings) }
    }

    fun stopTracking() {
        if (trackingJobs.isNotEmpty()) releasedAtMs = SystemClock.elapsedRealtime()
        trackingJobs.forEach(Job::cancel)
        trackingJobs.clear()
        skyTracker = skyTracker.onPause()
    }

    fun setUnit(unit: SpeedUnit) {
        scope.launch { unitPreference.set(unit) }
    }

    fun resetSession() {
        stats = SessionStats()
        speedHistory = SpeedHistory()
        _speedState.update { it.copy(maxMps = null, averageMps = null, speedHistory = emptyList()) }
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
        releasedAtMs = null  // a cold start is exactly what the log is for
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
            speedHistory = speedHistory.add(fix.elapsedRealtimeMs, reading.speedMps)
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
                speedAccuracyMps = if (reading.freshness == FixFreshness.EXPIRED) null else fix?.speedAccuracyMps,
                isMock = fix?.isMock == true,
                speedHistory = speedHistory.samples,
            )
        }

        // The measured accuracy arrives with the fix rather than the satellite sweep, so
        // the signal screen is refreshed from here too.
        publishSignal()
    }

    private fun publishGnss(snapshot: GnssSnapshot) {
        val enabled = locationSource.isGpsEnabled
        lastSnapshot = snapshot
        recordHistory(snapshot)

        _speedState.update {
            it.copy(
                satellitesUsed = snapshot.usedInFixCount,
                satellitesVisible = snapshot.visibleCount,
            )
        }
        _gnssState.value = analysis.decorateGnss(GnssUiState.from(
            status = AlmanacStatus.from(snapshot),
            satellites = snapshot.satellites,
            gpsEnabled = enabled,
            timing = currentTiming(),
            assistanceMessage = assistanceMessage,
        )).copy(history = history.samples, ttffLog = ttffLog, settings = phoneSettings)
        publishSignal()

        publishDiagnosis()
        analysis.onSnapshot(snapshot)
        val now = SystemClock.elapsedRealtime()
        skyTracker = skyTracker.onSnapshot(snapshot.satellites, now)
        publishSky(now)
    }

    /** The same moment seen by the receiver, the network provider and Android's fused provider. */
    private fun positionSources(): List<SourceRow> {
        val now = SystemClock.elapsedRealtime()
        val gnss = lastFix?.takeIf { !it.isCached && it.latitude != null }
        fun offset(lat: Double, lon: Double) = gnss?.let { NetworkComparison.distanceM(it.latitude!!, it.longitude!!, lat, lon) }
        fun row(name: String, description: String, source: NetworkLocationDataSource, fix: NetworkFix?) = SourceRow(
            name = name,
            description = description,
            available = source.exists && source.isEnabled,
            accuracyM = fix?.accuracyM,
            ageMs = fix?.let { now - it.elapsedRealtimeMs },
            offsetM = fix?.let { offset(it.latitude, it.longitude) },
            isMock = fix?.isMock == true,
        )
        return listOf(
            SourceRow(
                name = "GNSS receiver",
                description = "satellites only; the reference",
                available = locationSource.isGpsEnabled,
                accuracyM = gnss?.horizontalAccuracyM,
                ageMs = gnss?.let { now - it.elapsedRealtimeMs },
                offsetM = null,
                isReference = true,
                isMock = gnss?.isMock == true,
            ),
            row("Network", "Wi-Fi and cell towers", networkLocationSource, networkFix),
            row("Fused", "Android's blend of all sources — what most apps show", fusedLocationSource, fusedFix),
        )
    }

    private fun recordHistory(snapshot: GnssSnapshot) {
        if (!snapshot.hasReported) return
        if (sessionStartType == null) sessionStartType = AlmanacStatus.from(snapshot).readiness
        // One value per physical satellite: its strongest band.
        val heardCn0 = snapshot.satellites.filter { it.cn0DbHz > 0f }
            .groupBy { it.constellation to it.svid }.values.map { sigs -> sigs.maxOf { it.cn0DbHz } }
        val fresh = lastFix?.takeIf { !it.isCached && SystemClock.elapsedRealtime() - it.elapsedRealtimeMs < 5_000L }
        history = history.add(
            HistorySample(
                atMs = SystemClock.elapsedRealtime(),
                usedInFix = snapshot.usedInFixCount,
                heard = heardCn0.size,
                meanCn0 = heardCn0.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                accuracyM = fresh?.horizontalAccuracyM,
            ),
        )
    }

    private fun logFirstFix(ttffMs: Long) {
        val entry = TtffEntry(System.currentTimeMillis(), ttffMs, sessionStartType ?: AlmanacReadiness.UNKNOWN)
        scope.launch {
            ttffLog = ttffStore.add(entry)
            _gnssState.update { it.copy(ttffLog = ttffLog) }
        }
    }

    /** Re-run on every sweep and tick, so "searching for" and the settings stay current. */
    private fun publishDiagnosis() {
        val sats = lastSnapshot.satellites
        val status = AlmanacStatus.from(lastSnapshot)
        val used = sats.filter { it.usedInFix && !(it.azimuthDegrees == 0f && it.elevationDegrees == 0f) }
            .distinctBy { it.constellation to it.svid }
        val timing = currentTiming()
        val diagnosis = FixDiagnosis.evaluate(
            DiagnosisInput(
                gpsEnabled = locationSource.isGpsEnabled,
                isMock = lastFix?.isMock == true,
                powerSave = systemStatus.powerSaveLocation,
                airplaneMode = systemStatus.airplaneMode,
                dataConnection = systemStatus.dataConnection,
                satellitesHeard = sats.countSatellites { it.cn0DbHz > 0f },
                satellitesStrong = sats.countSatellites { it.cn0DbHz >= DiagnosisInput.STRONG_CN0 },
                usedInFix = lastSnapshot.usedInFixCount,
                readiness = status.readiness,
                withEphemeris = status.withEphemeris,
                pdop = DopCalculator.of(used.map { SkyPoint(it.azimuthDegrees, it.elevationDegrees) })?.pdop,
                searchingMs = timing.searchingForMs,
                firstFixMs = timing.firstFixMs,
            ),
        )
        _gnssState.update { it.copy(diagnosis = diagnosis) }
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
        ).copy(sources = positionSources(), providers = providers)
    }

    /** Starts the sky paths and the event list afresh, e.g. after fragments from earlier sessions. */
    fun clearSkyPaths() {
        skyTracker = SkyTracker()
        publishSky(SystemClock.elapsedRealtime())
    }

    private fun publishSky(nowMs: Long) {
        _skyState.value = analysis.decorateSky(SkyUiState.from(skyTracker, nowMs))
    }

    private fun publishSignal() {
        val fix = lastFix?.takeIf { _speedState.value.freshness != FixFreshness.EXPIRED }
        _signalState.value = analysis.decorateSignal(
            SignalUiState.from(
                quality = PositioningQuality.from(lastSnapshot),
                measuredAccuracyM = _speedState.value.horizontalAccuracyM,
                capabilities = capabilities,
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
        val enabled = locationSource.isGpsEnabled
        firstFixTimer = firstFixTimer?.holdWhileDisabled(
            nowMs = SystemClock.elapsedRealtime(),
            gpsEnabled = enabled,
        )
        // No satellite sweeps arrive while location is off, so the switch is picked up here.
        _gnssState.update { it.copy(timing = currentTiming(), gpsEnabled = enabled) }
        publishDiagnosis()
    }

    private fun currentTiming(): TimingUiState {
        val timer = firstFixTimer
        return TimingUiState(
            firstFixMs = timer?.firstFixAfterMs,
            searchingForMs = timer?.searchingForMs(SystemClock.elapsedRealtime()),
            clockOffsetMs = clockOffsetMs,
            networkOffsetMs = networkOffsetMs,
            systemGnssOffsetMs = systemGnssOffsetMs,
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PositioningInfo"
        const val FRESHNESS_TICK_MS = 500L
        const val SLOW_TICKS = 10
        /** A receiver released for less than this restarts hot; its first fix is not logged. */
        const val MIN_RELEASE_FOR_TTFF_LOG_MS = 60_000L
    }
}
