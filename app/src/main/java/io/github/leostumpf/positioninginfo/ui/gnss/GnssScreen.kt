// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.AlmanacStatus
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.domain.formatDuration
import io.github.leostumpf.positioninginfo.ui.common.ConfirmDialog
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.rememberLogTimeFormat
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.sky.SkyUiState
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.sky.skyEventItems
import io.github.leostumpf.positioninginfo.ui.sky.skyPlotItems
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.PageTitleStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color
import java.util.Date

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
    val heard = state.signals.filter { it.satellite.cn0DbHz > 0f }
    val unheard = state.signals.filter { it.satellite.cn0DbHz <= 0f }

    PageScaffold(Page.GNSS, modifier) {
        item {
            val fixed = state.isFixed()
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two separate facts: what the receiver is doing now, and how fast its next
                // start would be. Mixing them made "ready" appear even while it was fixed.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (fixed) StatusBadge("Fix", Tone.GOOD) else StatusBadge("No fix", Tone.NEUTRAL)
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
        state.diagnosis?.let { d -> item { DiagnosisCard(d, Modifier.padding(top = 16.dp)) } }
        item {
            TileRow(
                listOf(
                    { m -> StatTile("visible", state.visible.toString(), m) },
                    { m -> StatTile("almanac", state.withAlmanac.toString(), m) },
                    { m ->
                        StatTile(
                            "ephemeris",
                            if (state.ephemerisUnavailable) "—" else state.withEphemeris.toString(),
                            m,
                        )
                    },
                    { m ->
                        StatTile(
                            "in fix",
                            state.usedInFix.toString(),
                            m,
                            tone = if (state.usedInFix > 0) Tone.GOOD else null,
                        )
                    },
                ),
                Modifier.padding(top = 16.dp),
            )
        }
        if (state.ephemerisUnavailable) {
            item {
                Box(Modifier.padding(top = 12.dp)) {
                    Notice(
                        "This phone computes fixes while reporting no ephemeris at all, which cannot be " +
                            "true — its driver does not publish that flag, so the count is withheld.",
                        tone = Tone.DEGRADED,
                    )
                }
            }
        }

        if (state.visible == 0) {
            item {
                Box(Modifier.padding(top = 32.dp)) {
                    Notice(
                        if (!state.gpsEnabled) {
                            "Location is switched off, so the receiver is not running."
                        } else {
                            "No satellites reported yet. Indoors this is normal — GNSS signals need a clear view of the sky."
                        },
                    )
                }
            }
        }

        skyPlotItems(sky, onToggleCompass, onToggleMap, onToggleShowPaths, onClearPaths)

        if (state.signals.isNotEmpty()) {
            section("Satellites · ${state.visible}", trailing = "C/N₀ · A E")
            items(
                heard,
                key = { it.key },
            ) { SatelliteRow(it.satellite, state.details[it.baseKey]) { selectedKey = it.baseKey } }
            if (unheard.isNotEmpty()) {
                if (showUnheard) {
                    items(
                        unheard,
                        key = { it.key },
                    ) { SatelliteRow(it.satellite, state.details[it.baseKey]) { selectedKey = it.baseKey } }
                }
                item {
                    QuietButton(
                        if (showUnheard) {
                            "Hide the ${unheard.size} not heard"
                        } else {
                            "Show ${unheard.size} not heard (almanac only)"
                        },
                        onClick = { showUnheard = !showUnheard },
                    )
                }
            }
            item {
                Note(
                    "Filled dot: used in the fix. Ring: heard. Faint: known only from the almanac. The four " +
                        "ticks are the acquisition steps — code lock, bit sync, frame sync, time decoded; a " +
                        "satellite is usable once all four are done. Tap a row for details.",
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        skyEventItems(sky)

        section("Timing")
        item { TimingRow("Time to first fix", state.timing.firstFixText(state.gpsEnabled)) }
        item { TimingRow("Phone clock vs GNSS", state.timing.clockText()) }
        val networkVsGnss = state.timing.networkVsGnssText()
        item { TimingRow("Phone clock vs network", state.timing.networkClockText(), divider = networkVsGnss != null) }
        networkVsGnss?.let { item { TimingRow("Network time vs GNSS", it, divider = false) } }

        if (state.history.size > 1) {
            section("Last 30 minutes")
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HistoryChart("Satellites in fix", state.history, { it.usedInFix.toFloat() }, "", decimals = 0)
                    HistoryChart("Mean signal, C/N₀", state.history, { it.meanCn0 }, " dB-Hz", decimals = 0)
                    HistoryChart("Accuracy", state.history, { it.accuracyM }, " m", decimals = 1, lowerIsBetter = true)
                }
            }
        }
        if (state.ttffLog.isNotEmpty()) {
            section("Recent first fixes", trailing = "stored on this phone")
            val shown = state.ttffLog.takeLast(TTFF_SHOWN).reversed()
            shown.forEachIndexed { i, e ->
                item {
                    val slow = e.ttffMs > FixDiagnosis.expectedMs(e.startType)
                    ValueRow(
                        rememberLogTimeFormat().format(Date(e.utcMs)),
                        formatDuration(e.ttffMs),
                        // Slow is said as well as coloured, for anyone who cannot tell the two apart.
                        detail = e.startType.startLabel() + if (slow) " · slower than usual" else "",
                        valueColor = if (slow) Palette.Degraded else Palette.Good,
                        divider = i < shown.lastIndex,
                    )
                }
            }
        }

        section("Assistance")
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Cold start", onClick = { confirmColdStart = true }, modifier = Modifier.weight(1f))
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

        section("Phone settings", trailing = "read only")
        item { PhoneSettingsRows(state.settings) }

        if (state.perConstellation.isNotEmpty()) {
            section("Constellations")
            item { ConstellationHeader() }
            items(state.perConstellation, key = { it.constellation.name }) {
                ConstellationRow(it, last = it == state.perConstellation.last())
            }
        }
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

@Composable
private fun TimingRow(label: String, value: TimingText, divider: Boolean = true) {
    ValueRow(
        label,
        value.text,
        valueColor = when (value.tone) {
            TimingTone.GOOD -> Palette.Good
            TimingTone.PENDING, TimingTone.WARN -> Palette.Degraded
            TimingTone.NONE -> Palette.TextSecondary
        },
        divider = divider,
    )
}

private fun AlmanacReadiness.badge(): String = when (this) {
    AlmanacReadiness.HOT -> "Hot start"
    AlmanacReadiness.WARM -> "Warm start"
    AlmanacReadiness.COLD -> "Cold start"
    AlmanacReadiness.UNKNOWN -> "Waiting"
}

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

/** The newest first fixes shown; the log keeps more. */
private const val TTFF_SHOWN = 5

private fun AlmanacReadiness.startLabel(): String = when (this) {
    AlmanacReadiness.HOT -> "hot start"
    AlmanacReadiness.WARM -> "warm start"
    AlmanacReadiness.COLD -> "cold start"
    AlmanacReadiness.UNKNOWN -> "start type unknown"
}
