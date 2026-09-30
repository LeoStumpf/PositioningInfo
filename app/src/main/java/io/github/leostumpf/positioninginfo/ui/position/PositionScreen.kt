// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.position

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.ScatterStats
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.HeroValue
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.PrimaryButton
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.copyText
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.metres
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.max

/** Where exactly the phone is: coordinates, heights, and how much the fix really scatters. */
@Composable
fun PositionScreen(
    state: PositionUiState,
    onToggleScatter: () -> Unit,
    onResetScatter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(Page.POSITION, modifier) {
        coordinatesSection(state)
        altitudeSection(state)
        accuracyTestSection(state, onToggleScatter, onResetScatter)
    }
}

/** The position in every format, each copyable; the header says how fresh it is. */
private fun LazyListScope.coordinatesSection(state: PositionUiState) {
    val age = state.fixAgeMs
    section(
        "Coordinates · WGS84",
        trailing = state.horizontalAccuracyM?.let { acc ->
            val freshness = if ((age ?: 0) < LIVE_FIX_MS) "live" else "${(age ?: 0) / 1_000} s old"
            "±${acc.fmt(1)} m · $freshness"
        },
        trailingColor = if ((age ?: Long.MAX_VALUE) < LIVE_FIX_MS) Palette.Good else Palette.Degraded,
    )
    if (state.isMock) {
        item {
            Notice(
                "Simulated position: a mock-location app is supplying these coordinates, not the " +
                    "receiver. Everything below describes the fake, not where the phone is.",
                Modifier.padding(bottom = 10.dp),
                tone = Tone.BAD,
            )
        }
    }
    if (!state.hasFix) {
        item { Notice("No GNSS fix yet. Coordinates appear with the first fix.") }
        return
    }
    item {
        InfoCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            SelectionContainer {
                Column {
                    state.coordinates.forEachIndexed { i, (format, value) ->
                        CoordinateRow(format, value)
                        if (i < state.coordinates.lastIndex) HorizontalDivider(color = Palette.Divider)
                    }
                }
            }
        }
    }
    item {
        val context = LocalContext.current
        SecondaryButton(
            "Copy all formats",
            icon = AppIcons.Copy,
            onClick = {
                val all = state.coordinates.joinToString("\n") { (format, value) -> "${format.label}: $value" }
                copyText(context, all, "All formats copied")
            },
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** One format: its name, the value, and a button that copies it. */
@Composable
private fun CoordinateRow(format: CoordinateFormat, value: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(format.shortLabel, style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
        Text(
            value,
            style = DataStyle.copy(fontSize = 13.sp),
            color = Palette.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { copyText(context, value, "${format.label} copied") }, modifier = Modifier.size(48.dp)) {
            Icon(
                AppIcons.Copy,
                contentDescription = "Copy ${format.label}",
                tint = Palette.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Height above sea level from GNSS, the ellipsoid and geoid behind it, and the barometer. */
private fun LazyListScope.altitudeSection(state: PositionUiState) {
    section("Altitude")
    item {
        HeroValue(
            value = state.gnssMslM?.fmt(1) ?: DASH,
            unit = "m",
            caption = listOfNotNull(
                "Above sea level",
                state.verticalAccuracyM?.let { "±${it.fmt(1)} m" },
                state.mslSource?.let { "from the $it" },
            ).joinToString(" · "),
        )
    }
    item { ValueRow("Above the ellipsoid", metres(state.gnssEllipsoidM)) }
    item { ValueRow("Geoid height here", metres(state.geoidHeightM), divider = false) }
    if (state.hasBarometer) {
        item { BarometerCard(state, Modifier.padding(top = 8.dp)) }
    } else {
        item { Note("This phone has no barometer.", modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun BarometerCard(state: PositionUiState, modifier: Modifier = Modifier) {
    InfoCard(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        ValueRow(
            "Barometer",
            metres(state.baroCalibratedM),
            detail = if (state.calibrationSamples > 0) {
                "calibrated from ${state.calibrationSamples.counted("GNSS height")}"
            } else {
                "waiting for a GNSS height within ±10 m"
            },
        )
        ValueRow("Air pressure", state.pressureHpa?.let { "${it.fmt(2)} hPa" } ?: DASH)
        ValueRow("Sea-level pressure", state.seaLevelPressureHpa?.let { "${it.fmt(1)} hPa" } ?: DASH)
        ValueRow("Standard atmosphere", metres(state.baroStandardM))
        ValueRow(
            "Vertical speed",
            state.verticalSpeedMps?.let { (if (it >= 0) "+" else "−") + abs(it).fmt(2) + " m/s" } ?: DASH,
            divider = false,
        )
    }
}

/** Leave the phone still and see how far the fixes really scatter, against what it claims. */
private fun LazyListScope.accuracyTestSection(
    state: PositionUiState,
    onToggleScatter: () -> Unit,
    onResetScatter: () -> Unit,
) {
    val stats = state.scatter
    section("Accuracy test", trailing = stats?.let { it.count.counted("fix", "fixes") })
    if (stats != null) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ScatterPlot(stats, Modifier.size(170.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Figure("CEP50", metres(stats.cep50M), Palette.Good)
                    Figure("CEP95", metres(stats.cep95M), Palette.Degraded)
                    Figure("2DRMS", metres(stats.twoDrmsM))
                    stats.meanClaimedAccuracyM?.let { Figure("Claimed, mean", metres(it)) }
                    stats.fractionWithinClaimed?.let { Figure("Within claimed", "${(it * PERCENT).toInt()} %") }
                    stats.altitudeStdDevM?.let { Figure("Height σ", metres(it)) }
                }
            }
        }
    }
    item {
        Note(
            "Leave the phone still under open sky. The inner circle (green) holds half the fixes, the outer " +
                "(amber) 95 %; dashed is the ± the receiver claims (68 % expected inside).",
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    item {
        val action = when {
            state.scatterRunning -> "Stop"
            stats != null -> "Continue"
            else -> "Start"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            PrimaryButton(action, onClick = onToggleScatter, modifier = Modifier.weight(1f))
            SecondaryButton("Reset", onClick = onResetScatter, enabled = stats != null, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, labelColor: Color = Palette.TextSecondary) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = BodyStyle.copy(fontSize = 14.sp), color = labelColor, modifier = Modifier.weight(1f))
        Text(value, style = DataStyle, color = Palette.TextPrimary)
    }
}

/** Each fix around the mean, with the CEP50 and CEP95 circles and the claimed radius dashed. */
@Composable
private fun ScatterPlot(stats: ScatterStats, modifier: Modifier = Modifier) {
    Canvas(
        modifier.semantics {
            contentDescription = "Scatter of ${stats.count} fixes around their mean: half within " +
                "${stats.cep50M.fmt(1)} metres, 95 percent within ${stats.cep95M.fmt(1)} metres"
        },
    ) {
        drawRoundRect(Palette.Surface, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()))
        val half = size.minDimension / 2f - 10.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        val extent = max(
            1.0,
            maxOf(
                stats.cep95M,
                stats.meanClaimedAccuracyM ?: 0.0,
                stats.points.maxOfOrNull { maxOf(abs(it.eastM), abs(it.northM)) } ?: 0.0,
            ) * 1.1,
        )
        val scale = (half / extent).toFloat()
        drawLine(Palette.Hairline, Offset(c.x - half, c.y), Offset(c.x + half, c.y))
        drawLine(Palette.Hairline, Offset(c.x, c.y - half), Offset(c.x, c.y + half))
        stats.meanClaimedAccuracyM?.let {
            drawCircle(
                Palette.TextTertiary,
                (it * scale).toFloat(),
                c,
                style = Stroke(
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                ),
            )
        }
        drawCircle(Palette.Degraded, (stats.cep95M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(Palette.Good, (stats.cep50M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        stats.points.forEach {
            drawCircle(
                Palette.TextPrimary.copy(alpha = 0.55f),
                1.8.dp.toPx(),
                Offset(c.x + it.eastM.toFloat() * scale, c.y - it.northM.toFloat() * scale),
            )
        }
    }
}

/** A fix younger than this reads as "live"; the receiver delivers one a second. */
private const val LIVE_FIX_MS = 2_000L

private const val PERCENT = 100
