// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.signal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.DopRating
import io.github.leostumpf.positioninginfo.domain.ResolutionClass
import io.github.leostumpf.positioninginfo.domain.SignalBand
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.StatusBadge
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle

/**
 * How good the position is and why: the measured accuracy against what the technique in
 * use can deliver, the satellite geometry, the frequency bands, and augmentation.
 */
@Composable
fun SignalScreen(state: SignalUiState, modifier: Modifier = Modifier) {
    PageScaffold(Page.SIGNAL, modifier) {
        item { AccuracySummary(state, Modifier.padding(top = 16.dp)) }
        if (state.isMock) {
            item {
                Notice(
                    "Simulated position: these accuracies come from a mock-location app, not the receiver.",
                    Modifier.padding(top = 12.dp),
                    tone = Tone.BAD,
                )
            }
        }
        reportedAccuraciesSection(state)
        geometrySection(state)
        bandsSection(state)
        augmentationSection(state)
    }
}

/** The accuracy measured now, and what the technique in use typically achieves. */
@Composable
private fun AccuracySummary(state: SignalUiState, modifier: Modifier = Modifier) {
    val noFix = state.resolution == ResolutionClass.NO_FIX
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroValue(
            value = state.measuredAccuracyM?.let { "±${it.fmt(1)}" } ?: DASH,
            unit = state.measuredAccuracyM?.let { "m" },
            caption = if (state.measuredAccuracyM != null) {
                "Measured horizontal accuracy · 68 % confidence"
            } else {
                "No fix, so the receiver reports no accuracy"
            },
        )
        InfoCard {
            Text("EXPECTED FOR THIS TECHNIQUE", style = OverlineStyle, color = Palette.TextTertiary)
            Text(
                if (noFix) state.resolution.label else "${state.resolution.label} · ${state.resolution.typicalRange}",
                style = TitleStyle,
                color = Palette.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (noFix) {
                    "Nothing is being positioned yet, so there is no technique to judge."
                } else {
                    "Typical under open sky. Buildings, trees and poor geometry make it worse."
                },
                style = CaptionStyle,
                color = Palette.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Every accuracy Android reports with the fix, and how often fixes arrive. */
private fun LazyListScope.reportedAccuraciesSection(state: SignalUiState) {
    section("Reported accuracies", trailing = "68 %")
    item { ValueRow("Horizontal", state.measuredAccuracyM?.let { "±${it.fmt(1)} m" } ?: DASH) }
    item { ValueRow("Vertical", state.verticalAccuracyM?.let { "±${it.fmt(1)} m" } ?: DASH) }
    item {
        val kmh = state.speedAccuracyMps?.let { SpeedUnit.KMH.fromMps(it.toDouble()) }
        ValueRow(
            "Speed",
            state.speedAccuracyMps?.let { "±${it.fmt(2)} m/s" } ?: DASH,
            detail = kmh?.let { "±${it.fmt(1)} ${SpeedUnit.KMH.symbol}" },
        )
    }
    item {
        ValueRow(
            "Direction of travel",
            state.bearingAccuracyDeg?.let { "±${it.fmt(1)}°" } ?: DASH,
            detail = if (state.bearingAccuracyDeg == null) "reported only while moving" else null,
        )
    }
    fixTimingItems(state)
}

/** How precisely the fix is time-stamped, and how often fixes arrive. */
private fun LazyListScope.fixTimingItems(state: SignalUiState) {
    item {
        val uncertainty = state.timeUncertaintyMs
        ValueRow(
            "Fix timestamp",
            when {
                uncertainty == null -> DASH
                uncertainty < 1.0 -> "±${(uncertainty * US_PER_MS).fmt(0)} µs"
                else -> "±${uncertainty.fmt(1)} ms"
            },
            detail = if (uncertainty == null) "this receiver does not report it" else "when the position was valid",
        )
    }
    item {
        val interval = state.updateIntervalMs
        ValueRow(
            "Fix interval",
            when {
                interval == null -> DASH
                interval < SECONDS_FROM_MS -> "$interval ms"
                else -> "${(interval / MS_PER_S).fmt(1)} s"
            },
            detail = interval?.takeIf { it > 0 }?.let { "${(MS_PER_S / it).fmt(1)} fixes per second" },
            divider = false,
        )
    }
}

/** Dilution of precision: how well spread the satellites in the fix are. */
private fun LazyListScope.geometrySection(state: SignalUiState) {
    section("Satellite geometry")
    val dop = state.dop
    if (dop == null) {
        item { Note("Needs at least four satellites in the fix.") }
        return
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(dop.rating.label, dop.rating.tone())
            Text(
                "from ${state.dopSatellites.counted("satellite")} in the fix",
                style = CaptionStyle,
                color = Palette.TextTertiary,
            )
        }
    }
    item {
        // Computed here, with the chip's own figure from NMEA underneath where it has one.
        fun chip(value: Double?) = value?.let { "chip ${it.fmt(1)}" } ?: DASH
        TileRow(
            listOf(
                { m -> StatTile("PDOP", dop.pdop.fmt(2), m, footnote = chip(state.chipPdop)) },
                { m -> StatTile("HDOP", dop.hdop.fmt(2), m, footnote = chip(state.chipHdop)) },
                { m -> StatTile("VDOP", dop.vdop.fmt(2), m, footnote = chip(state.chipVdop)) },
                { m -> StatTile("TDOP", dop.tdop.fmt(2), m, footnote = DASH) },
            ),
            Modifier.padding(top = 12.dp),
        )
    }
    item {
        Note(
            "Position error ≈ DOP × range error. Satellites spread over the whole sky keep it low; " +
                "a street canyon that hides half the sky drives it up.",
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The bands in use, and whether that makes the fix dual frequency. */
private fun LazyListScope.bandsSection(state: SignalUiState) {
    section("Frequency bands")
    when {
        state.bandsUnavailable -> item {
            Note("This receiver does not report carrier frequencies, so the bands in use cannot be determined.")
        }

        state.bandsInUse.isEmpty() -> item { Note("No satellites are being used for a fix yet.") }

        else -> {
            item {
                val gap = Arrangement.spacedBy(8.dp)
                FlowRow(horizontalArrangement = gap, verticalArrangement = gap) {
                    state.bandsInUse.forEach { BandChip(it, inUse = true) }
                    // L5 is the band that makes the difference; shown dashed while unused.
                    if (SignalBand.L5 !in state.bandsInUse) BandChip(SignalBand.L5, inUse = false)
                }
            }
            item {
                if (state.dualFrequency) {
                    Notice(
                        "Dual frequency: the receiver measures the ionospheric delay directly and removes it.",
                        Modifier.padding(top = 12.dp),
                        tone = Tone.GOOD,
                    )
                } else {
                    Notice(
                        "Single frequency: ionospheric delay is estimated from a broadcast model — the " +
                            "largest remaining error.",
                        Modifier.padding(top = 12.dp),
                        tone = Tone.DEGRADED,
                    )
                }
            }
        }
    }
}

/** Which correction services are overhead, and whether the fix uses them. */
private fun LazyListScope.augmentationSection(state: SignalUiState) {
    section("Augmentation · SBAS")
    if (state.sbasInView.isEmpty()) {
        item {
            Note(
                "No augmentation satellites in view. They are geostationary over the equator, so " +
                    "whether one is reachable depends on where you are and what blocks that part of the sky.",
            )
        }
        return
    }
    state.sbasInView.forEachIndexed { i, sbas ->
        item {
            ValueRow(
                sbas.label,
                if (state.sbasUsedInFix) "in use" else "in view",
                detail = sbas.region,
                valueColor = if (state.sbasUsedInFix) Palette.Good else Palette.Degraded,
                divider = i < state.sbasInView.lastIndex,
            )
        }
    }
    item {
        Note(
            if (state.sbasUsedInFix) {
                "Corrections are being applied to the current fix."
            } else {
                "In view but not used in the current fix, so no corrections are being applied."
            },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun BandChip(band: SignalBand, inUse: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .then(
                if (inUse) {
                    Modifier.border(1.dp, Palette.TextPrimary, shape)
                } else {
                    // Dashed: a band the phone could use but does not right now.
                    Modifier.drawBehind {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                        drawRoundRect(
                            Palette.Outline,
                            cornerRadius = CornerRadius(12.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = dash),
                        )
                    }
                },
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            band.label,
            style = DataStyle.copy(fontSize = 13.sp),
            color = if (inUse) Palette.TextPrimary else Palette.Inactive,
        )
        Text(
            (band.frequencyLabel()?.let { "$it · " } ?: "") + if (inUse) "in use" else "not heard",
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = if (inUse) Palette.TextSecondary else Palette.Inactive,
        )
    }
}

private fun SignalBand.frequencyLabel(): String? = when (this) {
    SignalBand.L1 -> "1575 MHz"
    SignalBand.L2 -> "1227 MHz"
    SignalBand.L5 -> "1176 MHz"
    SignalBand.E5B -> "1207 MHz"
    SignalBand.E6 -> "1278 MHz"
    SignalBand.S_BAND -> "2492 MHz"
    SignalBand.UNKNOWN -> null
}

private fun DopRating.tone(): Tone = when (this) {
    DopRating.IDEAL, DopRating.EXCELLENT -> Tone.GOOD
    DopRating.GOOD -> Tone.NEUTRAL
    DopRating.MODERATE -> Tone.DEGRADED
    DopRating.FAIR, DopRating.POOR -> Tone.BAD
}

private const val US_PER_MS = 1_000.0
private const val MS_PER_S = 1_000.0

/** From 1.5 s up the fix interval reads better in seconds. */
private const val SECONDS_FROM_MS = 1_500
