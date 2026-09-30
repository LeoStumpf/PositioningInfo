// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import kotlin.math.abs
import kotlin.math.sqrt

/** The receiver's automatic gain control for one constellation and carrier, from raw measurements. */
data class AgcReading(val constellation: Constellation, val carrierFrequencyHz: Double, val levelDb: Double)

/** One tracked signal in a raw measurement epoch. [multipath] is null when the receiver does not say. */
data class SignalReading(
    val constellation: Constellation,
    val svid: Int,
    val carrierFrequencyHz: Double?,
    val cn0DbHz: Double,
    val multipath: Boolean?,
)

/**
 * One `GnssMeasurementsEvent`, stripped of Android types.
 *
 * [atMs] should come from a monotonic clock (elapsed realtime), like the rest of the app.
 */
data class RawEpoch(
    val atMs: Long,
    val agc: List<AgcReading>,
    val signals: List<SignalReading>,
    val clockDriftNsPerS: Double?,
    val hardwareClockDiscontinuityCount: Int?,
    val leapSecond: Int?,
)

/**
 * The radio bands that matter for interference. A jammer is a transmitter on one frequency
 * range, so the evidence has to be judged per band: an L1 jammer leaves L5 untouched.
 */
enum class Band {
    L1_E1_B1,
    L5_E5A_B2A,
    OTHER,
    ;

    companion object {
        /**
         * GPS L1, Galileo E1, BeiDou B1I/B1C, QZSS L1 and the GLONASS L1 FDMA channels all sit
         * between about 1559 and 1610 MHz; GPS L5, Galileo E5a, BeiDou B2a and NavIC L5 around
         * 1176 MHz.
         */
        fun of(carrierHz: Double): Band = when (SignalBand.fromCarrierFrequencyHz(carrierHz.toFloat())) {
            // SignalBand's edges, so a signal sits on the same band on every page.
            SignalBand.L1 -> L1_E1_B1

            SignalBand.L5 -> L5_E5A_B2A

            else -> OTHER
        }
    }
}

data class BandStatus(
    val band: Band,
    val agcDb: Double?,
    val agcBaselineDb: Double?,
    /** Baseline minus current: positive means the receiver turned its gain down. */
    val agcDropDb: Double?,
    val meanCn0DbHz: Double?,
    val cn0BaselineDbHz: Double?,
    val signals: Int,
    /** Signals typically tracked on this band during the baseline minute. */
    val signalsBaseline: Double? = null,
)

/** Something in the signals that a spoofer could cause, though other things can too. */
sealed interface SpoofingIndicator {
    /** At least six signals on [band] within a narrow spread of strength, all strong. */
    data class UniformStrength(val band: Band, val signals: Int, val meanDbHz: Double, val spreadDb: Double) :
        SpoofingIndicator

    /** More power in [band] (the gain turned down by [agcDropDb]) while the signals got stronger. */
    data class PowerWithStrongerSignals(val band: Band, val agcDropDb: Double) : SpoofingIndicator

    /** The receiver clock's drift jumped by more than [thresholdPpm] between epochs. */
    data class DriftJump(val thresholdPpm: Double) : SpoofingIndicator
}

data class InterferenceAssessment(
    val bands: List<BandStatus>,
    val jammingSuspected: Boolean,
    /** Each is a reason to look closer, not a verdict; the page words them cautiously. */
    val spoofingIndicators: List<SpoofingIndicator>,
    val clockDriftPpm: Double?,
    /** Over the last [InterferenceMonitor.DRIFT_WINDOW_MS]. */
    val clockDriftStdDevPpm: Double?,
    /** Increments of the hardware discontinuity counter observed since start. */
    val clockDiscontinuities: Int,
    val leapSecond: Int?,
    val multipathSignals: Int,
    val totalSignals: Int,
    val epochs: Int,
)

/** What the first minute of a session looked like on one band, one value per epoch. */
data class BandBaselineSamples(
    val agcDb: List<Double> = emptyList(),
    val meanCn0DbHz: List<Double> = emptyList(),
    val signals: List<Double> = emptyList(),
)

