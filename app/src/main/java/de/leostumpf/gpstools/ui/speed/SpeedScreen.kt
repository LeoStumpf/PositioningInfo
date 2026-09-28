// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.speed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import de.leostumpf.gpstools.domain.FixFreshness
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.ErrorRed
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.SpeedDisplayStyle
import de.leostumpf.gpstools.ui.theme.StatValueStyle
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
import de.leostumpf.gpstools.ui.theme.WarnAmber

/**
 * The whole app, in one screen: speed, session statistics, and a status line honest enough
 * that the reading above it can be trusted.
 *
 * Gestures rather than chrome, to keep the screen free of anything that is not data:
 * tap the unit to cycle it, long-press the statistics to reset the session, long-press the
 * status line for the About/licence dialog.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeedScreen(
    state: SpeedUiState,
    onCycleUnit: () -> Unit,
    onResetSession: () -> Unit,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        // Landscape in a car mount leaves far less height than portrait, and a number sized
        // for portrait collides with the statistics row there. The readout is sized from the
        // height actually available rather than from the orientation, so unusual screens and
        // split-screen windows are handled by the same rule.
        val readoutSize = (maxHeight * 0.22f).value.coerceIn(48f, 108f).sp

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpeedReadout(state = state, fontSize = readoutSize, onCycleUnit = onCycleUnit)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SessionStatsRow(state = state, onResetSession = onResetSession)
            Spacer(Modifier.height(14.dp))
            StatusLine(state = state, onShowAbout = onShowAbout)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedReadout(
    state: SpeedUiState,
    fontSize: TextUnit,
    onCycleUnit: () -> Unit,
) {
    // A stale fix stays on screen but visibly recedes, so a frozen number can never be
    // mistaken for a live one.
    val targetAlpha = if (state.freshness == FixFreshness.STALE) 0.45f else 1f
    val alpha by animateFloatAsState(targetValue = targetAlpha, label = "staleFade")

    val speedText = if (state.isAcquiring) NO_VALUE else formatSpeed(state.speedMps, state.unit)

    Text(
        text = speedText,
        style = SpeedDisplayStyle.copy(fontSize = fontSize, lineHeight = fontSize * 1.08f),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .alpha(alpha)
            .semantics {
                contentDescription = "Speed $speedText ${state.unit.symbol}"
            },
    )

    val interaction = remember { MutableInteractionSource() }
    Text(
        text = state.unit.symbol,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Change speed unit",
                onClick = onCycleUnit,
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
    )

    if (state.isAcquiring) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.gpsEnabled) "Acquiring GPS signal…" else "Location is switched off",
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SessionStatsRow(state: SpeedUiState, onResetSession: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
    ) {
        StatCell(label = "MAX", value = formatSpeed(state.maxMps, state.unit))
        Spacer(Modifier.width(36.dp))
        StatCell(label = "AVG", value = formatSpeed(state.averageMps, state.unit))
        Spacer(Modifier.width(20.dp))
        TextButton(onClick = onResetSession) {
            Text("RESET", style = StatusLineStyle)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = StatValueStyle,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StatusLine(state: SpeedUiState, onShowAbout: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {},
                onLongClickLabel = "About GPS Tools",
                onLongClick = onShowAbout,
            )
            .padding(vertical = 10.dp, horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(color = state.statusColor(), shape = CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.statusText(),
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun SpeedUiState.statusColor(): Color = when {
    !gpsEnabled -> ErrorRed
    freshness == FixFreshness.FRESH -> OkGreen
    freshness == FixFreshness.STALE -> WarnAmber
    else -> DimGrey
}

private fun SpeedUiState.statusText(): String = when {
    !gpsEnabled -> "LOCATION OFF"
    else -> {
        val label = when (freshness) {
            FixFreshness.FRESH -> "FIX"
            FixFreshness.STALE -> "STALE"
            FixFreshness.EXPIRED -> "NO FIX"
        }
        val accuracy = horizontalAccuracyM?.let { "   ${formatAccuracy(it)}" }.orEmpty()
        "$label$accuracy   $satellitesUsed/$satellitesVisible sats"
    }
}
