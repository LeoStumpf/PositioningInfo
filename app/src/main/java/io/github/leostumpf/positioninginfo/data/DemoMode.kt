// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import io.github.leostumpf.positioninginfo.data.model.GnssSnapshot
import io.github.leostumpf.positioninginfo.data.model.PressureReading
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import kotlinx.coroutines.flow.Flow

/**
 * Scripted receiver and sensor data in place of the real ones, for store screenshots taken on
 * an emulator, whose GNSS reports only a few satellites without positions.
 *
 * Only debug builds contain an implementation (`src/debug`); it is loaded by name, so the
 * release build has neither the class nor a way to switch it on.
 */
interface DemoSource {
    /** Fixes along a drive, once a second. */
    fun fixes(): Flow<SpeedFix>

    /** A multi-constellation, dual-frequency sky, once a second. */
    fun snapshots(): Flow<GnssSnapshot>

    /** Air pressure matching the drive's height, for the barometric altitude. */
    fun pressure(): Flow<PressureReading>

    /** NMEA sentences; none, so the fix's own sea-level height is used. */
    fun nmea(): Flow<String>
}

/** Whether this run uses a [DemoSource]; see there. */
object DemoMode {
    /** Set from the launch intent of a debug build, before the ViewModel exists. */
    @Volatile
    var requested = false

    /** The scripted source when [requested] in a debug build; null otherwise. */
    val source: DemoSource? by lazy {
        if (!requested) {
            null
        } else {
            runCatching { Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance() as DemoSource }
                .getOrNull()
        }
    }

    /** The launch-intent extra that asks for demo mode: `adb shell am start … --ez demo true`. */
    const val EXTRA = "demo"

    private const val IMPLEMENTATION = "io.github.leostumpf.positioninginfo.demo.ScriptedDemoSource"
}
