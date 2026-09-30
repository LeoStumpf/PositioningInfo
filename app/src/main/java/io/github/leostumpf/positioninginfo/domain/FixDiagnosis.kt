// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/** How the phone's battery saver treats location, as Android reports it. */
enum class PowerSaveLocation {
    /** Battery saver off, or on without touching location. */
    UNRESTRICTED,
    /** GNSS switched off while the screen is off. */
    GNSS_OFF_SCREEN_OFF,
    /** All location switched off while the screen is off. */
    ALL_OFF_SCREEN_OFF,
    /** Location only for apps in the foreground. */
    FOREGROUND_ONLY,
    /** Requests slowed down while the screen is off. */
    THROTTLED_SCREEN_OFF,
}

/** Everything the diagnosis looks at; all of it read on the phone. */
data class DiagnosisInput(
    val gpsEnabled: Boolean,
    val isMock: Boolean,
    val powerSave: PowerSaveLocation,
    val airplaneMode: Boolean,
    /** Null when the phone cannot tell. */
    val dataConnection: Boolean?,
    /** Physical satellites with any signal at all. */
    val satellitesHeard: Int,
    /** Physical satellites with a usable signal (at least [STRONG_CN0]). */
    val satellitesStrong: Int,
    val usedInFix: Int,
    val readiness: AlmanacReadiness,
    val withEphemeris: Int,
    val pdop: Double?,
    /** How long this session has searched without a fix, or null once fixed. */
    val searchingMs: Long?,
    /** Time to first fix of this session, once there is one. */
    val firstFixMs: Long?,
) {
    companion object {
        const val STRONG_CN0 = 25f
    }
}

enum class CheckStatus { OK, WARN, FAIL, INFO }

/** One link of the chain a fix depends on, in the order the receiver goes through them. */
enum class CheckKind { LOCATION, SOURCE, BATTERY_SAVER, DATA, SATELLITES_HEARD, USABLE_SIGNALS, ORBITS, GEOMETRY, TIMING }

/** A link and how it stands; the wording, from the same input, is the page's business. */
data class DiagnosisCheck(val kind: CheckKind, val status: CheckStatus)

/** The one-line answer: what is (or would be) stopping a fix, most fundamental first. */
enum class Verdict {
    LOCATION_OFF,
    SIMULATED,
    POOR_GEOMETRY,
    ALL_PASS,
    NO_SIGNALS,
    TOO_FEW_HEARD,
    SIGNALS_TOO_WEAK,
    FIX_LOST,
    LEARNING_ORBITS,
    SLOWER_THAN_EXPECTED,
    ACQUIRING,
}

data class Diagnosis(
    val fixed: Boolean,
    val verdict: Verdict,
    val verdictStatus: CheckStatus,
    /** Every check in the order the receiver goes through them. */
    val checks: List<DiagnosisCheck>,
    /** What the verdict was reached from, for the wording and its figures. */
    val input: DiagnosisInput,
    /** How long this start usually takes, see [FixDiagnosis.expectedMs]. */
    val expectedMs: Long,
)

/**
 * Answers "why don't I have a fix?" by walking the chain a fix depends on — location
 * switched on, a real receiver, power, satellites heard, signals strong enough, orbits
 * known, geometry — and naming the first link that fails.
 *
 * The receiver never says why it has no fix, so this is inference from what it does say.
 * This decides; how it is put into words lives with the page (ui/gnss/DiagnosisText.kt).
 */
object FixDiagnosis {

    /**
     * Typical time to first fix per start type, generously rounded up. A cold start with a
     * data connection gets its orbits over the network (A-GNSS) and usually fixes within a
     * minute; only without one must the receiver read them from the satellites.
     */
    fun expectedMs(readiness: AlmanacReadiness, dataConnection: Boolean? = null): Long = when (readiness) {
        AlmanacReadiness.HOT -> 15_000L
        AlmanacReadiness.WARM -> 60_000L
        AlmanacReadiness.COLD, AlmanacReadiness.UNKNOWN -> if (dataConnection == true) 2 * 60_000L else 12 * 60_000L
    }

    fun evaluate(i: DiagnosisInput): Diagnosis {
        val fixed = i.gpsEnabled && i.usedInFix >= AlmanacStatus.SATELLITES_FOR_FIX
        val expected = expectedMs(i.readiness, i.dataConnection)
        val verdict = verdict(i, fixed, expected)
        return Diagnosis(fixed, verdict, verdict.status(), checks(i, expected), i, expected)
    }