/**
 * Jamming and spoofing indicators, and receiver clock quality, from raw GNSS measurements.
 *
 * Everything here is a heuristic. A phone has one small antenna and no reference receiver,
 * so it cannot prove interference; it can only notice when the radio environment changes in
 * the ways interference tends to change it. The app is offline, so nothing is compared with
 * outside data either: the reference is this receiver's own first minute ([BASELINE_MS]).
 *
 * - **Baselines**, per band: the median AGC level and the median of the per-epoch mean C/N0
 *   over the first [BASELINE_MS] of epochs, then frozen. A gap of more than [RESET_AFTER_MS]
 *   (app in the background, receiver off, the user moved elsewhere) starts a new baseline.
 *   Until the baseline window has passed nothing is judged.
 * - **Jamming**: a jammer raises the power in the band, so the receiver turns its gain down
 *   (the AGC level drops) and, because the noise floor rose, the C/N0 of every satellite
 *   falls together. Suspected when on some band the AGC dropped by at least [JAM_AGC_DROP_DB]
 *   and either the mean C/N0 fell by at least [JAM_CN0_DROP_DB] or fewer than
 *   [JAM_MIN_SIGNALS] signals remain, for [JAM_PERSIST_EPOCHS] consecutive epochs. Walking
 *   indoors lowers C/N0 but not the AGC, which is why both are required.
 * - **Spoofing** leaves subtler traces, each reported on its own:
 *   - many signals on a band with nearly identical, strong C/N0 (real satellites at
 *     different elevations differ; a single transmitter makes them alike);
 *   - more power in the band (AGC level down by [SPOOF_AGC_SHIFT_DB] or more) while C/N0
 *     rises: a source stronger than the sky, where a jammer would make C/N0 fall;
 *   - the clock drift jumping by more than [DRIFT_JUMP_PPM] between consecutive epochs,
 *     which a crystal oscillator does not do by itself.
 *
 * Hardware clock discontinuities are counted but deliberately not treated as an indicator:
 * phone chips reset their clock routinely when they duty-cycle to save power, so a Pixel
 * racks up a dozen within minutes of normal use.
 *
 * Immutable, like [SkyTracker]: every event returns a new instance.
 */
