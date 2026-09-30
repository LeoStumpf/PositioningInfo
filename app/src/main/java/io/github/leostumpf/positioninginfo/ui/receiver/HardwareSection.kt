// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.Palette

/* The chipset and the capabilities Android reports for it. */

/** The chipset and what it says it supports. */
internal fun LazyListScope.hardwareSection(state: ReceiverUiState) {
    section("Hardware")
    val caps = state.capabilities
    caps.hardwareModel?.let { item { ValueRow("Chipset", it.replace(';', ' ').trim()) } }
    caps.hardwareYear?.let { item { ValueRow("Hardware generation", it.toString()) } }
    if (!caps.reported) {
        item {
            Note(
                "Android 12 and later report which services the receiver supports; this device is older.",
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    } else {
        item { Capability("Raw measurements", caps.rawMeasurements) }
        item { Capability("Navigation messages", caps.navigationMessages) }
        item { Capability("Antenna corrections", caps.antennaInfo, last = !caps.assistanceReported) }
        if (caps.assistanceReported) {
            item { Capability("A-GNSS, network computes", caps.assistedMsa) }
            item { Capability("A-GNSS, phone computes", caps.assistedMsb) }
            item { Capability("Time injection", caps.onDemandTime) }
            item { Capability("Measurement corrections", caps.measurementCorrections) }
            item { Capability("Carrier phase tracking", caps.carrierPhase) }
            item { Capability("Satellite positions from the chip", caps.satellitePvt) }
            item { Capability("Satellite blocklist", caps.satelliteBlocklist) }
            item { Capability("Low-power mode", caps.lowPowerMode) }
            item { Capability("Geofencing on the chip", caps.geofencing) }
            item { Capability("Own fix scheduling", caps.scheduling) }
            item { Capability("Single-shot fix", caps.singleShotFix) }
            item { Capability("Correlation vectors", caps.correlationVectors) }
            item { Capability("Power statistics", caps.powerStats) }
            item {
                ValueRow(
                    "Path corrections accepted",
                    caps.correctionKinds.joinToString().ifEmpty { "none" },
                    valueColor = if (caps.correctionKinds.isEmpty()) Palette.TextTertiary else Palette.Good,
                    divider = false,
                )
            }
        } else {
            item {
                Note(
                    "Android reports the assistance services (A-GNSS, time injection, corrections) and " +
                        "the chip's other features only from version 14; this phone almost certainly uses " +
                        "assisted GNSS but cannot say so.",
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun Capability(label: String, available: Boolean, last: Boolean = false) {
    ValueRow(
        label,
        if (available) "yes" else "no",
        valueColor = if (available) Palette.Good else Palette.TextTertiary,
        divider = !last,
    )
}
