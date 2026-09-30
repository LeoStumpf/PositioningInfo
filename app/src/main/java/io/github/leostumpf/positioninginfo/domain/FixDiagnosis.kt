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

data class DiagnosisCheck(val label: String, val value: String, val status: CheckStatus, val hint: String? = null)

data class Diagnosis(
    val fixed: Boolean,
    /** The one-line answer: what is (or would be) stopping a fix. */
    val verdict: String,
    val verdictStatus: CheckStatus,
    val detail: String,
    /** Every check in the order the receiver goes through them. */
    val checks: List<DiagnosisCheck>,
)

/**
 * Answers "why don't I have a fix?" by walking the chain a fix depends on — location
 * switched on, a real receiver, power, satellites heard, signals strong enough, orbits
 * known, geometry — and naming the first link that fails.
 *
 * The receiver never says why it has no fix, so this is inference from what it does say,
 * worded as such.
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
        val checks = checks(i)
        val (verdict, status, detail) = verdict(i, fixed)
        return Diagnosis(fixed, verdict, status, detail, checks)
    }

    private fun verdict(i: DiagnosisInput, fixed: Boolean): Triple<String, CheckStatus, String> {
        val searching = i.searchingMs ?: 0L
        val expected = expectedMs(i.readiness, i.dataConnection)
        // Fixed earlier this session and not now: the orbits are known, so "searching since
        // start" and "learning orbits" no longer apply.
        val lostFix = !fixed && i.searchingMs == null && i.firstFixMs != null
        return when {
            !i.gpsEnabled -> Triple(
                "Location is switched off", CheckStatus.FAIL,
                "The receiver is not running at all. Switch location on in the quick settings.",
            )
            i.isMock -> Triple(
                "The position is simulated", CheckStatus.FAIL,
                "A mock-location app is supplying positions, so the receiver's own fix is not what apps see.",
            )
            fixed && (i.pdop ?: 0.0) > POOR_PDOP -> Triple(
                "Fixed, but with poor geometry", CheckStatus.WARN,
                "The satellites in use are bunched together (PDOP ${String.format(java.util.Locale.US, "%.1f", i.pdop)}), so the position " +
                    "is less precise than the signals would allow. Open sky helps.",
            )
            fixed -> Triple(
                "All checks pass", CheckStatus.OK,
                "Fixed on ${i.usedInFix.counted("satellite")}." +
                    (i.firstFixMs?.let { " This session's first fix took ${formatDuration(it)}." } ?: ""),
            )
            i.satellitesHeard == 0 && (lostFix || searching > NO_SIGNAL_GRACE_MS) -> Triple(
                "No satellite signals", CheckStatus.FAIL,
                "Nothing is heard at all — almost always a roof, walls or a car body in the way. " +
                    "GNSS signals need a view of the sky; try near a window or outside.",
            )
            i.satellitesHeard in 1 until AlmanacStatus.SATELLITES_FOR_FIX -> Triple(
                "Only ${i.satellitesHeard.counted("satellite")} heard", CheckStatus.FAIL,
                "A fix needs at least ${AlmanacStatus.SATELLITES_FOR_FIX}: three for position, one for the " +
                    "receiver's own clock. More of the sky has to be visible.",
            )
            i.satellitesStrong < AlmanacStatus.SATELLITES_FOR_FIX && i.satellitesHeard >= AlmanacStatus.SATELLITES_FOR_FIX -> Triple(
                "Signals too weak", CheckStatus.WARN,
                "${i.satellitesHeard.counted("satellite")} ${if (i.satellitesHeard == 1) "is" else "are"} heard, but only ${i.satellitesStrong} strongly enough to " +
                    "decode their data. Typical indoors or under dense trees.",
            )
            lostFix -> Triple(
                "Fix lost, reacquiring", CheckStatus.INFO,
                "The receiver had a fix this session and still holds the orbits, so it usually " +
                    "recovers within seconds once enough of the sky is in view again.",
            )
            i.readiness == AlmanacReadiness.COLD && i.dataConnection == false -> Triple(
                "Learning orbits from the satellites", CheckStatus.WARN,
                "No orbital data is stored and there is no data connection for assistance, so the " +
                    "receiver must download orbits from the satellites themselves — up to 12 minutes " +
                    "with a clear view of the sky.",
            )
            searching > expected -> Triple(
                "Taking longer than expected", CheckStatus.WARN,
                "Searching for ${formatDuration(searching)}; a ${i.readiness.name.lowercase()} start usually " +
                    "fixes within ${formatDuration(expected)}. Signals are probably marginal.",
            )
            else -> Triple(
                "Acquiring", CheckStatus.INFO,
                "Signals are there and the receiver is working through them. Expected within " +
                    "${formatDuration(expected)} of starting (${formatDuration(searching)} so far).",
            )
        }
    }

    private fun checks(i: DiagnosisInput): List<DiagnosisCheck> = buildList {
        add(
            DiagnosisCheck(
                "Location service", if (i.gpsEnabled) "on" else "off",
                if (i.gpsEnabled) CheckStatus.OK else CheckStatus.FAIL,
            ),
        )
        add(
            DiagnosisCheck(
                "Position source", if (i.isMock) "simulated" else "receiver",
                if (i.isMock) CheckStatus.FAIL else CheckStatus.OK,
            ),
        )
        add(
            when (i.powerSave) {
                PowerSaveLocation.UNRESTRICTED -> DiagnosisCheck("Battery saver", "no effect on location", CheckStatus.OK)
                PowerSaveLocation.GNSS_OFF_SCREEN_OFF, PowerSaveLocation.ALL_OFF_SCREEN_OFF -> DiagnosisCheck(
                    "Battery saver", "location off with screen off", CheckStatus.WARN,
                    "Background mode cannot record with the screen off while battery saver is on.",
                )
                PowerSaveLocation.FOREGROUND_ONLY -> DiagnosisCheck(
                    "Battery saver", "foreground apps only", CheckStatus.WARN,
                    "Background mode stops working while battery saver is on.",
                )
                PowerSaveLocation.THROTTLED_SCREEN_OFF -> DiagnosisCheck(
                    "Battery saver", "slowed with screen off", CheckStatus.INFO,
                )
            },
        )
        add(
            when {
                i.airplaneMode -> DiagnosisCheck(
                    "Data for assistance", "airplane mode", CheckStatus.INFO,
                    "Without data the receiver cannot download orbits (A-GNSS); cold starts take minutes.",
                )
                i.dataConnection == true -> DiagnosisCheck("Data for assistance", "available", CheckStatus.OK)
                i.dataConnection == false -> DiagnosisCheck(
                    "Data for assistance", "none", CheckStatus.INFO,
                    "Without data the receiver cannot download orbits (A-GNSS); cold starts take minutes.",
                )
                else -> DiagnosisCheck("Data for assistance", "unknown", CheckStatus.INFO)
            },
        )
        add(
            DiagnosisCheck(
                "Satellites heard", "${i.satellitesHeard}",
                when {
                    i.satellitesHeard >= AlmanacStatus.SATELLITES_FOR_FIX -> CheckStatus.OK
                    i.satellitesHeard > 0 -> CheckStatus.WARN
                    else -> CheckStatus.FAIL
                },
                "At least ${AlmanacStatus.SATELLITES_FOR_FIX} needed.",
            ),
        )
        add(
            DiagnosisCheck(
                "Usable signals", "${i.satellitesStrong}",
                if (i.satellitesStrong >= AlmanacStatus.SATELLITES_FOR_FIX) CheckStatus.OK else CheckStatus.WARN,
                "Satellites at ${DiagnosisInput.STRONG_CN0.toInt()} dB-Hz or more, enough to decode their data.",
            ),
        )
        add(
            DiagnosisCheck(
                "Orbital data",
                when (i.readiness) {
                    AlmanacReadiness.HOT -> "hot · ephemeris for ${i.withEphemeris}"
                    AlmanacReadiness.WARM -> "warm · almanac only"
                    AlmanacReadiness.COLD -> "cold · nothing stored"
                    AlmanacReadiness.UNKNOWN -> "unknown"
                },
                when (i.readiness) {
                    AlmanacReadiness.HOT -> CheckStatus.OK
                    AlmanacReadiness.WARM -> CheckStatus.INFO
                    AlmanacReadiness.COLD -> CheckStatus.WARN
                    AlmanacReadiness.UNKNOWN -> CheckStatus.INFO
                },
            ),
        )
        add(
            DiagnosisCheck(
                "Geometry", i.pdop?.let { String.format(java.util.Locale.US, "PDOP %.1f", it) } ?: "needs a fix",
                when {
                    i.pdop == null -> CheckStatus.INFO
                    i.pdop > POOR_PDOP -> CheckStatus.WARN
                    else -> CheckStatus.OK
                },
            ),
        )
        add(
            when {
                i.firstFixMs != null -> DiagnosisCheck("Time to first fix", formatDuration(i.firstFixMs), CheckStatus.OK)
                i.searchingMs != null -> DiagnosisCheck(
                    "Searching for", formatDuration(i.searchingMs),
                    if (i.searchingMs > expectedMs(i.readiness, i.dataConnection)) CheckStatus.WARN else CheckStatus.INFO,
                    "Expected within ${formatDuration(expectedMs(i.readiness, i.dataConnection))} for this start.",
                )
                else -> DiagnosisCheck("Searching for", "—", CheckStatus.INFO)
            },
        )
    }

    /** "4.7 s", "38 s", "1 min 20 s", "12 min" — the same precision as the timing section. */
    fun formatDuration(ms: Long): String {
        val s = ms / 1_000
        return when {
            ms < 10_000 -> String.format(java.util.Locale.US, "%.1f s", ms / 1_000.0)
            s < 60 -> "$s s"
            s % 60 == 0L -> "${s / 60} min"
            else -> "${s / 60} min ${s % 60} s"
        }
    }

    const val POOR_PDOP = 6.0
    const val NO_SIGNAL_GRACE_MS = 20_000L
}