data class InterferenceMonitor(
    val sessionStartMs: Long? = null,
    val lastEpoch: RawEpoch? = null,
    val baselineSamples: Map<Band, BandBaselineSamples> = emptyMap(),
    val jammingStreak: Int = 0,
    /** (atMs, drift in ppm) within the last [DRIFT_WINDOW_MS]. */
    val driftHistory: List<Pair<Long, Double>> = emptyList(),
    val lastDriftJumpMs: Long? = null,
    val lastDiscontinuityCount: Int? = null,
    val discontinuities: Int = 0,
    val leapSecond: Int? = null,
    val epochs: Int = 0,
) {

    /** Takes in one epoch of raw measurements; the baseline is learned over the first minute. */
    fun onEpoch(epoch: RawEpoch): InterferenceMonitor {
        val previousAt = lastEpoch?.atMs
        // After a long gap the old baseline may describe another place or another time.
        val base = if (previousAt == null || epoch.atMs - previousAt > RESET_AFTER_MS) freshStart(epoch.atMs) else this
        val start = base.sessionStartMs ?: epoch.atMs
        val current = measure(epoch)

        val collecting = epoch.atMs - start < BASELINE_MS
        val samples = if (collecting) base.baselineSamples.plus(current) else base.baselineSamples
        val statuses = bandStatuses(current, if (collecting) emptyMap() else samples)
        val jamming = statuses.any { it.looksJammed() }

        val count = epoch.hardwareClockDiscontinuityCount
        val newDiscontinuities = newDiscontinuities(count, base.lastDiscontinuityCount)
        val drift = base.driftAfter(epoch, clockReset = newDiscontinuities > 0)

        return base.copy(
            sessionStartMs = start,
            lastEpoch = epoch,
            baselineSamples = samples,
            jammingStreak = if (jamming) base.jammingStreak + 1 else 0,
            driftHistory = drift.history,
            lastDriftJumpMs = if (drift.jumped) epoch.atMs else base.lastDriftJumpMs,
            lastDiscontinuityCount = count ?: base.lastDiscontinuityCount,
            discontinuities = base.discontinuities + newDiscontinuities,
            leapSecond = epoch.leapSecond ?: base.leapSecond,
            epochs = base.epochs + 1,
        )
    }

    private fun freshStart(atMs: Long) = copy(
        sessionStartMs = atMs,
        lastEpoch = null,
        baselineSamples = emptyMap(),
        jammingStreak = 0,
        driftHistory = emptyList(),
        lastDriftJumpMs = null,
        lastDiscontinuityCount = null,
    )

    /** How many times the hardware clock restarted since [lastCount] was read. */
    private fun newDiscontinuities(count: Int?, lastCount: Int?): Int = when {
        count == null || lastCount == null || count == lastCount -> 0
        count > lastCount -> count - lastCount
        else -> 1 // the counter went backwards: at least one reset happened
    }

    /** The drift history with [epoch] added, and whether the drift jumped since the epoch before. */
    private class DriftUpdate(val history: List<Pair<Long, Double>>, val jumped: Boolean)

    private fun driftAfter(epoch: RawEpoch, clockReset: Boolean): DriftUpdate {
        // A drift jump counts only between consecutive epochs of one continuous clock: after
        // a hardware discontinuity (routine duty cycling) the drift estimate restarts, and
        // across a gap in the epochs the change is spread over time nobody watched.
        val driftPpm = epoch.clockDriftNsPerS?.let { it / NS_PER_S_PER_PPM }
        val previous = driftHistory.lastOrNull()
        val consecutive = previous != null && !clockReset && epoch.atMs - previous.first <= DRIFT_JUMP_MAX_GAP_MS
        val jumped = consecutive && driftPpm != null && abs(driftPpm - previous.second) > DRIFT_JUMP_PPM
        val kept = if (clockReset) emptyList() else driftHistory
        val history = (if (driftPpm != null) kept + (epoch.atMs to driftPpm) else kept)
            .filter { epoch.atMs - it.first <= DRIFT_WINDOW_MS }
        return DriftUpdate(history, jumped)
    }

    /** The baseline samples with one more epoch's measurements per band added. */
    private fun Map<Band, BandBaselineSamples>.plus(
        current: Map<Band, BandMeasurement>,
    ): Map<Band, BandBaselineSamples> {
        val updated = toMutableMap()
        for ((band, m) in current) {
            val old = updated[band] ?: BandBaselineSamples()
            updated[band] = BandBaselineSamples(
                agcDb = m.agcDb?.let { old.agcDb + it } ?: old.agcDb,
                meanCn0DbHz = m.meanCn0?.let { old.meanCn0DbHz + it } ?: old.meanCn0DbHz,
                signals = old.signals + m.cn0s.size.toDouble(),
            )
        }
        return updated
    }

    val assessment: InterferenceAssessment
        get() {
            val epoch = lastEpoch
            val start = sessionStartMs
            val baselineReady = epoch != null && start != null && epoch.atMs - start >= BASELINE_MS
            val current = epoch?.let { measure(it) }.orEmpty()
            val statuses = bandStatuses(current, if (baselineReady) baselineSamples else emptyMap())
            val drifts = driftHistory.map { it.second }
            return InterferenceAssessment(
                bands = statuses,
                jammingSuspected = jammingStreak >= JAM_PERSIST_EPOCHS,
                spoofingIndicators = spoofingIndicators(statuses, current),
                clockDriftPpm = epoch?.clockDriftNsPerS?.let { it / 1000.0 },
                clockDriftStdDevPpm = if (drifts.size >= 2) stdDev(drifts) else null,
                clockDiscontinuities = discontinuities,
                leapSecond = leapSecond,
                multipathSignals = epoch?.signals?.count { it.multipath == true } ?: 0,
                totalSignals = epoch?.signals?.size ?: 0,
                epochs = epochs,
            )
        }

    private fun spoofingIndicators(
        statuses: List<BandStatus>,
        current: Map<Band, BandMeasurement>,
    ): List<SpoofingIndicator> {
        val out = mutableListOf<SpoofingIndicator>()
        for ((band, m) in current) {
            if (m.cn0s.size >= SPOOF_UNIFORM_MIN_SIGNALS) {
                val mean = m.cn0s.average()
                val sd = stdDev(m.cn0s)
                if (sd < SPOOF_UNIFORM_MAX_STDDEV && mean > SPOOF_UNIFORM_MIN_MEAN) {
                    out += SpoofingIndicator.UniformStrength(band, m.cn0s.size, mean, sd)
                }
            }
        }
        for (s in statuses) {
            s.strongerSourceDropDb()?.let { out += SpoofingIndicator.PowerWithStrongerSignals(s.band, it) }
        }
        val jumpAt = lastDriftJumpMs
        val now = lastEpoch?.atMs
        if (jumpAt != null && now != null && now - jumpAt <= DRIFT_WINDOW_MS) {
            out += SpoofingIndicator.DriftJump(DRIFT_JUMP_PPM)
        }
        return out
    }

    /**
     * The AGC drop when the band has more power while its signals got clearly stronger — a
     * source stronger than the sky — or null when it has not.
     */
    private fun BandStatus.strongerSourceDropDb(): Double? {
        val drop = agcDropDb ?: return null
        val rise = meanCn0DbHz?.let { mean -> cn0BaselineDbHz?.let { mean - it } } ?: return null
        return drop.takeIf { it >= SPOOF_AGC_SHIFT_DB && rise >= SPOOF_CN0_RISE_DB }
    }

    private fun BandStatus.looksJammed(): Boolean {
        val drop = agcDropDb ?: return false
        if (drop < JAM_AGC_DROP_DB) return false
        val cn0Fell =
            cn0BaselineDbHz != null && (meanCn0DbHz == null || cn0BaselineDbHz - meanCn0DbHz >= JAM_CN0_DROP_DB)
        // Losing signals counts only against a band that had plenty: L5 is often tracked on
        // two or three satellites, and few signals there is normal, not a sign of jamming.
        val base = signalsBaseline
        val signalsLost = base != null && base >= JAM_MIN_SIGNALS && signals < JAM_MIN_SIGNALS && signals <= base / 2
        return cn0Fell || signalsLost
    }

    /** One band in one epoch. */
    private data class BandMeasurement(val agcDb: Double?, val cn0s: List<Double>) {
        val meanCn0: Double? get() = if (cn0s.isEmpty()) null else cn0s.average()
    }

    companion object {
        const val BASELINE_MS = 60_000L
        const val RESET_AFTER_MS = 60_000L
        const val DRIFT_WINDOW_MS = 60_000L
        const val JAM_AGC_DROP_DB = 6.0
        const val JAM_CN0_DROP_DB = 6.0
        const val JAM_MIN_SIGNALS = 4
        const val JAM_PERSIST_EPOCHS = 3
        const val SPOOF_UNIFORM_MIN_SIGNALS = 6
        const val SPOOF_UNIFORM_MAX_STDDEV = 1.5
        const val SPOOF_UNIFORM_MIN_MEAN = 40.0
        const val SPOOF_AGC_SHIFT_DB = 4.0

        /** Signals must have got clearly stronger, not by the odd tenth of a dB. */
        const val SPOOF_CN0_RISE_DB = 3.0
        const val DRIFT_JUMP_PPM = 0.5

        /** Clock drift arrives in ns/s; 1 ppm is 1000 ns/s. */
        private const val NS_PER_S_PER_PPM = 1_000.0

        /** Epochs further apart than this are not compared for a drift jump. */
        const val DRIFT_JUMP_MAX_GAP_MS = 2_000L

        /**
         * Groups an epoch by band. Several constellations report their own AGC on the same
         * band; they are averaged. Signals without a carrier frequency come from receivers
         * that only report L1, so they are counted there. Untracked signals (C/N0 ≤ 0) are
         * left out.
         */
        private fun measure(epoch: RawEpoch): Map<Band, BandMeasurement> {
            val agc = epoch.agc.groupBy { Band.of(it.carrierFrequencyHz) }
                .mapValues { (_, r) -> r.map { it.levelDb }.average() }
            val cn0 = epoch.signals.filter { it.cn0DbHz > 0.0 }
                .groupBy { it.carrierFrequencyHz?.let(Band::of) ?: Band.L1_E1_B1 }
                .mapValues { (_, s) -> s.map { it.cn0DbHz } }
            return (agc.keys + cn0.keys).associateWith { BandMeasurement(agc[it], cn0[it].orEmpty()) }
        }

        private fun bandStatuses(
            current: Map<Band, BandMeasurement>,
            baseline: Map<Band, BandBaselineSamples>,
        ): List<BandStatus> = (current.keys + baseline.keys).sorted().map { band ->
            val m = current[band]
            val b = baseline[band]
            val agcBaseline = b?.agcDb?.takeIf { it.isNotEmpty() }?.let(::median)
            val cn0Baseline = b?.meanCn0DbHz?.takeIf { it.isNotEmpty() }?.let(::median)
            val agc = m?.agcDb
            BandStatus(
                band = band,
                agcDb = agc,
                agcBaselineDb = agcBaseline,
                agcDropDb = if (agc != null && agcBaseline != null) agcBaseline - agc else null,
                meanCn0DbHz = m?.meanCn0,
                cn0BaselineDbHz = cn0Baseline,
                signals = m?.cn0s?.size ?: 0,
                signalsBaseline = b?.signals?.takeIf { it.isNotEmpty() }?.let(::median),
            )
        }

        private fun median(values: List<Double>): Double {
            val s = values.sorted()
            val mid = s.size / 2
            return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2
        }

        /** Population standard deviation. */
        private fun stdDev(values: List<Double>): Double {
            val mean = values.average()
            return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        }
    }
}
