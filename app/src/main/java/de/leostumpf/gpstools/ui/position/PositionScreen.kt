// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.position

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leostumpf.gpstools.domain.ScatterStats
import de.leostumpf.gpstools.ui.common.DASH
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Note
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.ui.common.SectionLabel
import de.leostumpf.gpstools.ui.common.ValueRow
import de.leostumpf.gpstools.ui.common.fmt
import de.leostumpf.gpstools.ui.common.metres
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.WarnAmber
import kotlin.math.max

/** Where exactly the phone is: coordinates, heights, and how much the fix really scatters. */
@Composable
fun PositionScreen(
    state: PositionUiState,
    onToggleScatter: () -> Unit,
    onResetScatter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.position) }
        item { Spacer(Modifier.height(20.dp)) }

        item { SectionLabel("COORDINATES (WGS84)") }
        item { Spacer(Modifier.height(6.dp)) }
        if (!state.hasFix) {
            item { Note("No GNSS fix yet.") }
        } else {
            item {
                SelectionContainer {
                    androidx.compose.foundation.layout.Column {
                        state.coordinates.forEach { (label, value) -> ValueRow(label, value) }
                    }
                }
            }
            item {
                Note(
                    listOfNotNull(
                        state.horizontalAccuracyM?.let { "±${it.fmt(1)} m" },
                        state.fixAgeMs?.let { if (it < 2_000) "live" else "${it / 1_000} s old" },
                        "long-press to copy",
                    ).joinToString(" · "),
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("ALTITUDE") }
        item { Spacer(Modifier.height(6.dp)) }
        item {
            ValueRow(
                "GNSS, above sea level",
                metres(state.gnssMslM),
                detail = listOfNotNull(
                    state.verticalAccuracyM?.let { "±${it.fmt(1)} m" },
                    state.mslSource,
                ).joinToString(" · ").ifEmpty { null },
            )
        }
        item { ValueRow("GNSS, above ellipsoid", metres(state.gnssEllipsoidM)) }
        item { ValueRow("Geoid height here", metres(state.geoidHeightM)) }
        if (state.hasBarometer) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                ValueRow(
                    "Barometer, calibrated",
                    metres(state.baroCalibratedM),
                    detail = if (state.calibrationSamples > 0) {
                        "calibrated from ${state.calibrationSamples} GNSS heights"
                    } else {
                        "waiting for a GNSS height within ±10 m to calibrate"
                    },
                )
            }
            item { ValueRow("Barometer, standard atmosphere", metres(state.baroStandardM)) }
            item { ValueRow("Air pressure", state.pressureHpa?.let { "${it.fmt(2)} hPa" } ?: DASH) }
            item {
                ValueRow("Derived sea-level pressure", state.seaLevelPressureHpa?.let { "${it.fmt(1)} hPa" } ?: DASH)
            }
            item {
                ValueRow(
                    "Vertical speed",
                    state.verticalSpeedMps?.let { "${if (it >= 0) "+" else ""}${it.fmt(2)} m/s" } ?: DASH,
                )
            }
        } else {
            item { Note("This phone has no barometer.") }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("ACCURACY TEST") }
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Note(
                "Put the phone down somewhere with open sky and leave it. The spread of the " +
                    "fixes shows the real accuracy, to compare with the ± the receiver claims.",
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onToggleScatter, modifier = Modifier.weight(1f)) {
                    Text(if (state.scatterRunning) "Stop" else if (state.scatter != null) "Continue" else "Start")
                }
                OutlinedButton(onClick = onResetScatter, modifier = Modifier.weight(1f), enabled = state.scatter != null) {
                    Text("Reset")
                }
            }
        }
        state.scatter?.let { stats ->
            item { Spacer(Modifier.height(12.dp)) }
            item { ScatterPlot(stats) }
            item { Spacer(Modifier.height(8.dp)) }
            items(scatterRows(stats)) { (label, value) -> ValueRow(label, value) }
        }
    }
}

private fun scatterRows(s: ScatterStats): List<Pair<String, String>> = listOfNotNull(
    "Fixes" to s.count.toString(),
    "CEP50 (half of the fixes within)" to metres(s.cep50M),
    "CEP95" to metres(s.cep95M),
    "2DRMS" to metres(s.twoDrmsM),
    s.meanClaimedAccuracyM?.let { "Claimed accuracy, mean" to metres(it) },
    s.fractionWithinClaimed?.let { "Within their claimed radius" to "${(it * 100).toInt()} % (68 % expected)" },
    s.altitudeStdDevM?.let { "Altitude spread (σ)" to metres(it) },
)

/** Each fix around the mean, with the CEP50 and CEP95 circles and the claimed radius dashed. */
@Composable
private fun ScatterPlot(stats: ScatterStats) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val dot = MaterialTheme.colorScheme.primary
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val half = size.minDimension / 2f - 8.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        val extent = max(
            1.0,
            maxOf(stats.cep95M, stats.meanClaimedAccuracyM ?: 0.0, stats.points.maxOfOrNull { maxOf(kotlin.math.abs(it.eastM), kotlin.math.abs(it.northM)) } ?: 0.0) * 1.1,
        )
        val scale = (half / extent).toFloat()
        drawLine(grid, Offset(c.x - half, c.y), Offset(c.x + half, c.y))
        drawLine(grid, Offset(c.x, c.y - half), Offset(c.x, c.y + half))
        stats.points.forEach {
            drawCircle(dot.copy(alpha = 0.5f), 2.dp.toPx(), Offset(c.x + it.eastM.toFloat() * scale, c.y - it.northM.toFloat() * scale))
        }
        drawCircle(OkGreen, (stats.cep50M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        drawCircle(WarnAmber, (stats.cep95M * scale).toFloat(), c, style = Stroke(1.5.dp.toPx()))
        stats.meanClaimedAccuracyM?.let {
            drawCircle(
                label,
                (it * scale).toFloat(),
                c,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
            )
        }
        drawText(
            measurer.measure("±${extent.fmt(1)} m", TextStyle(color = label, fontSize = 10.sp)),
            topLeft = Offset(c.x + half - 60.dp.toPx(), c.y + 4.dp.toPx()),
        )
        drawText(
            measurer.measure("green CEP50 · amber CEP95 · dashed claimed", TextStyle(color = label, fontSize = 10.sp)),
            topLeft = Offset(4.dp.toPx(), size.height - 16.dp.toPx()),
        )
    }
}
