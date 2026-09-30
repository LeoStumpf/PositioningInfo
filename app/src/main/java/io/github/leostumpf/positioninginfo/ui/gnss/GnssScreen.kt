// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.ui.common.ConfirmDialog
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.sky.skyEventItems
import io.github.leostumpf.positioninginfo.ui.sky.skyPlotItems
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color

/**
 * What orbital data the receiver is holding, and therefore how quickly it can fix.
 *
 * The headline is the readiness verdict rather than the raw counts, because that is the
 * question the numbers actually answer.
 */
@Composable
fun GnssScreen(
    state: GnssUiState,
    onColdStart: () -> Unit,
    onFetchAssistance: () -> Unit,
    sky: SkyUiState,
    onToggleCompass: () -> Unit,
    onToggleMap: () -> Unit,
    onToggleShowPaths: () -> Unit,
    onClearPaths: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmColdStart by rememberSaveable { mutableStateOf(false) }
    var showUnheard by rememberSaveable { mutableStateOf(false) }
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }

    PageScaffold(Page.GNSS, modifier) {
        item { ReadinessHeader(state, Modifier.padding(top = 16.dp)) }
        state.diagnosis?.let { d -> item { DiagnosisCard(d, Modifier.padding(top = 16.dp)) } }
        orbitalDataItems(state)
        skyPlotItems(sky, onToggleCompass, onToggleMap, onToggleShowPaths, onClearPaths)
        satelliteListItems(state, showUnheard, { showUnheard = !showUnheard }, { selectedKey = it })
        skyEventItems(sky)
        timingSection(state)
        historySection(state)
        firstFixLogSection(state)
        assistanceSection(state, onColdStart = { confirmColdStart = true }, onFetchAssistance = onFetchAssistance)
        section("Phone settings", trailing = "read only")
        item { PhoneSettingsRows(state.settings) }
        constellationSection(state)
    }

    selectedKey?.let { key ->
        val row = state.signals.firstOrNull { it.baseKey == key }
        // Gone from the list: close the sheet, from an effect rather than mid-composition.
        if (row == null) {
            LaunchedEffect(key) { selectedKey = null }
        } else {
            SatelliteSheet(
                row,
                state.details[key],
                siblings = state.signals.filter {
                    it.satellite.constellation == row.satellite.constellation && it.satellite.svid == row.satellite.svid
                },
                onDismiss = { selectedKey = null },
            )
        }
    }

    if (confirmColdStart) {
        ConfirmDialog(
            title = "Clear aiding data?",
            text = "Deletes the receiver's stored almanac, ephemeris, position and time. The next fix " +
                "will be slower for every app on this phone until the data is downloaded again.",
            confirmLabel = "Clear",
            onConfirm = onColdStart,
            onDismiss = { confirmColdStart = false },
        )
    }
}

/** How many satellites the receiver holds orbits for; notes when that says little. */
private fun LazyListScope.orbitalDataItems(state: GnssUiState) {
    val ephemeris = if (state.ephemerisUnavailable) "—" else state.withEphemeris.toString()
    val inFixTone = if (state.usedInFix > 0) Tone.GOOD else null
    item {
        TileRow(
            listOf(
                { m -> StatTile("visible", state.visible.toString(), m) },
                { m -> StatTile("almanac", state.withAlmanac.toString(), m) },
                { m -> StatTile("ephemeris", ephemeris, m) },
                { m -> StatTile("in fix", state.usedInFix.toString(), m, tone = inFixTone) },
            ),
            Modifier.padding(top = 16.dp),
        )
    }
    if (state.ephemerisUnavailable) {
        item {
            Notice(
                "This phone computes fixes while reporting no ephemeris at all, which cannot be " +
                    "true — its driver does not publish that flag, so the count is withheld.",
                Modifier.padding(top = 12.dp),
                tone = Tone.DEGRADED,
            )
        }
    }
    if (state.visible == 0) {
        item {
            Notice(
                if (!state.gpsEnabled) {
                    "Location is switched off, so the receiver is not running."
                } else {
                    "No satellites reported yet. Indoors this is normal — GNSS signals need a clear view of the sky."
                },
                Modifier.padding(top = 32.dp),
            )
        }
    }
}

/** Clear the stored orbits for a real cold start, or ask Android for fresh ones. */
private fun LazyListScope.assistanceSection(
    state: GnssUiState,
    onColdStart: () -> Unit,
    onFetchAssistance: () -> Unit,
) {
    section("Assistance")
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Cold start", onClick = onColdStart, modifier = Modifier.weight(1f))
            SecondaryButton("Fetch A-GNSS", onClick = onFetchAssistance, modifier = Modifier.weight(1f))
        }
    }
    item {
        Note(
            state.assistanceMessage
                ?: "Clear the stored orbits to watch a real cold start, or ask Android to download fresh ones.",
            color = if (state.assistanceMessage != null) Palette.TextPrimary else Palette.TextTertiary,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
