// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.SpeedFix

/**
 * Time to first fix for one receiver session.
 *
 * The GNSS screen predicts how long a fix should take from the orbital data held; this
 * measures how long it actually took, so the prediction can be checked against reality.
 *
 * A session starts whenever tracking starts. The receiver is released every time the app
 * leaves the foreground, so each return is a new session — and a short absence showing a
 * hot start of a second or two is exactly the right answer.
 *
 * Immutable, like [SessionStats]: every event returns a new instance.
 */
data class FirstFixTimer(
    /** Session start on the monotonic elapsed-realtime clock. */
    val startedAtMs: Long,
    /** Time to first fix, or null while still searching. */
    val firstFixAfterMs: Long? = null,
) {
    val hasFix: Boolean get() = firstFixAfterMs != null

    /**
     * Records a fix. Only the first live fix counts: the cached seed from the last known
     * location was not produced by this session, and later fixes say nothing about how
     * long the first one took.
     *
     * Measured to the fix's own timestamp rather than to its delivery, and clamped at zero
     * for a fix the receiver computed just before tracking formally began.
     */
    fun onFix(fix: SpeedFix): FirstFixTimer {
        if (hasFix || fix.isCached) return this
        return copy(firstFixAfterMs = (fix.elapsedRealtimeMs - startedAtMs).coerceAtLeast(0L))
    }

    /**
     * With location switched off the receiver is not searching at all, so the clock is held
     * at "now" — otherwise the time spent switched off would be charged to the receiver.
     */
    fun holdWhileDisabled(nowMs: Long, gpsEnabled: Boolean): FirstFixTimer =
        if (gpsEnabled || hasFix) this else copy(startedAtMs = nowMs)

    /** How long the receiver has been searching so far, or null once it has a fix. */
    fun searchingForMs(nowMs: Long): Long? =
        if (hasFix) null else (nowMs - startedAtMs).coerceAtLeast(0L)
}

/**
 * How far the phone's clock is from GNSS time, taken from one fix.
 *
 * Every GNSS fix carries UTC from the satellites, so comparing it with the phone's wall
 * clock shows how far the latter has drifted.
 */
object ClockOffset {

    /**
     * Receivers timestamp fixes coarsely, some to the whole second, so an offset below this
     * is reported as "in sync" rather than quoted with a precision it does not have.
     */
    const val SYNC_TOLERANCE_MS: Long = 1_000L

    /**
     * Phone clock minus GNSS time, in milliseconds: positive when the phone is ahead.
     *
     * The phone's wall time is reconstructed for the moment of the fix — current wall time
     * minus the fix's age on the monotonic clock — so the delay before the fix was delivered
     * does not show up as a clock error. Null for a cached fix or one without a UTC time.
     */
    fun of(fix: SpeedFix, nowWallMs: Long, nowElapsedMs: Long): Long? {
        if (fix.isCached) return null
        val gnssUtc = fix.utcTimeMs ?: return null
        val phoneWallAtFix = nowWallMs - (nowElapsedMs - fix.elapsedRealtimeMs)
        return phoneWallAtFix - gnssUtc
    }
}
