// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.Manifest
import android.content.Context
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.data.model.NavigationFrame
import io.github.leostumpf.positioninginfo.data.model.NavigationUpdate
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementEpoch
import io.github.leostumpf.positioninginfo.data.model.RawMeasurementUpdate
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.AgcReading
import io.github.leostumpf.positioninginfo.domain.Constellation
import io.github.leostumpf.positioninginfo.domain.RawEpoch
import io.github.leostumpf.positioninginfo.domain.SignalReading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The receiver's lower-level output: NMEA sentences, raw measurements and the decoded bits
 * of the satellites' navigation messages.
 *
 * All three only flow while the GPS provider is running, which the location stream keeps
 * going; none of them starts the receiver on its own.
 */
class GnssRawDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService<LocationManager>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun nmea(): Flow<String> = callbackFlow {
        val manager = locationManager ?: run {
            close()
            return@callbackFlow
        }
        val listener = OnNmeaMessageListener { message, _ -> trySend(message) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.addNmeaListener(appContext.mainExecutor, listener)
        } else {
            manager.addNmeaListener(listener, handler)
        }
        awaitClose { manager.removeNmeaListener(listener) }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun measurements(): Flow<RawMeasurementUpdate> = callbackFlow {
        val manager = locationManager ?: run {
            close()
            return@callbackFlow
        }
        val callback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                trySend(RawMeasurementUpdate.Epoch(event.toEpoch()))
            }

            // Deprecated from Android 12 in favour of capabilities, but the only signal before
            // that of whether raw measurements are supported at all; still delivered after.
            @Deprecated("Status callbacks are deprecated from API 31")
            override fun onStatusChanged(status: Int) {
                trySend(RawMeasurementUpdate.Status(status.toStreamStatus()))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.registerGnssMeasurementsCallback(appContext.mainExecutor, callback)
        } else {
            manager.registerGnssMeasurementsCallback(callback, handler)
        }
        awaitClose { manager.unregisterGnssMeasurementsCallback(callback) }
    }

    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    fun navigationMessages(): Flow<NavigationUpdate> = callbackFlow {
        val manager = locationManager ?: run {
            close()
            return@callbackFlow
        }
        val callback = object : GnssNavigationMessage.Callback() {
            override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                trySend(
                    NavigationUpdate.Frame(
                        NavigationFrame(
                            signal = event.type.signalName(),
                            isGpsL1Ca = event.type == GnssNavigationMessage.TYPE_GPS_L1CA,
                            svid = event.svid,
                            data = event.data,
                            parityOk = event.status and GnssNavigationMessage.STATUS_PARITY_PASSED != 0 ||
                                event.status and GnssNavigationMessage.STATUS_PARITY_REBUILT != 0,
                        ),
                    ),
                )
            }

            @Deprecated("Status callbacks are deprecated from API 31")
            @Suppress("DEPRECATION")
            override fun onStatusChanged(status: Int) {
                trySend(
                    NavigationUpdate.Status(
                        when (status) {
                            STATUS_READY -> RawStreamStatus.READY
                            STATUS_NOT_SUPPORTED -> RawStreamStatus.NOT_SUPPORTED
                            STATUS_LOCATION_DISABLED -> RawStreamStatus.LOCATION_DISABLED
                            else -> RawStreamStatus.UNKNOWN
                        },
                    ),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.registerGnssNavigationMessageCallback(appContext.mainExecutor, callback)
        } else {
            manager.registerGnssNavigationMessageCallback(callback, handler)
        }
        awaitClose { manager.unregisterGnssNavigationMessageCallback(callback) }
    }
}

@Suppress("DEPRECATION") // the status constants, see onStatusChanged
private fun Int.toStreamStatus(): RawStreamStatus = when (this) {
    GnssMeasurementsEvent.Callback.STATUS_READY -> RawStreamStatus.READY

    GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED,
    GnssMeasurementsEvent.Callback.STATUS_NOT_ALLOWED,
    -> RawStreamStatus.NOT_SUPPORTED

    GnssMeasurementsEvent.Callback.STATUS_LOCATION_DISABLED -> RawStreamStatus.LOCATION_DISABLED

    else -> RawStreamStatus.UNKNOWN
}

