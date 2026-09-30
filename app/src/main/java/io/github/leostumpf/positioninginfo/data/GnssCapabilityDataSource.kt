// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.location.GnssCapabilities
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
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

    /** The chip's declarations, as far as this Android version exposes them. Never throws. */
    fun read(): AssistanceCapabilities = runCatching { readUnsafe() }
        // Nothing on this screen is worth crashing a speedometer for. A vendor that
        // diverges from the platform API degrades to "not reported" instead.
        .getOrElse { AssistanceCapabilities() }

    private fun readUnsafe(): AssistanceCapabilities {
        val manager = locationManager ?: return AssistanceCapabilities()
        val hardware = hardwareOf(manager)
        // The capabilities class arrived in Android 11, but its query methods only became
        // public API in Android 12, so 12 is the real floor.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) capabilitiesOf(manager, hardware) else hardware
    }

    /**
     * The hardware name and year. They arrived in Android 9, well before the capabilities API,
     * so they are read separately rather than being hidden from Android 9 and 10.
     */
    private fun hardwareOf(manager: LocationManager): AssistanceCapabilities {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return AssistanceCapabilities()
        return AssistanceCapabilities(
            hardwareModel = runCatching { manager.gnssHardwareModelName }.getOrNull(),
            hardwareYear = runCatching { manager.gnssYearOfHardware }.getOrNull()?.takeIf { it > 0 },
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun capabilitiesOf(manager: LocationManager, hardware: AssistanceCapabilities): AssistanceCapabilities {
        val capabilities = runCatching { manager.gnssCapabilities }.getOrNull() ?: return hardware

        // Available since Android 12: the two founding members of the public API, and antenna info.
        val base = hardware.copy(
            reported = true,
            rawMeasurements = capabilities.hasMeasurements(),
            navigationMessages = capabilities.hasNavigationMessages(),
            antennaInfo = capabilities.hasAntennaInfo(),
        )
        val android14 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return if (android14) base.withAndroid14(capabilities) else base
    }

    /** What Android 14 added: the assistance services and the chip's other features. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun AssistanceCapabilities.withAndroid14(capabilities: GnssCapabilities): AssistanceCapabilities = copy(
        assistanceReported = true,
        assistedMsa = capabilities.hasMsa(),
        assistedMsb = capabilities.hasMsb(),
        onDemandTime = capabilities.hasOnDemandTime(),
        measurementCorrections = capabilities.hasMeasurementCorrections(),
        carrierPhase = capabilities.hasAccumulatedDeltaRange() ==
            GnssCapabilities.CAPABILITY_SUPPORTED,
        satellitePvt = capabilities.hasSatellitePvt(),
        satelliteBlocklist = capabilities.hasSatelliteBlocklist(),
        lowPowerMode = capabilities.hasLowPowerMode(),
        geofencing = capabilities.hasGeofencing(),
        scheduling = capabilities.hasScheduling(),
        singleShotFix = capabilities.hasSingleShotFix(),
        correctionKinds = listOfNotNull(
            "line of sight".takeIf { capabilities.hasMeasurementCorrectionsLosSats() },
            "excess path".takeIf { capabilities.hasMeasurementCorrectionsExcessPathLength() },
            "reflecting planes".takeIf { capabilities.hasMeasurementCorrectionsReflectingPlane() },
            "driving".takeIf { capabilities.hasMeasurementCorrectionsForDriving() },
        ),
        correlationVectors = capabilities.hasMeasurementCorrelationVectors(),
        powerStats = capabilities.hasPowerTotal() || capabilities.hasPowerSinglebandTracking() ||
            capabilities.hasPowerMultibandTracking(),
    )
}
