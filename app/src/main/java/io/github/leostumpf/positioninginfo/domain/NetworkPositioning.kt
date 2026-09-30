// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import io.github.leostumpf.positioninginfo.data.model.NetworkFix
import io.github.leostumpf.positioninginfo.data.model.SpeedFix
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The network location's real error, measured against a good GNSS fix.
 *
 * The network provider's accuracy figure is its own estimate. With a fresh, precise GNSS
 * fix at hand the two positions can simply be compared: GNSS is an order of magnitude
 * better, so the distance between them is, to within the GNSS accuracy, the network
 * location's actual error.
 */
data class NetworkComparison(
    val distanceM: Double,
    val gnssAccuracyM: Float,
    /** True when the true error lies inside the radius the provider claimed. */
    val withinClaimed: Boolean?,
) {
    companion object {
        const val MAX_GNSS_AGE_MS = 10_000L
        const val MAX_NETWORK_AGE_MS = 120_000L
        const val MAX_GNSS_ACCURACY_M = 15f

        /** Null when either fix is too old or GNSS is not precise enough to be the reference. */
        fun of(network: NetworkFix?, gnss: SpeedFix?, nowMs: Long): NetworkComparison? {
            if (network == null || gnss == null) return null
            val lat = gnss.latitude ?: return null
            val lon = gnss.longitude ?: return null
            val gnssAccuracy = gnss.horizontalAccuracyM ?: return null
            if (gnss.isCached || nowMs - gnss.elapsedRealtimeMs > MAX_GNSS_AGE_MS) return null
            if (nowMs - network.elapsedRealtimeMs > MAX_NETWORK_AGE_MS) return null
            if (gnssAccuracy > MAX_GNSS_ACCURACY_M) return null
            val distance = distanceM(lat, lon, network.latitude, network.longitude)
            return NetworkComparison(
                distanceM = distance,
                gnssAccuracyM = gnssAccuracy,
                withinClaimed = network.accuracyM?.let { distance <= it },
            )
        }

        /** Great-circle distance (haversine); plenty for the few hundred metres at stake. */
        fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_M * asin(sqrt(a))
        }

        private const val EARTH_RADIUS_M = 6_371_000.0
    }
}

/**
 * Distance to the serving tower from the timing advance.
 *
 * The network tells the phone how early to transmit so its signal arrives in its time slot;
 * that offset is the round trip to the tower, quantised into steps.
 */
object TimingAdvance {
    /** LTE: one step is 16 Ts (≈ 0.52 µs of round trip), about 78 m of distance. */
    const val LTE_METRES_PER_STEP = 78.12

    /** GSM: one step is one bit period (3.69 µs of round trip), about 553 m. */
    const val GSM_METRES_PER_STEP = 553.5
    private const val SPEED_OF_LIGHT_M_PER_S = 299_792_458.0

    /** The largest values Android documents for each; anything beyond is "unavailable". */
    private const val LTE_MAX_STEPS = 1_282
    private const val GSM_MAX_STEPS = 219
    private const val NR_MAX_MICROS = 20_000
    private const val S_PER_MICROSECOND = 1e-6

    /** Distance in metres for an LTE timing advance; null outside 0..1282 (Android's "unavailable"). */
    fun lteMetres(steps: Int): Double? = steps.takeIf { it in 0..LTE_MAX_STEPS }?.let { it * LTE_METRES_PER_STEP }

    /** Distance in metres for a GSM timing advance; null outside 0..219 (Android's "unavailable"). */
    fun gsmMetres(steps: Int): Double? = steps.takeIf { it in 0..GSM_MAX_STEPS }?.let { it * GSM_METRES_PER_STEP }

    /** 5G reports the round trip directly in microseconds; half of it is the way there. */
    fun nrMetres(micros: Int): Double? =
        micros.takeIf { it in 0..NR_MAX_MICROS }?.let { it * S_PER_MICROSECOND * SPEED_OF_LIGHT_M_PER_S / 2 }
}
