// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.gnss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.data.model.SatelliteInfo
import de.leostumpf.gpstools.domain.AlmanacReadiness
import de.leostumpf.gpstools.domain.AlmanacStatus
import de.leostumpf.gpstools.domain.ConstellationSummary
import de.leostumpf.gpstools.domain.SignalBand
import de.leostumpf.gpstools.domain.band
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.ErrorRed
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
import de.leostumpf.gpstools.ui.theme.WarnAmber

/**
 * What orbital data the receiver is holding, and therefore how quickly it can fix.
 *
 * The headline is the readiness verdict rather than the raw counts, because that is the
 * question the numbers actually answer: a receiver with an almanac but no ephemeris will
 * fix in half a minute, one with neither may take several.
 */
@Composable
fun GnssScreen(
    state: GnssUiState,
    onColdStart: () -> Unit,
    onFetchAssistance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            top = 24.dp,
            bottom = 48.dp,
        ),
    ) {
        item { Primer(Glossary.gnss) }
        item { Spacer(Modifier.height(20.dp)) }
        item { ReadinessHeader(state) }
        item { Spacer(Modifier.height(20.dp)) }
        item { OrbitalDataCounts(state) }
        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("TIMING") }
        item { Spacer(Modifier.height(10.dp)) }
        item { Timing(state) }
        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("ASSISTANCE") }
        item { Spacer(Modifier.height(10.dp)) }
        item { Assistance(state, onColdStart, onFetchAssistance) }

        if (state.ephemerisUnavailable) {
            item { Spacer(Modifier.height(16.dp)) }
            item { FlagsUnavailableNote() }
        }

        if (state.perConstellation.isNotEmpty()) {
            item { Spacer(Modifier.height(28.dp)) }
            item { SectionLabel("CONSTELLATIONS") }
            item { Spacer(Modifier.height(10.dp)) }
            item { ConstellationHeaderRow() }
            items(state.perConstellation, key = { it.constellation.name }) {
                ConstellationRow(it)
            }
        }

        if (state.signals.isNotEmpty()) {
            item { Spacer(Modifier.height(28.dp)) }
            item { SectionLabel("SATELLITES") }
            item { Spacer(Modifier.height(4.dp)) }
            item { SatelliteLegend() }
            item { Spacer(Modifier.height(8.dp)) }
            items(state.signals, key = { it.key }) { SatelliteRow(it.satellite) }
        }

        if (state.visible == 0) {
            item { Spacer(Modifier.height(32.dp)) }
            item { EmptyNote(state) }
        }
    }
}

