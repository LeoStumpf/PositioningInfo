// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Clears and re-fetches the receiver's assistance data through the GPS provider's extra
 * commands.
 *
 * A phone rarely downloads its orbital data from the satellites themselves: it gets
 * predicted orbits (PSDS, formerly XTRA) and the time from the internet, over Wi-Fi or
 * mobile data. These commands let that be observed: wipe everything to force a genuine
 * cold start, or ask for a fresh download.
 *
 * A `true` result means only that the framework accepted the command. Some vendors'
 * drivers silently ignore it, and only the GNSS screen can show whether anything changed.
 */
class AssistanceDataSource(context: Context) {

    private val locationManager = context.applicationContext.getSystemService<LocationManager>()

    /** Deletes all aiding data (ephemeris, almanac, position, time) — a null bundle means "all". */
    fun clearAidingData(): Boolean = send(DELETE_AIDING_DATA)

    /** Requests fresh predicted orbits and a time injection. True if either was accepted. */
    fun injectAssistanceData(): Boolean {
        val orbits = send(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) FORCE_PSDS else FORCE_XTRA)
        val time = send(FORCE_TIME)
        return orbits || time
    }

    private fun send(command: String): Boolean {
        val manager = locationManager ?: return false
        return try {
            manager.sendExtraCommand(LocationManager.GPS_PROVIDER, command, null)
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            // Thrown when the device has no GPS provider.
            false
        }
    }

    private companion object {
        const val DELETE_AIDING_DATA = "delete_aiding_data"
        const val FORCE_PSDS = "force_psds_injection"
        const val FORCE_XTRA = "force_xtra_injection"
        const val FORCE_TIME = "force_time_injection"
    }
}
