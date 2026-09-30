// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.data.model.SignalMeasurement
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import io.github.leostumpf.positioninginfo.domain.RawSignal
import io.github.leostumpf.positioninginfo.domain.SatelliteId
import io.github.leostumpf.positioninginfo.domain.band
import io.github.leostumpf.positioninginfo.domain.formatAgo
import io.github.leostumpf.positioninginfo.ui.common.Gutter
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.SectionHeader
import io.github.leostumpf.positioninginfo.ui.common.SheetHeader
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import io.github.leostumpf.positioninginfo.ui.theme.color
import java.util.Locale

/** Everything known about one satellite, updated live while open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SatelliteSheet(row: SignalRow, detail: SignalDetail?, siblings: List<SignalRow>, onDismiss: () -> Unit) {
    val sat = row.satellite
    val color = sat.constellation.color()
    val heard = sat.cn0DbHz > 0f
    val stage = detail?.stage?.takeIf { heard }
    val inferred = detail?.stageInferred == true
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = Gutter, end = Gutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SheetHeader(onDismiss) {
                Text(
                    "${SatelliteId(sat.constellation, sat.svid).label()} · ${sat.constellation.label} ${sat.svid}",
                    style = TitleStyle.copy(fontSize = 20.sp),
                    color = color,
                )
                Text(
                    when {
                        sat.usedInFix -> "Used in the fix"
                        heard -> "Heard, not used in the fix"
                        else -> "Not heard — position known from the almanac"
                    },
                    style = CaptionStyle,
                    color = Palette.TextSecondary,
                )
            }

            SectionHeader("Acquisition", modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AcquisitionStage.entries.drop(1).forEach { s ->
                    val done = stage != null && s.step <= stage.step
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(
                                4.dp,
                            ).background(
                                if (done) {
                                    (
                                        if (stage ==
                                            AcquisitionStage.TIME_DECODED
                                        ) {
                                            Palette.Good
                                        } else {
                                            Palette.TextPrimary
                                        }
                                        )
                                } else {
                                    Palette.Hairline
                                },
                                RoundedCornerShape(2.dp),
                            ),
                        )
                        Text(
                            s.label,
                            style = CaptionStyle.copy(fontSize = 11.sp),
                            color = if (done) Palette.TextPrimary else Palette.TextTertiary,
                        )
                    }
                }
            }
            Note(
                when {
                    !heard ->
                        "No signal from this satellite. The receiver only knows from the almanac that it " +
                            "should be up there."

                    stage == null ->
                        "The chip reports no raw measurement for this signal right now, so how far " +
                            "acquisition has got is unknown."

                    inferred ->
                        "In the fix, so every step is complete — the chip just reports no raw " +
                            "measurement for it right now."

                    else -> stage.meaning
                },
                modifier = Modifier.padding(top = 6.dp),
            )

            SectionHeader("Signal", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            siblings.forEach { sib ->
                ValueRow(
                    "Strength" + (sib.satellite.band?.shortLabel()?.let { " · $it" } ?: ""),
                    if (sib.satellite.cn0DbHz > 0f) "%.0f dB-Hz".format(Locale.US, sib.satellite.cn0DbHz) else "—",
                )
            }
            ValueRow(
                "Doppler shift",
                detail?.dopplerHz?.takeIf { heard }?.let {
                    (if (it >= 0) "+" else "−") + String.format(
                        java.util.Locale.US,
                        "%.2f kHz",
                        kotlin.math.abs(it) / 1_000,
                    )
                } ?: "—",
                detail = "positive: approaching",
            )
            ValueRow(
                "Multipath",
                when (detail?.multipath) {
                    true -> "detected"
                    false -> "none"
                    null -> "not reported"
                },
            )

            detail?.raw?.takeIf { heard }?.let { RawMeasurementRows(sat, it) }

            SectionHeader("Position and data", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
            ValueRow("Elevation", "%.0f°".format(Locale.US, sat.elevationDegrees))
            ValueRow("Azimuth", "%.0f°".format(Locale.US, sat.azimuthDegrees))
            ValueRow("Almanac", if (sat.hasAlmanac) "held" else "missing")
            ValueRow("Ephemeris", if (sat.hasEphemeris) "held" else "missing")
            ValueRow(
                "First heard",
                detail?.firstHeardMs?.let { formatAgo(android.os.SystemClock.elapsedRealtime() - it) } ?: "—",
                divider = false,
            )
        }
    }
}

/** The raw-measurement fields that need a word of translation, for the satellite sheet. */
@Composable
private fun RawMeasurementRows(sat: SatelliteInfo, raw: SignalMeasurement) {
    SectionHeader("Raw measurement", modifier = Modifier.padding(top = 16.dp, bottom = 2.dp))
    raw.codeType?.let { code ->
        ValueRow("Signal code", code, detail = RawSignal.codeMeaning(sat.constellation, code) ?: "RINEX code letter")
    }
    raw.basebandCn0DbHz?.let { base ->
        ValueRow(
            "Strength at the chip",
            String.format(java.util.Locale.US, "%.1f dB-Hz", base),
            detail = String.format(
                java.util.Locale.US,
                "%.1f dB lost between antenna and correlators",
                raw.cn0DbHz - base,
            ),
        )
    }
    raw.snrDb?.let { ValueRow("Signal-to-noise", String.format(java.util.Locale.US, "%.1f dB", it)) }
    raw.receivedSvTimeUncertaintyNs?.let { ns ->
        val metres = RawSignal.timeUncertaintyM(ns)
        ValueRow(
            "Time uncertainty",
            if (ns < 1_000_000) "± $ns ns" else String.format(java.util.Locale.US, "± %.1f ms", ns / 1e6),
            detail = if (metres < 10_000) {
                String.format(
                    java.util.Locale.US,
                    "≈ %.0f m of range",
                    metres,
                )
            } else {
                String.format(java.util.Locale.US, "≈ %.0f km of range", metres / 1_000)
            },
        )
    }
    ValueRow("Carrier phase", RawSignal.carrierPhase(raw.carrierPhaseState), divider = raw.interSignalBiasNs != null)
    raw.interSignalBiasNs?.let {
        ValueRow(
            "Inter-signal bias",
            String.format(java.util.Locale.US, "%.1f ns", it),
            detail = "delay against the receiver's reference signal",
            divider = false,
        )
    }
}