@Composable
private fun ReadinessHeader(state: GnssUiState) {
    Column {
        Text(
            text = state.readiness.headline(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            color = state.readiness.tint(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = explanationFor(state),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrbitalDataCounts(state: GnssUiState) {
    Row(modifier = Modifier.fillMaxWidth()) {
        CountCell("VISIBLE", state.visible.toString(), Modifier.weight(1f))
        CountCell("ALMANAC", state.withAlmanac.toString(), Modifier.weight(1f))
        CountCell(
            label = "EPHEMERIS",
            value = if (state.ephemerisUnavailable) "--" else state.withEphemeris.toString(),
            modifier = Modifier.weight(1f),
        )
        CountCell("IN FIX", state.usedInFix.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun CountCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = label,
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How long this session's first fix actually took, so the readiness verdict above can be
 * checked against reality, and how far the phone's clock is from GNSS time.
 */
@Composable
private fun Timing(state: GnssUiState) {
    Column {
        TimingRow("Time to first fix", state.timing.firstFixText(state.gpsEnabled))
        TimingRow("Phone clock", state.timing.clockText())
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Measured afresh each time the app comes to the front. Hot starts fix in " +
                "seconds, warm in about half a minute, cold in up to 12 minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = DimGrey,
        )
    }
}

@Composable
private fun TimingRow(label: String, value: TimingText) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.text,
            style = StatusLineStyle,
            fontWeight = FontWeight.Medium,
            color = when (value.tone) {
                TimingTone.GOOD -> OkGreen
                TimingTone.PENDING, TimingTone.WARN -> WarnAmber
                TimingTone.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * Cold start and A-GNSS download, so the difference assistance data makes can be seen
 * directly: clear everything, watch the flags empty and the timer run, then fetch.
 */
@Composable
private fun Assistance(state: GnssUiState, onColdStart: () -> Unit, onFetchAssistance: () -> Unit) {
    var confirmColdStart by rememberSaveable { mutableStateOf(false) }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { confirmColdStart = true }, modifier = Modifier.weight(1f)) {
                Text("Cold start")
            }
            OutlinedButton(onClick = onFetchAssistance, modifier = Modifier.weight(1f)) {
                Text("Fetch A-GNSS data")
            }
        }
        state.assistanceMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(text = it, style = StatusLineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Phones download predicted orbits and the time over Wi-Fi or mobile data " +
                "instead of waiting minutes for the satellites to broadcast them.",
            style = MaterialTheme.typography.bodySmall,
            color = DimGrey,
        )
    }

    if (confirmColdStart) {
        AlertDialog(
            onDismissRequest = { confirmColdStart = false },
            title = { Text("Clear aiding data?") },
            text = {
                Text(
                    "Deletes the receiver's stored almanac, ephemeris, position and time. " +
                        "The next fix will be slower for every app on this phone until the " +
                        "data is downloaded again.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmColdStart = false; onColdStart() }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmColdStart = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FlagsUnavailableNote() {
    Text(
        text = "This phone is computing fixes while reporting no ephemeris at all, which " +
            "cannot be true — its GNSS driver simply does not publish that flag, so the " +
            "count is withheld. The almanac figures below are reported normally.",
        style = MaterialTheme.typography.bodySmall,
        color = WarnAmber,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x22FFB300), RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = StatusLineStyle,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ConstellationHeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        HeaderCell("SYSTEM", Modifier.weight(2.2f))
        HeaderCell("VIS", Modifier.weight(1f))
        HeaderCell("ALM", Modifier.weight(1f))
        HeaderCell("EPH", Modifier.weight(1f))
        HeaderCell("FIX", Modifier.weight(1f))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = StatusLineStyle,
        color = DimGrey,
        modifier = modifier,
    )
}

@Composable
private fun ConstellationRow(summary: ConstellationSummary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2.2f)) {
            Text(
                text = summary.constellation.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = summary.constellation.operator,
                style = StatusLineStyle,
                color = DimGrey,
            )
        }
        ValueCell(summary.visible.toString(), Modifier.weight(1f))
        ValueCell(summary.almanac.toString(), Modifier.weight(1f))
        ValueCell(summary.ephemeris.toString(), Modifier.weight(1f))
        ValueCell(
            text = summary.usedInFix.toString(),
            modifier = Modifier.weight(1f),
            color = if (summary.usedInFix > 0) OkGreen else MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ValueCell(
    text: String,
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
) {
    Text(
        text = text,
        style = StatusLineStyle,
        color = color,
        modifier = modifier,
    )
}

@Composable
private fun SatelliteLegend() {
    Text(
        text = "A = almanac   E = ephemeris   filled = used in fix",
        style = StatusLineStyle,
        color = DimGrey,
    )
}

@Composable
private fun SatelliteRow(satellite: SatelliteInfo) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(
                    color = if (satellite.usedInFix) OkGreen else DimGrey,
                    shape = CircleShape,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${satellite.constellation.label} ${satellite.svid}" +
                satellite.band?.shortLabel()?.let { " $it" }.orEmpty(),
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(2f),
        )
        SignalBar(cn0DbHz = satellite.cn0DbHz, modifier = Modifier.weight(1.6f))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "%.0f".format(satellite.cn0DbHz),
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Flag(text = "A", on = satellite.hasAlmanac)
        Spacer(Modifier.width(6.dp))
        Flag(text = "E", on = satellite.hasEphemeris)
    }
}

/**
 * Carrier-to-noise density as a bar. 45 dB-Hz is about as good as a phone antenna gets,
 * so the scale tops out there rather than at some theoretical maximum.
 */
@Composable
private fun SignalBar(cn0DbHz: Float, modifier: Modifier = Modifier) {
    val fraction = (cn0DbHz / GOOD_SIGNAL_DB_HZ).coerceIn(0f, 1f)
    Box(
        modifier
            .height(5.dp)
            .background(Color(0xFF2A2A2A), RoundedCornerShape(3.dp)),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .background(signalColour(cn0DbHz), RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun Flag(text: String, on: Boolean) {
    Text(
        text = text,
        style = StatusLineStyle,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3A),
    )
}

@Composable
private fun EmptyNote(state: GnssUiState) {
    Text(
        text = if (!state.gpsEnabled) {
            "Location is switched off, so the receiver is not running."
        } else {
            "No satellites reported yet. Indoors this is normal — GNSS signals need a " +
                "clear view of the sky."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val GOOD_SIGNAL_DB_HZ = 45f

/** "L5 / E5a / B2a" is too wide for a list row; the first name identifies the band. */
private fun SignalBand.shortLabel(): String? =
    if (this == SignalBand.UNKNOWN) null else label.substringBefore(" /")

private fun signalColour(cn0DbHz: Float): Color = when {
    cn0DbHz >= 30f -> OkGreen
    cn0DbHz >= 20f -> WarnAmber
    cn0DbHz > 0f -> ErrorRed
    else -> DimGrey
}

private fun AlmanacReadiness.headline(): String = when (this) {
    AlmanacReadiness.HOT -> "Ready to fix"
    AlmanacReadiness.WARM -> "Warm start"
    AlmanacReadiness.COLD -> "Cold start"
    AlmanacReadiness.UNKNOWN -> "Waiting for receiver"
}

/**
 * Says why the receiver is in the state it is, not merely which state that is.
 *
 * "Ready" is reached by two different routes — the receiver is fixing right now, or it
 * holds enough precise orbits to fix shortly — and quoting the ephemeris count for a
 * device that got there by fixing would contradict the figure shown right above.
 */
private fun explanationFor(state: GnssUiState): String = when (state.readiness) {
    AlmanacReadiness.HOT -> if (state.usedInFix >= AlmanacStatus.SATELLITES_FOR_FIX) {
        "The receiver is using ${state.usedInFix} satellites for a fix right now."
    } else {
        "Precise orbits (ephemeris) are held for at least " +
            "${AlmanacStatus.SATELLITES_FOR_FIX} satellites. A fix takes seconds."
    }

    AlmanacReadiness.WARM ->
        "Coarse orbits (almanac) are held, but not enough precise ones. The receiver " +
            "knows where to look and needs about half a minute to download the rest."

    AlmanacReadiness.COLD ->
        "Too little orbital data to fix quickly. The receiver must search blindly; a " +
            "full almanac download takes up to 12 minutes."

    AlmanacReadiness.UNKNOWN ->
        "No report from the GNSS receiver yet."
}

@Composable
private fun AlmanacReadiness.tint(): Color = when (this) {
    AlmanacReadiness.HOT -> OkGreen
    AlmanacReadiness.WARM -> WarnAmber
    AlmanacReadiness.COLD -> ErrorRed
    AlmanacReadiness.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
