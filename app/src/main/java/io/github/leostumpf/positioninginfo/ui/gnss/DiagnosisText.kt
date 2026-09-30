// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.CheckKind
import io.github.leostumpf.positioninginfo.domain.Diagnosis
import io.github.leostumpf.positioninginfo.domain.DiagnosisCheck
import io.github.leostumpf.positioninginfo.domain.DiagnosisInput
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import io.github.leostumpf.positioninginfo.domain.Verdict
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.domain.formatDuration
import java.util.Locale

/*
 * "Why no fix?" in words. FixDiagnosis decides; everything a reader sees is written here, so
 * this is the one place a translation changes. Worded as inference: the receiver never says
 * why it has no fix.
 */

private const val NEEDED = AlmanacStatus.SATELLITES_FOR_FIX

/** The one-line answer. */
fun Diagnosis.title(): String = when (verdict) {
    Verdict.LOCATION_OFF -> "Location is switched off"
    Verdict.SIMULATED -> "The position is simulated"
    Verdict.POOR_GEOMETRY -> "Fixed, but with poor geometry"
    Verdict.ALL_PASS -> "All checks pass"
    Verdict.NO_SIGNALS -> "No satellite signals"
    Verdict.TOO_FEW_HEARD -> "Only ${input.satellitesHeard.counted("satellite")} heard"
    Verdict.SIGNALS_TOO_WEAK -> "Signals too weak"
    Verdict.FIX_LOST -> "Fix lost, reacquiring"
    Verdict.LEARNING_ORBITS -> "Learning orbits from the satellites"
    Verdict.SLOWER_THAN_EXPECTED -> "Taking longer than expected"
    Verdict.ACQUIRING -> "Acquiring"
}

/** A sentence or two on what the verdict means and what helps. */
fun Diagnosis.detail(): String {
    val i = input
    val searching = i.searchingMs ?: 0L
    return when (verdict) {
        Verdict.LOCATION_OFF ->
            "The receiver is not running at all. Switch location on in the quick settings."

        Verdict.SIMULATED ->
            "A mock-location app is supplying positions, so the receiver's own fix is not what apps see."

        Verdict.POOR_GEOMETRY ->
            "The satellites in use are bunched together (PDOP ${String.format(Locale.US, "%.1f", i.pdop)}), " +
                "so the position is less precise than the signals would allow. Open sky helps."

        Verdict.ALL_PASS ->
            "Fixed on ${i.usedInFix.counted("satellite")}." +
                (i.firstFixMs?.let { " This session's first fix took ${formatDuration(it)}." } ?: "")

        Verdict.NO_SIGNALS ->
            "Nothing is heard at all — almost always a roof, walls or a car body in the way. " +
                "GNSS signals need a view of the sky; try near a window or outside."

        Verdict.TOO_FEW_HEARD ->
            "A fix needs at least $NEEDED: three for position, one for the receiver's own clock. " +
                "More of the sky has to be visible."

        Verdict.SIGNALS_TOO_WEAK ->
            "${i.satellitesHeard.counted("satellite")} ${if (i.satellitesHeard == 1) "is" else "are"} heard, " +
                "but only ${i.satellitesStrong} strongly enough to decode their data. Typical indoors or under " +
                "dense trees."

        Verdict.FIX_LOST ->
            "The receiver had a fix this session and still holds the orbits, so it usually " +
                "recovers within seconds once enough of the sky is in view again."

        Verdict.LEARNING_ORBITS ->
            "No orbital data is stored and there is no data connection for assistance, so the " +
                "receiver must download orbits from the satellites themselves — up to 12 minutes " +
                "with a clear view of the sky."

        Verdict.SLOWER_THAN_EXPECTED ->
            "Searching for ${formatDuration(searching)}; a ${i.readiness.name.lowercase()} start usually " +
                "fixes within ${formatDuration(expectedMs)}. Signals are probably marginal."

        Verdict.ACQUIRING ->
            "Signals are there and the receiver is working through them. Expected within " +
                "${formatDuration(expectedMs)} of starting (${formatDuration(searching)} so far)."
    }
}

