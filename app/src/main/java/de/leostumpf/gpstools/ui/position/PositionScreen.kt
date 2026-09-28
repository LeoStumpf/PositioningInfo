// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.position

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leostumpf.gpstools.domain.ScatterStats
import de.leostumpf.gpstools.ui.common.AppIcons
import de.leostumpf.gpstools.ui.common.DASH
import de.leostumpf.gpstools.ui.common.HeroValue
import de.leostumpf.gpstools.ui.common.InfoCard
import de.leostumpf.gpstools.ui.common.Note
import de.leostumpf.gpstools.ui.common.Notice
import de.leostumpf.gpstools.ui.common.Page
import de.leostumpf.gpstools.ui.common.PageScaffold
import de.leostumpf.gpstools.ui.common.PrimaryButton
import de.leostumpf.gpstools.ui.common.SecondaryButton
import de.leostumpf.gpstools.ui.common.ValueRow
import de.leostumpf.gpstools.ui.common.fmt
import de.leostumpf.gpstools.ui.common.metres
import de.leostumpf.gpstools.ui.common.section
import de.leostumpf.gpstools.ui.theme.BodyStyle
import de.leostumpf.gpstools.ui.theme.DataStyle
import de.leostumpf.gpstools.ui.theme.Palette
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
    val clipboard = LocalClipboardManager.current
    PageScaffold(Page.POSITION, modifier) {
        section(
            "Coordinates · WGS84",
            trailing = state.horizontalAccuracyM?.let { acc ->
                "±${acc.fmt(1)} m · " + if ((state.fixAgeMs ?: 0) < 2_000) "live" else "${(state.fixAgeMs ?: 0) / 1_000} s old"
            },
            trailingColor = if ((state.fixAgeMs ?: Long.MAX_VALUE) < 2_000) Palette.Good else Palette.Degraded,
        )
        if (!state.hasFix) {
            item { Notice("No GNSS fix yet. Coordinates appear with the first fix.") }
        } else {
            item {
                InfoCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    SelectionContainer {
                        Column {
                            state.coordinates.forEachIndexed { i, (label, value) ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(if (label == "Degrees, minutes, seconds") "DMS" else label, style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
                                    Text(value, style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                }
                                if (i < state.coordinates.lastIndex) HorizontalDivider(color = Palette.Divider)
                            }
                        }
                    }
                }
            }
            item {
                SecondaryButton(
                    "Copy all formats",
                    icon = AppIcons.Copy,
                    onClick = {
                        clipboard.setText(AnnotatedString(state.coordinates.joinToString("\n") { "${it.first}: ${it.second}" }))
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

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
            item {
                InfoCard(Modifier.padding(top = 8.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    ValueRow(
                        "Barometer",
                        metres(state.baroCalibratedM),
                        detail = if (state.calibrationSamples > 0) {
                            "calibrated from ${state.calibrationSamples} GNSS heights"
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
        } else {
            item { Note("This phone has no barometer.", modifier = Modifier.padding(top = 8.dp)) }
        }

        section("Accuracy test", trailing = state.scatter?.let { "${it.count} fixes" })
        val stats = state.scatter
        if (stats != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScatterPlot(stats, Modifier.size(170.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Figure("CEP50", metres(stats.cep50M), Palette.Good)
                        Figure("CEP95", metres(stats.cep95M), Palette.Degraded)
                        Figure("2DRMS", metres(stats.twoDrmsM))
                        stats.meanClaimedAccuracyM?.let { Figure("Claimed, mean", metres(it)) }
                        stats.fractionWithinClaimed?.let { Figure("Within claimed", "${(it * 100).toInt()} %") }
                        stats.altitudeStdDevM?.let { Figure("Height σ", metres(it)) }
                    }
                }
            }
        }
        item {
            Note(
                "Leave the phone still under open sky. Green holds half the fixes, amber 95 %; " +
                    "dashed is the ± the receiver claims (68 % expected inside).",
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                PrimaryButton(
                    if (state.scatterRunning) "Stop" else if (stats != null) "Continue" else "Start",
                    onClick = onToggleScatter,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton("Reset", onClick = onResetScatter, enabled = stats != null, modifier = Modifier.weight(1f))
            }
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
private fun ScatterPlot(stats: ScatterStats, modifier: Modifier) {
    Canvas(modifier) {
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
            drawCircle(Palette.TextTertiary, (it * scale).toFloat(), c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
        drawCircle(Palette.Degraded, (stats.cep95M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(Palette.Good, (stats.cep50M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        stats.points.forEach {
            drawCircle(Palette.TextPrimary.copy(alpha = 0.55f), 1.8.dp.toPx(), Offset(c.x + it.eastM.toFloat() * scale, c.y - it.northM.toFloat() * scale))
        }
    }
}