    private fun verdict(i: DiagnosisInput, fixed: Boolean, expected: Long): Verdict {
        val searching = i.searchingMs ?: 0L
        // Fixed earlier this session and not now: the orbits are known, so "searching since
        // start" and "learning orbits" no longer apply.
        val lostFix = !fixed && i.searchingMs == null && i.firstFixMs != null
        val needed = AlmanacStatus.SATELLITES_FOR_FIX
        return when {
            !i.gpsEnabled -> Verdict.LOCATION_OFF
            i.isMock -> Verdict.SIMULATED
            fixed && (i.pdop ?: 0.0) > POOR_PDOP -> Verdict.POOR_GEOMETRY
            fixed -> Verdict.ALL_PASS
            i.satellitesHeard == 0 && (lostFix || searching > NO_SIGNAL_GRACE_MS) -> Verdict.NO_SIGNALS
            i.satellitesHeard in 1 until needed -> Verdict.TOO_FEW_HEARD
            i.satellitesStrong < needed && i.satellitesHeard >= needed -> Verdict.SIGNALS_TOO_WEAK
            lostFix -> Verdict.FIX_LOST
            i.readiness == AlmanacReadiness.COLD && i.dataConnection == false -> Verdict.LEARNING_ORBITS
            searching > expected -> Verdict.SLOWER_THAN_EXPECTED
            else -> Verdict.ACQUIRING
        }
    }

    private fun Verdict.status(): CheckStatus = when (this) {
        Verdict.LOCATION_OFF, Verdict.SIMULATED, Verdict.NO_SIGNALS, Verdict.TOO_FEW_HEARD -> CheckStatus.FAIL
        Verdict.POOR_GEOMETRY, Verdict.SIGNALS_TOO_WEAK, Verdict.LEARNING_ORBITS, Verdict.SLOWER_THAN_EXPECTED -> CheckStatus.WARN
        Verdict.ALL_PASS -> CheckStatus.OK
        Verdict.FIX_LOST, Verdict.ACQUIRING -> CheckStatus.INFO
    }

    private fun checks(i: DiagnosisInput, expected: Long): List<DiagnosisCheck> {
        val needed = AlmanacStatus.SATELLITES_FOR_FIX
        return listOf(
            DiagnosisCheck(CheckKind.LOCATION, if (i.gpsEnabled) CheckStatus.OK else CheckStatus.FAIL),
            DiagnosisCheck(CheckKind.SOURCE, if (i.isMock) CheckStatus.FAIL else CheckStatus.OK),
            DiagnosisCheck(
                CheckKind.BATTERY_SAVER,
                when (i.powerSave) {
                    PowerSaveLocation.UNRESTRICTED -> CheckStatus.OK
                    PowerSaveLocation.GNSS_OFF_SCREEN_OFF, PowerSaveLocation.ALL_OFF_SCREEN_OFF,
                    PowerSaveLocation.FOREGROUND_ONLY -> CheckStatus.WARN
                    PowerSaveLocation.THROTTLED_SCREEN_OFF -> CheckStatus.INFO
                },
            ),
            DiagnosisCheck(
                CheckKind.DATA,
                if (!i.airplaneMode && i.dataConnection == true) CheckStatus.OK else CheckStatus.INFO,
            ),
            DiagnosisCheck(
                CheckKind.SATELLITES_HEARD,
                when {
                    i.satellitesHeard >= needed -> CheckStatus.OK
                    i.satellitesHeard > 0 -> CheckStatus.WARN
                    else -> CheckStatus.FAIL
                },
            ),
            DiagnosisCheck(CheckKind.USABLE_SIGNALS, if (i.satellitesStrong >= needed) CheckStatus.OK else CheckStatus.WARN),
            DiagnosisCheck(
                CheckKind.ORBITS,
                when (i.readiness) {
                    AlmanacReadiness.HOT -> CheckStatus.OK
                    AlmanacReadiness.WARM, AlmanacReadiness.UNKNOWN -> CheckStatus.INFO
                    AlmanacReadiness.COLD -> CheckStatus.WARN
                },
            ),
            DiagnosisCheck(
                CheckKind.GEOMETRY,
                when {
                    i.pdop == null -> CheckStatus.INFO
                    i.pdop > POOR_PDOP -> CheckStatus.WARN
                    else -> CheckStatus.OK
                },
            ),
            DiagnosisCheck(
                CheckKind.TIMING,
                when {
                    i.firstFixMs != null -> CheckStatus.OK
                    i.searchingMs != null && i.searchingMs > expected -> CheckStatus.WARN
                    else -> CheckStatus.INFO
                },
            ),
        )
    }

    const val POOR_PDOP = 6.0
    const val NO_SIGNAL_GRACE_MS = 20_000L
}
