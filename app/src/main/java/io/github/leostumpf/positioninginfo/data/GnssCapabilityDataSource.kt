// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.location.GnssCapabilities
import android.location.LocationManager
import android.os.Build
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.AssistanceCapabilities

/**
 * Reads the receiver's declared capabilities.
 *
 * These are static for the life of the device, so unlike the satellite status there is
 * nothing to stream — the screen asks once.
 *
 * `GnssCapabilities` grew in two steps: its raw-data queries became public API in Android
 * 12, and the assistance and correction queries only in Android 14. Calling a newer one on
 * an older device throws `NoSuchMethodError` — and lint's metadata does not flag the
 * Android 14 additions — so both boundaries are enforced here by hand, checked against the
 * platform jars, and the whole read is caught as a last resort.
 */
class GnssCapabilityDataSource(context: Context) {

    private val locationManager = context.applicationContext.getSystemService<LocationManager>()

    fun read(): AssistanceCapabilities = runCatching { readUnsafe() }
        // Nothing on this screen is worth crashing a speedometer for. A vendor that
        // diverges from the platform API degrades to "not reported" instead.
        .getOrElse { AssistanceCapabilities() }

    private fun readUnsafe(): AssistanceCapabilities {
        val manager = locationManager ?: return AssistanceCapabilities()

        // The hardware name and year arrived in Android 9, well before the capabilities
        // API, so they are read separately rather than being hidden from Android 9 and 10.
        val model: String?
        val year: Int?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            model = runCatching { manager.gnssHardwareModelName }.getOrNull()
            year = runCatching { manager.gnssYearOfHardware }.getOrNull()?.takeIf { it > 0 }
        } else {
            model = null
            year = null
        }

        // The class itself arrived in Android 11, but its query methods only became
        // public API in Android 12, so 12 is the real floor.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return AssistanceCapabilities(hardwareModel = model, hardwareYear = year)
        }

        val capabilities = runCatching { manager.gnssCapabilities }.getOrNull()
            ?: return AssistanceCapabilities(hardwareModel = model, hardwareYear = year)

        // Available since Android 12, the two founding members of the public API.
        val base = AssistanceCapabilities(
            reported = true,
            rawMeasurements = capabilities.hasMeasurements(),
            navigationMessages = capabilities.hasNavigationMessages(),
            hardwareModel = model,
            hardwareYear = year,
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return base

        return base.copy(
            assistanceReported = true,
            assistedMsa = capabilities.hasMsa(),
            assistedMsb = capabilities.hasMsb(),
            onDemandTime = capabilities.hasOnDemandTime(),
            measurementCorrections = capabilities.hasMeasurementCorrections(),
            carrierPhase = capabilities.hasAccumulatedDeltaRange() ==
                GnssCapabilities.CAPABILITY_SUPPORTED,
        )
    }
}
