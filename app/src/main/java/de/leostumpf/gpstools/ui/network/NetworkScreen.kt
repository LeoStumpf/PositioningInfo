// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.data.model.AccessPoint
import de.leostumpf.gpstools.data.model.CellTower
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
import de.leostumpf.gpstools.ui.theme.WarnAmber

/**
 * Positioning without satellites: what the network provider reports, how good it really
 * is, and the Wi-Fi and cell data it is built from.
 */
@Composable
fun NetworkScreen(state: NetworkUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.network) }
        item { Spacer(Modifier.height(20.dp)) }
        item { NetworkFixHeader(state) }

        item { Spacer(Modifier.height(24.dp)) }
        item { SectionLabel("COMPARED WITH GNSS") }
        item { Spacer(Modifier.height(8.dp)) }
        item { Comparison(state) }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("CELL TOWERS") }
        item { Spacer(Modifier.height(6.dp)) }
        when {
            !state.hasTelephony -> item { Note("This device has no mobile radio.") }
            state.cells.isEmpty() -> item { Note("No cells reported. Is a SIM inserted and airplane mode off?") }
            else -> {
                if (state.cells.none { it.registered }) {
                    item {
                        Note(
                            "No serving cell: the phone is not attached to a network (no SIM, " +
                                "or out of service). The modem still measures the towers around it.",
                        )
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                items(state.cells) { CellRow(it) }
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("WI-FI ACCESS POINTS") }
        item { Spacer(Modifier.height(6.dp)) }
        when {
            !state.wifiAvailable -> item {
                Note("Wi-Fi is off and background Wi-Fi scanning is disabled, so no access points are visible.")
            }
            state.accessPoints.isEmpty() -> item { Note("No access points found yet.") }
            else -> {
                item { Note("${state.accessPoints.size} in range, strongest first.") }
                item { Spacer(Modifier.height(4.dp)) }
                items(state.accessPoints.take(MAX_ACCESS_POINTS), key = { it.bssid }) { AccessPointRow(it) }
            }
        }
    }
}

@Composable
private fun NetworkFixHeader(state: NetworkUiState) {
    Column {
        Text(
            text = state.accuracyM?.let { "±" + formatDistance(it.toDouble()) } ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = when {
                !state.providerEnabled ->
                    "Network location is switched off. On a Pixel: Settings › Location › " +
                        "Location services › Google Location Accuracy."
                state.ageMs == null -> "Waiting for a network position…"
                else -> listOfNotNull(
                    "Claimed accuracy (68 % confidence)",
                    state.source?.let { "from $it" },
                    formatAge(state.ageMs),
                ).joinToString(" · ")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.providerEnabled) MaterialTheme.colorScheme.onSurfaceVariant else WarnAmber,
        )
    }
}

@Composable
private fun Comparison(state: NetworkUiState) {
    val comparison = state.comparison
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212), RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        if (comparison == null) {
            Text(
                text = state.comparisonUnavailableReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Text(
            text = "${formatDistance(comparison.distanceM)} off",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Actual error: distance to the GNSS position, itself good to " +
                "±${comparison.gnssAccuracyM.toInt()} m.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        comparison.withinClaimed?.let { within ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (within) "Inside the claimed accuracy circle." else "Outside the claimed accuracy circle.",
                style = StatusLineStyle,
                color = if (within) OkGreen else WarnAmber,
            )
        }
    }
}

@Composable
private fun CellRow(cell: CellTower) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row {
            Text(
                text = cell.technology + if (cell.registered) " · serving" else " · neighbour",
                style = MaterialTheme.typography.bodyMedium,
                color = if (cell.registered) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = cell.signalDbm?.let { "$it dBm" } ?: "—",
                style = StatusLineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val details = listOfNotNull(
            cell.network,
            cell.identity,
            cell.physicalId?.let { "${cell.physicalIdLabel} $it" },
            cell.timingAdvanceDistanceM?.let { "tower ≈ ${formatDistance(it)} away" },
        )
        if (details.isNotEmpty()) {
            Text(text = details.joinToString(" · "), style = StatusLineStyle, color = DimGrey)
        }
    }
}

@Composable
private fun AccessPointRow(ap: AccessPoint) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = ap.ssid ?: "(hidden) ${ap.bssid}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${ap.rssiDbm} dBm · ${band(ap.frequencyMhz)}",
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = DimGrey)
}

private const val MAX_ACCESS_POINTS = 12