private fun GnssMeasurementsEvent.toEpoch(): RawMeasurementEpoch {
    val agc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gnssAutomaticGainControls.map {
            AgcReading(
                Constellation.fromAndroidType(it.constellationType),
                it.carrierFrequencyHz.toDouble(),
                it.levelDb,
            )
        }
    } else {
        emptyList()
    }.ifEmpty {
        // Older platforms report AGC per measurement instead; one reading per band suffices.
        @Suppress("DEPRECATION")
        measurements
            .filter { it.hasAutomaticGainControlLevelDb() }
            .map {
                AgcReading(
                    Constellation.fromAndroidType(it.constellationType),
                    if (it.hasCarrierFrequencyHz()) it.carrierFrequencyHz.toDouble() else L1_HZ,
                    it.automaticGainControlLevelDb,
                )
            }
            .distinctBy { it.constellation to (it.carrierFrequencyHz / 1e6).toInt() }
    }
    val signals = measurements.map {
        SignalReading(
            constellation = Constellation.fromAndroidType(it.constellationType),
            svid = it.svid,
            carrierFrequencyHz = if (it.hasCarrierFrequencyHz()) it.carrierFrequencyHz.toDouble() else null,
            cn0DbHz = it.cn0DbHz,
            multipath = when (it.multipathIndicator) {
                GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> true
                GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> false
                else -> null
            },
        )
    }
    return RawMeasurementEpoch(
        epoch = RawEpoch(
            atMs = SystemClock.elapsedRealtime(),
            agc = agc,
            signals = signals,
            clockDriftNsPerS = if (clock.hasDriftNanosPerSecond()) clock.driftNanosPerSecond else null,
            hardwareClockDiscontinuityCount = clock.hardwareClockDiscontinuityCount,
            leapSecond = if (clock.hasLeapSecond()) clock.leapSecond else null,
        ),
        carrierPhaseValid = measurements.count {
            it.accumulatedDeltaRangeState and GnssMeasurement.ADR_STATE_VALID != 0
        },
        hasFullBias = clock.hasFullBiasNanos(),
        measurements = measurements.map {
            SignalMeasurement(
                constellation = Constellation.fromAndroidType(it.constellationType),
                svid = it.svid,
                carrierFrequencyHz = if (it.hasCarrierFrequencyHz()) it.carrierFrequencyHz.toDouble() else null,
                cn0DbHz = it.cn0DbHz,
                state = it.state,
                pseudorangeRateMps = it.pseudorangeRateMetersPerSecond,
                multipath = when (it.multipathIndicator) {
                    GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> true
                    GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> false
                    else -> null
                },
                codeType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    it.hasCodeType()
                ) {
                    it.codeType
                } else {
                    null
                },
                basebandCn0DbHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    it.hasBasebandCn0DbHz()
                ) {
                    it.basebandCn0DbHz
                } else {
                    null
                },
                snrDb = if (it.hasSnrInDb()) it.snrInDb else null,
                receivedSvTimeUncertaintyNs = it.receivedSvTimeUncertaintyNanos.takeIf { u -> u > 0 },
                carrierPhaseState = it.accumulatedDeltaRangeState,
                interSignalBiasNs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    it.hasFullInterSignalBiasNanos()
                ) {
                    it.fullInterSignalBiasNanos
                } else {
                    null
                },
            )
        },
    )
}

private fun Int.signalName(): String = when (this) {
    GnssNavigationMessage.TYPE_GPS_L1CA -> "GPS L1 C/A"
    GnssNavigationMessage.TYPE_GPS_L2CNAV -> "GPS L2 CNAV"
    GnssNavigationMessage.TYPE_GPS_L5CNAV -> "GPS L5 CNAV"
    GnssNavigationMessage.TYPE_GPS_CNAV2 -> "GPS CNAV-2"
    GnssNavigationMessage.TYPE_GLO_L1CA -> "GLONASS L1 C/A"
    GnssNavigationMessage.TYPE_BDS_D1 -> "BeiDou D1"
    GnssNavigationMessage.TYPE_BDS_D2 -> "BeiDou D2"
    GnssNavigationMessage.TYPE_GAL_I -> "Galileo I/NAV"
    GnssNavigationMessage.TYPE_GAL_F -> "Galileo F/NAV"
    GnssNavigationMessage.TYPE_QZS_L1CA -> "QZSS L1 C/A"
    GnssNavigationMessage.TYPE_IRN_L5CA -> "NavIC L5"
    else -> "Other"
}

private const val L1_HZ = 1_575.42e6
