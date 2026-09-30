// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.background

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user has asked Positioning Info to keep running when it leaves the screen.
 *
 * Off by default: the app releases the receiver the moment it is no longer in front. When
 * switched on, [BackgroundTrackingService] runs as a location foreground service with a
 * permanent notification, which is what Android requires for an app to keep using
 * location while the user is elsewhere — without asking for "allow all the time".
 */
object BackgroundMode {

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal fun setActive(value: Boolean) {
        _active.value = value
    }

    /**
     * Must be called while the app is on screen: Android only lets a visible app start a
     * location service. Marked active at once rather than when the service comes up, so
     * leaving the app in between does not stop tracking under a notification that says it
     * runs. Returns false, and stays off, if Android refuses the start.
     */
    fun start(context: Context): Boolean {
        _active.value = true
        return try {
            ContextCompat.startForegroundService(context, Intent(context, BackgroundTrackingService::class.java))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the app is no longer in front.
            refused(e)
        } catch (e: SecurityException) {
            // No location access any more.
            refused(e)
        }
    }

    private fun refused(e: Exception): Boolean {
        Log.w("PositioningInfo", "Background mode could not start", e)
        _active.value = false
        return false
    }

    /** Stops the service and marks background mode off; safe to call from anywhere, even when not running. */
    fun stop(context: Context) {
        context.stopService(Intent(context, BackgroundTrackingService::class.java))
        _active.value = false
    }
}