/** The check's name in the chain; the timing row reads "Searching for" until the first fix. */
fun DiagnosisCheck.label(input: DiagnosisInput): String = when (kind) {
    CheckKind.LOCATION -> "Location service"
    CheckKind.SOURCE -> "Position source"
    CheckKind.BATTERY_SAVER -> "Battery saver"
    CheckKind.DATA -> "Data for assistance"
    CheckKind.SATELLITES_HEARD -> "Satellites heard"
    CheckKind.USABLE_SIGNALS -> "Usable signals"
    CheckKind.ORBITS -> "Orbital data"
    CheckKind.GEOMETRY -> "Geometry"
    CheckKind.TIMING -> if (input.firstFixMs != null) "Time to first fix" else "Searching for"
}

/** What the check found, in a few words, e.g. "on" or "warm · almanac only". */
fun DiagnosisCheck.value(i: DiagnosisInput): String = when (kind) {
    CheckKind.LOCATION -> if (i.gpsEnabled) "on" else "off"

    CheckKind.SOURCE -> if (i.isMock) "simulated" else "receiver"

    CheckKind.BATTERY_SAVER -> when (i.powerSave) {
        PowerSaveLocation.UNRESTRICTED -> "no effect on location"
        PowerSaveLocation.GNSS_OFF_SCREEN_OFF, PowerSaveLocation.ALL_OFF_SCREEN_OFF -> "location off with screen off"
        PowerSaveLocation.FOREGROUND_ONLY -> "foreground apps only"
        PowerSaveLocation.THROTTLED_SCREEN_OFF -> "slowed with screen off"
    }

    CheckKind.DATA -> when {
        i.airplaneMode -> "airplane mode"
        i.dataConnection == true -> "available"
        i.dataConnection == false -> "none"
        else -> "unknown"
    }

    CheckKind.SATELLITES_HEARD -> "${i.satellitesHeard}"

    CheckKind.USABLE_SIGNALS -> "${i.satellitesStrong}"

    CheckKind.ORBITS -> when (i.readiness) {
        AlmanacReadiness.HOT -> "hot · ephemeris for ${i.withEphemeris}"
        AlmanacReadiness.WARM -> "warm · almanac only"
        AlmanacReadiness.COLD -> "cold · nothing stored"
        AlmanacReadiness.UNKNOWN -> "unknown"
    }

    CheckKind.GEOMETRY -> i.pdop?.let { String.format(Locale.US, "PDOP %.1f", it) } ?: "needs a fix"

    CheckKind.TIMING -> (i.firstFixMs ?: i.searchingMs)?.let(::formatDuration) ?: "—"
}

/** What to know about a check, where there is something. */
fun DiagnosisCheck.hint(i: DiagnosisInput, expectedMs: Long): String? = when (kind) {
    CheckKind.BATTERY_SAVER -> when (i.powerSave) {
        PowerSaveLocation.GNSS_OFF_SCREEN_OFF, PowerSaveLocation.ALL_OFF_SCREEN_OFF ->
            "Background mode cannot record with the screen off while battery saver is on."

        PowerSaveLocation.FOREGROUND_ONLY -> "Background mode stops working while battery saver is on."

        else -> null
    }

    CheckKind.DATA ->
        if (i.airplaneMode || i.dataConnection == false) {
            "Without data the receiver cannot download orbits (A-GNSS); cold starts take minutes."
        } else {
            null
        }

    CheckKind.SATELLITES_HEARD -> "At least $NEEDED needed."

    CheckKind.USABLE_SIGNALS -> "Satellites at ${DiagnosisInput.STRONG_CN0.toInt()} dB-Hz or more, enough to " +
        "decode their data."

    CheckKind.TIMING ->
        if (i.firstFixMs == null && i.searchingMs != null) {
            "Expected within ${formatDuration(
                expectedMs,
            )} for this start."
        } else {
            null
        }

    else -> null
}
