// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import android.os.SystemClock
import io.github.leostumpf.positioninginfo.data.TtffLogStore
import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.ClockOffset
import io.github.leostumpf.positioninginfo.domain.FirstFixTimer
import io.github.leostumpf.positioninginfo.domain.TtffEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Time to first fix and clock offset for the GNSS page, and the log of first fixes kept on
 * the phone.
 *
 * Each start of tracking is a new receiver session with its own time to first fix. Only a
 * receiver that was really off gets its first fix logged, though: a glance at another app or
 * a rotation restarts the session within seconds, and logging each of those near-instant
 * "hot starts" would push the real measurements out of the log.
 *
 * @param onLogChanged called with the whole log after it was loaded or changed.
 */
internal class FirstFixSession(
    private val scope: CoroutineScope,
    private val store: TtffLogStore,
    private val onLogChanged: (List<TtffEntry>) -> Unit,
) {
    /** The first fixes logged so far, newest first. */
    var log = listOf<TtffEntry>()
        private set

    private var timer: FirstFixTimer? = null

    /** What the receiver held when this session started: hot, warm or cold. */
    private var startType: AlmanacReadiness? = null

    /** When the receiver was last released; null before the first start, or after a cold start. */
    private var releasedAtMs: Long? = null

    /** Whether this session's first fix goes into the log. */
    private var logThisFirstFix = true
    private var clockOffsetMs: Long? = null

    init {
        scope.launch {
            log = store.load()
            onLogChanged(log)
        }
    }

    /** Tracking started: a new session, timed from now. */
    fun onStart() {
        val now = SystemClock.elapsedRealtime()
        timer = FirstFixTimer(startedAtMs = now)
        startType = null
        logThisFirstFix = releasedAtMs.let { it == null || now - it >= MIN_RELEASE_FOR_TTFF_LOG_MS }
    }

    /** Tracking stopped: the receiver is released from now on. */
    fun onRelease() {
        releasedAtMs = SystemClock.elapsedRealtime()
    }

    /** The coming start is a deliberate cold start, which is exactly what the log is for. */
    fun forgetRelease() {
        releasedAtMs = null
    }

    fun onFix(fix: SpeedFix) {
        val hadFirstFix = timer?.hasFix == true
        timer = timer?.onFix(fix)
        val ttff = timer?.firstFixAfterMs
        if (!hadFirstFix && ttff != null && logThisFirstFix) logFirstFix(ttff)
        ClockOffset.of(fix, System.currentTimeMillis(), SystemClock.elapsedRealtime())?.let { clockOffsetMs = it }
    }

    /** The first report of the session tells how the receiver started: hot, warm or cold. */
    fun onSnapshot(snapshot: GnssSnapshot) {
        if (snapshot.hasReported && startType == null) startType = AlmanacStatus.from(snapshot).readiness
    }

    /** With location switched off the receiver is not searching, so the clock is held. */
    fun holdWhileDisabled(gpsEnabled: Boolean) {
        timer = timer?.holdWhileDisabled(nowMs = SystemClock.elapsedRealtime(), gpsEnabled = gpsEnabled)
    }

    /** The timing figures, with the phone's other clock offsets as the system reports them. */
    fun timing(networkOffsetMs: Long?, systemGnssOffsetMs: Long?): TimingUiState = TimingUiState(
        firstFixMs = timer?.firstFixAfterMs,
        searchingForMs = timer?.searchingForMs(SystemClock.elapsedRealtime()),
        clockOffsetMs = clockOffsetMs,
        networkOffsetMs = networkOffsetMs,
        systemGnssOffsetMs = systemGnssOffsetMs,
    )

    /** Deletes the log on the phone. */
    fun clearLog() {
        log = emptyList()
        scope.launch { store.clear() }
    }

    private fun logFirstFix(ttffMs: Long) {
        val entry = TtffEntry(System.currentTimeMillis(), ttffMs, startType ?: AlmanacReadiness.UNKNOWN)
        scope.launch {
            log = store.add(entry)
            onLogChanged(log)
        }
    }

    private companion object {
        /** A receiver released for less than this restarts hot; its first fix is not logged. */
        const val MIN_RELEASE_FOR_TTFF_LOG_MS = 60_000L
    }
}
