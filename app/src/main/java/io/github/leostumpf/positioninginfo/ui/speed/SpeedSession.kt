// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.speed

import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.FixFreshness
import io.github.leostumpf.positioninginfo.domain.SessionStats
import io.github.leostumpf.positioninginfo.domain.SpeedHistory
import io.github.leostumpf.positioninginfo.domain.SpeedResolver
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.settings.UnitPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The speed page: the live speed and how fresh it is, the maximum and average since the
 * last reset, the speed plot and the chosen unit.
 */
class SpeedSession internal constructor(
    private val scope: CoroutineScope,
    private val unitPreference: UnitPreference,
) {
    private val _state = MutableStateFlow(SpeedUiState())
    val state: StateFlow<SpeedUiState> = _state.asStateFlow()

    private var stats = SessionStats()

    /** Speed since the last reset, for the plot on the speed page; memory only. */
    private var history = SpeedHistory()

    init {
        scope.launch {
            unitPreference.unit.collect { unit -> _state.update { it.copy(unit = unit) } }
        }
    }

    fun setUnit(unit: SpeedUnit) {
        scope.launch { unitPreference.set(unit) }
    }

    /** Starts the maximum, average and plot afresh. */
    fun reset() {
        stats = SessionStats()
        history = SpeedHistory()
        _state.update { it.copy(maxMps = null, averageMps = null, speedHistory = emptyList()) }
    }

    /**
     * Resolves the speed from [fix] (or withdraws it once the fix has expired), adds it to
     * the statistics, and rebuilds the page if [publish].
     */
    internal fun update(fix: SpeedFix?, gpsEnabled: Boolean, publish: Boolean) {
        val reading = SpeedResolver.resolve(fix, SystemClock.elapsedRealtime())
        val speed = reading.speedMps
        if (reading.countsTowardsStats && fix != null && speed != null) {
            stats = stats.accept(speed.toDouble(), fix.elapsedRealtimeMs)
            history = history.add(fix.elapsedRealtimeMs, speed)
        }
        if (!publish) return
        val expired = reading.freshness == FixFreshness.EXPIRED
        _state.update {
            it.copy(
                speedMps = speed,
                freshness = reading.freshness,
                maxMps = if (stats.hasData) stats.maxMps else null,
                averageMps = stats.averageMps,
                // An expired fix's accuracy is withdrawn along with its speed: quoting a
                // figure from a reading we have just declared untrustworthy would undo the
                // point of withdrawing it.
                horizontalAccuracyM = if (expired) null else fix?.horizontalAccuracyM ?: it.horizontalAccuracyM,
                hasEverHadFix = it.hasEverHadFix || speed != null,
                gpsEnabled = gpsEnabled,
                speedAccuracyMps = if (expired) null else fix?.speedAccuracyMps,
                isMock = fix?.isMock == true,
                speedHistory = history.samples,
            )
        }
    }

    /** The satellite counts in the corner of the page. */
    internal fun onSnapshot(snapshot: GnssSnapshot) {
        _state.update {
            it.copy(satellitesUsed = snapshot.usedInFixCount, satellitesVisible = snapshot.visibleCount)
        }
    }
}
