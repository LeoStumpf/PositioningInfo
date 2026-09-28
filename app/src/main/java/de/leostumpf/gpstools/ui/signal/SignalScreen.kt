// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.signal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.data.model.AssistanceCapabilities
import de.leostumpf.gpstools.domain.ResolutionClass
import de.leostumpf.gpstools.domain.SignalBand
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Note
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.ui.common.ValueRow
import de.leostumpf.gpstools.ui.common.fmt
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
import de.leostumpf.gpstools.ui.theme.WarnAmber
import java.util.Locale

/**
 * What the receiver is working with, and what resolution that ought to buy.
 *
 * The measured accuracy is given first and kept visually distinct from the expected range
 * below it: one is a figure the receiver reports for the fix you actually have, the other
 * is what the technique in use typically achieves under an open sky. Conflating the two
 * would be the easiest way to make this screen lie.
 */
@Composable
fun SignalScreen(state: SignalUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.signal) }
        item { Spacer(Modifier.height(20.dp)) }
        item { MeasuredAccuracy(state) }
        item { Spacer(Modifier.height(24.dp)) }
        item { ExpectedResolution(state) }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("SATELLITE GEOMETRY (DOP)") }
        item { Spacer(Modifier.height(8.dp)) }
        item { Geometry(state) }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("FREQUENCY BANDS IN USE") }
        item { Spacer(Modifier.height(8.dp)) }
        item { Bands(state) }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("AUGMENTATION (SBAS)") }
        item { Spacer(Modifier.height(8.dp)) }
        item { Augmentation(state) }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("ASSISTANCE SERVICES") }
        item { Spacer(Modifier.height(8.dp)) }
        item { Assistance(state.capabilities) }

        if (state.capabilities.hardwareModel != null || state.capabilities.hardwareYear != null) {
            item { Spacer(Modifier.height(28.dp)) }
            item { SectionLabel("RECEIVER") }
            item { Spacer(Modifier.height(8.dp)) }
            item { Receiver(state.capabilities) }
        }
    }
}

@Composable
private fun Geometry(state: SignalUiState) {
    val dop = state.dop
    if (dop == null) {
        Note("Needs at least four satellites in the fix.")
        return
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dop.rating.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    dop.pdop <= 2.0 -> OkGreen
                    dop.pdop <= 5.0 -> MaterialTheme.colorScheme.onBackground
                    else -> WarnAmber
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "from ${state.dopSatellites} satellites",
                style = StatusLineStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        ValueRow("PDOP (3D position)", dop.pdop.fmt(2), detail = state.chipPdop?.let { "chip reports ${it.fmt(1)}" })
        ValueRow("HDOP (horizontal)", dop.hdop.fmt(2), detail = state.chipHdop?.let { "chip reports ${it.fmt(1)}" })
        ValueRow("VDOP (vertical)", dop.vdop.fmt(2), detail = state.chipVdop?.let { "chip reports ${it.fmt(1)}" })
        ValueRow("TDOP (time)", dop.tdop.fmt(2))
        Spacer(Modifier.height(4.dp))
        Note(
            "Position error ≈ DOP × range error. Many satellites spread over the whole sky " +
                "give low values; a street canyon that hides half the sky drives them up.",
        )
    }
}

@Composable
private fun MeasuredAccuracy(state: SignalUiState) {
    Column {
        Text(
            text = state.measuredAccuracyM?.let {
                String.format(Locale.US, "±%.1f m", it)
            } ?: "—",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (state.measuredAccuracyM != null) {
                "Measured horizontal accuracy of the current fix (68% confidence)."
            } else {
                "No fix, so the receiver reports no accuracy."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpectedResolution(state: SignalUiState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212), RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.resolution.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = state.resolution.typicalRange,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.resolution == ResolutionClass.NO_FIX) {
                "Nothing is being positioned yet, so there is no technique to judge."
            } else {
                "Typical open-sky accuracy for this technique. Not a measurement — " +
                    "buildings, trees and satellite geometry all make it worse."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Bands(state: SignalUiState) {
    when {
        state.bandsUnavailable -> Note(
            "This receiver does not report carrier frequencies, so the bands in use " +
                "cannot be determined.",
        )

        state.bandsInUse.isEmpty() -> Note("No satellites are being used for a fix yet.")

        else -> Column {
            state.bandsInUse.forEach { band -> BandRow(band) }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.dualFrequency) {
                    "Dual frequency: the receiver hears satellites on two bands at once " +
                        "and can cancel ionospheric delay by direct measurement."
                } else {
                    "Single frequency: ionospheric delay has to be estimated from a " +
                        "broadcast model, which is the largest remaining source of error."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.dualFrequency) OkGreen else WarnAmber,
            )
        }
    }
}

@Composable
private fun BandRow(band: SignalBand) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = band.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (band.isHighPrecision) OkGreen else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = band.description,
            style = StatusLineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Augmentation(state: SignalUiState) {
    if (state.sbasInView.isEmpty()) {
        Note(
            "No augmentation satellites in view. These are geostationary, so whether one " +
                "is reachable depends on where you are and what is blocking the sky " +
                "towards the equator.",
        )
        return
    }

    Column {
        state.sbasInView.forEach { sbas ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    text = sbas.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    text = sbas.region,
                    style = StatusLineStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.sbasUsedInFix) {
                "Corrections are being applied to the current fix."
            } else {
                "In view but not used in the current fix, so no corrections are being applied."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (state.sbasUsedInFix) OkGreen else WarnAmber,
        )
    }
}

@Composable
private fun Assistance(capabilities: AssistanceCapabilities) {
    if (!capabilities.reported) {
        Note(
            "Android 12 and later can report which services the receiver supports. " +
                "This device is older, so the platform provides no way to ask.",
        )
        return
    }

    Column {
        CapabilityRow("Raw measurements", capabilities.rawMeasurements)
        CapabilityRow("Navigation messages", capabilities.navigationMessages)

        if (capabilities.assistanceReported) {
            CapabilityRow("A-GNSS (network computes)", capabilities.assistedMsa)
            CapabilityRow("A-GNSS (phone computes)", capabilities.assistedMsb)
            CapabilityRow("Time injection", capabilities.onDemandTime)
            CapabilityRow("Measurement corrections", capabilities.measurementCorrections)
            CapabilityRow("Carrier phase tracking", capabilities.carrierPhase)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (capabilities.assistanceReported) {
                "Assisted GNSS delivers orbital data over the network instead of waiting " +
                    "for the satellites to broadcast it, which is what turns a " +
                    "multi-minute cold start into a few seconds."
            } else {
                "Android only began reporting the assistance services — A-GNSS, time " +
                    "injection, corrections — in version 14. This device runs an earlier " +
                    "release, so it almost certainly uses assisted GNSS but cannot say so."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapabilityRow(label: String, available: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (available) "yes" else "no",
            style = StatusLineStyle,
            fontWeight = if (available) FontWeight.Bold else FontWeight.Normal,
            color = if (available) OkGreen else DimGrey,
        )
    }
}

@Composable
private fun Receiver(capabilities: AssistanceCapabilities) {
    Column {
        capabilities.hardwareModel?.let {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Chipset",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        capabilities.hardwareYear?.let {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = "Generation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
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
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
