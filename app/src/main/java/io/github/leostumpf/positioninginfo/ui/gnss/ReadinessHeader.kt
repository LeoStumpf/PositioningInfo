// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.PageTitleStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color

/**
 * The top of the GNSS page: whether there is a fix now and how fast the next start would be —
 * two separate facts, since mixing them made "ready" appear even while it was fixed — then
 * the headline and why.
 */
@Composable
internal fun ReadinessHeader(state: GnssUiState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.isFixed()) StatusBadge("Fix", Tone.GOOD) else StatusBadge("No fix", Tone.NEUTRAL)
            StatusBadge("Next start: ${state.readiness.badge()}", state.readiness.tone())
        }
        Text(
            headline(state),
            style = PageTitleStyle.copy(fontSize = 28.sp, lineHeight = 34.sp),
            color = Palette.TextPrimary,
        )
        Text(
            explanationFor(state),
            style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = Palette.TextSecondary,
        )
    }
}

private fun AlmanacReadiness.badge(): String = when (this) {
    AlmanacReadiness.HOT -> "Hot start"
    AlmanacReadiness.WARM -> "Warm start"
    AlmanacReadiness.COLD -> "Cold start"
    AlmanacReadiness.UNKNOWN -> "Waiting"
}

/** How the readiness badge is tinted. */
internal fun AlmanacReadiness.tone(): Tone = when (this) {
    AlmanacReadiness.HOT -> Tone.GOOD
    AlmanacReadiness.WARM -> Tone.DEGRADED
    AlmanacReadiness.COLD -> Tone.BAD
    AlmanacReadiness.UNKNOWN -> Tone.NEUTRAL
}

/** Fixed means a position is being computed right now: four satellites are the minimum. */
private fun GnssUiState.isFixed() = gpsEnabled && usedInFix >= AlmanacStatus.SATELLITES_FOR_FIX

private fun headline(state: GnssUiState): String = when {
    state.isFixed() -> "Fixed on ${state.usedInFix.counted("satellite")}"
    state.readiness == AlmanacReadiness.HOT -> "Ready to fix"
    state.readiness == AlmanacReadiness.WARM -> "Almost ready"
    state.readiness == AlmanacReadiness.COLD -> "Searching blind"
    else -> "Waiting for the receiver"
}

/**
 * Says why the receiver is in the state it is. "Ready" is reached by two routes — fixing
 * right now, or holding enough precise orbits to fix shortly — and quoting the ephemeris
 * count for a device that got there by fixing would contradict the figure beside it.
 */
private fun explanationFor(state: GnssUiState): String = when {
    state.isFixed() -> when {
        state.ephemerisUnavailable -> "The receiver is computing a position right now."

        state.readiness == AlmanacReadiness.HOT ->
            "The receiver is computing a position right now. It also holds precise orbits for " +
                "${state.withEphemeris.counted("satellite")}, so after a restart it would fix again within seconds."

        state.readiness == AlmanacReadiness.WARM ->
            "The receiver is computing a position right now, but holds precise orbits for only a " +
                "few satellites; a restart would take about half a minute."

        else ->
            "The receiver is computing a position right now."
    }

    else -> when (state.readiness) {
        AlmanacReadiness.HOT ->
            "Precise orbits (ephemeris) are held for at least ${AlmanacStatus.SATELLITES_FOR_FIX} satellites. " +
                "A fix should follow within seconds of hearing them."

        AlmanacReadiness.WARM ->
            "Coarse orbits (almanac) are held, but not enough precise ones. The receiver knows where to " +
                "look and needs about half a minute."

        AlmanacReadiness.COLD ->
            "Too little orbital data to fix quickly. The receiver must search blindly; a full almanac " +
                "takes up to 12 minutes."

        AlmanacReadiness.UNKNOWN -> "No report from the GNSS receiver yet."
    }
}
