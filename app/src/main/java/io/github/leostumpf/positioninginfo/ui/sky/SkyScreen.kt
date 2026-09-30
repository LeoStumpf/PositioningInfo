// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.sky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.CompassTrust
import io.github.leostumpf.positioninginfo.domain.SatelliteId
import io.github.leostumpf.positioninginfo.domain.SkyPoint
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SegmentedToggle
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.MonoFamily
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.SansFamily
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle
import io.github.leostumpf.positioninginfo.ui.theme.color
import io.github.leostumpf.positioninginfo.ui.theme.signalAlpha
import io.github.leostumpf.positioninginfo.ui.theme.signalColour
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sky as the receiver sees it: where each satellite is, where it has been, and where it
 * seems to be heading. North is up and the centre is straight overhead; compass mode turns
 * it with the phone. Shown on the GNSS page, right above the satellite list.
 */
fun LazyListScope.skyPlotItems(
    state: SkyUiState,
    onToggleCompass: () -> Unit,
    onToggleMap: () -> Unit,
    onToggleShowPaths: () -> Unit,
    onClearPaths: () -> Unit,
) {
    section("Sky")
    item {
        // Wraps the compass button under the switch when the text is large.
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedToggle(
                options = listOf("Paths", "Signal map"),
                selected = if (state.mapMode) 1 else 0,
                onSelect = { if ((it == 1) != state.mapMode) onToggleMap() },
            )
            CompassToggle(state.compassMode, onToggleCompass)
        }
    }
    state.headingText?.let { text ->
        item {
            Text(
                text = text + if (state.compassUnreliable) " · calibrate: move the phone in a figure-eight" else "",
                style = StatusLineStyle,
                color = if (state.compassUnreliable) Palette.Degraded else Palette.TextSecondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (state.compassMode && state.magneticUt != null) {
        item { CompassTrustLine(state.magneticUt, state.compassTrust) }
    }
    item { SkyPlot(state, Modifier.padding(top = 12.dp)) }
    item { Legend(state.mapMode) }
    if (!state.mapMode) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .toggleable(
                            value = state.showPaths,
                            role = Role.Switch,
                            onValueChange = { onToggleShowPaths() },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Switch(
                        checked = state.showPaths,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Palette.Background,
                            checkedTrackColor = Palette.TextPrimary,
                            uncheckedThumbColor = Palette.TextTertiary,
                            uncheckedTrackColor = Palette.SurfaceRaised,
                            uncheckedBorderColor = Palette.Outline,
                        ),
                    )
                    Text("Show paths", style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
                }
                QuietButton("Clear paths", onClick = onClearPaths)
            }
        }
    }
    item {
        Note(
            if (state.mapMode) {
                "Average signal strength by direction from ${state.obstructionSamples} measurements. " +
                    "Regions that stay weak or empty near the horizon are buildings and trees — give it half an hour."
            } else {
                "Paths build up while the app runs; projections need two minutes of history and " +
                    "cannot show satellites that have not risen yet."
            } + if (state.compassMode) " Hold the phone flat; the plot turns with it." else "",
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** Satellites about to set, and which ones came and went. */
fun LazyListScope.skyEventItems(state: SkyUiState) {
    section("Setting soon")
    if (state.upcoming.isEmpty()) {
        item { Note("No satellite expected to set in the next 15 minutes.") }
    } else {
        items(state.upcoming, key = { it.id.label() }) {
            EventRow(it.id, null, "in ~${it.minutes.roundToInt().coerceAtLeast(1)} min", dataValue = true)
        }
    }

    section("Events")
    if (state.events.isEmpty()) {
        item { Note("Satellites appearing and disappearing will be listed here.") }
    } else {
        items(state.events, key = { it.key }) { EventRow(it.id, it.text, it.ago) }
    }
}

/** Whether the compass can be believed: measured field strength against the model's. */
@Composable
private fun CompassTrustLine(measuredUt: Double, trust: CompassTrust?) {
    val (text, color) = when (trust?.level) {
        null -> "Magnetic field ${measuredUt.roundToInt()} µT · needs a fix to compare with the model" to
            Palette.TextSecondary

        CompassTrust.Level.RELIABLE ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · compass " +
                "reliable" to
                Palette.Good

        CompassTrust.Level.SUSPECT ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · " +
                "something magnetic nearby, heading may be off" to
                Palette.Degraded

        CompassTrust.Level.DISTURBED ->
            "Magnetic field ${measuredUt.roundToInt()} µT, expected ${trust.expectedUt.roundToInt()} · compass " +
                "disturbed, heading is wrong" to
                Palette.Bad
    }
    Text(text, style = StatusLineStyle, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun CompassToggle(on: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (on) "on" else "off" },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        border = BorderStroke(1.dp, if (on) Palette.TextPrimary else Palette.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (on) Palette.TextPrimary else Palette.Background,
            contentColor = if (on) Palette.Background else Palette.TextSecondary,
        ),
    ) {
        Icon(AppIcons.Compass, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Compass", style = BodyStyle.copy(fontSize = 14.sp))
    }
}

@Composable
private fun Legend(mapMode: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (mapMode) {
            LegendSwatch(Palette.Good, "35+ dB-Hz", signalAlpha(35f))
            LegendSwatch(Palette.Degraded, "25–35", signalAlpha(25f))
            LegendSwatch(Palette.Bad, "weaker", signalAlpha(0f))
        } else {
            LegendDot(filled = true, "in fix")
            LegendDot(filled = false, "heard")
            LegendLine(dashed = false, "path")
            LegendLine(dashed = true, "next 15 min")
        }
    }
}

@Composable
private fun LegendDot(filled: Boolean, label: String) = LegendItem(label) {
    Box(
        Modifier.size(10.dp).then(
            if (filled) {
                Modifier.background(Palette.TextPrimary, CircleShape)
            } else {
                Modifier.border(2.dp, Palette.TextPrimary, CircleShape)
            },
        ),
    )
}

@Composable
private fun LegendSwatch(color: Color, label: String, alpha: Float = 0.6f) = LegendItem(label) {
    Box(Modifier.size(10.dp).background(color.copy(alpha = alpha), RoundedCornerShape(2.dp)))
}

@Composable
private fun LegendLine(dashed: Boolean, label: String) = LegendItem(label) {
    Canvas(Modifier.size(18.dp, 2.dp)) {
        drawLine(
            Palette.TextSecondary,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 1.dp.toPx())) else null,
        )
    }
}

@Composable
private fun LegendItem(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextSecondary)
    }
}

/** "G12 · set below the horizon (4°) · 2 min ago": the code coloured by its constellation. */
@Composable
private fun EventRow(id: SatelliteId, text: String?, trailing: String, dataValue: Boolean = false) {
    val code = id.label()
    val color = id.constellation.color()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(
                        SpanStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, color = color),
                    ) { append(code) }
                    text?.let { withStyle(SpanStyle(color = Palette.TextPrimary)) { append(" $it") } }
                },
                style = BodyStyle,
                modifier = Modifier.weight(1f),
            )
            Text(
                trailing,
                style = if (dataValue) DataStyle else CaptionStyle,
                color = if (dataValue) Palette.TextPrimary else Palette.TextTertiary,
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
private fun SkyPlot(state: SkyUiState, modifier: Modifier = Modifier) {
    val markers = state.markers
    val rotation = if (state.compassMode) state.headingDegrees ?: 0f else 0f
    // Room for every satellite label plus the compass letters; the default of 8 would
    // re-measure most of them on every frame.
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val ringColour = Palette.Hairline
    val labelColour = Palette.TextTertiary
    val heard = markers.filter { it.current != null && it.tracked }
    val orientation = if (state.compassMode) "turned with the phone" else "north up"
    val description =
        "Sky plot: ${heard.size.counted("satellite")} plotted, ${heard.count { it.usedInFix }} in the fix, $orientation"
    // Square, but never taller than most of the screen: in landscape the full width would
    // put half the plot out of view.
    val maxSide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * 0.8f }
    Canvas(
        modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .widthIn(max = maxSide)
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f - 18.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun at(point: SkyPoint): Offset {
            val r = radius * (90f - point.elevationDegrees.coerceIn(0f, 90f)) / 90f
            val az = Math.toRadians((point.azimuthDegrees - rotation).toDouble())
            return Offset(centre.x + r * sin(az).toFloat(), centre.y - r * cos(az).toFloat())
        }

        // The sky disc, elevation rings at 30° and 60°, the horizon, and the compass cross.
        drawCircle(Palette.PlotBackground, radius, centre)
        drawCircle(Palette.Outline, radius, centre, style = Stroke(1.dp.toPx()))
        for (elevation in listOf(30f, 60f)) {
            drawCircle(ringColour, radius * (90f - elevation) / 90f, centre, style = Stroke(1.dp.toPx()))
        }
        drawLine(ringColour, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y))
        drawLine(ringColour, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius))
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (text, az) ->
            val style = TextStyle(
                fontFamily = SansFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (text == "N") Palette.TextPrimary else labelColour,
            )
            val layout = measurer.measure(text, style)
            val edge = at(SkyPoint(az, 0f))
            val outward = Offset(edge.x - centre.x, edge.y - centre.y) / radius * 10.dp.toPx()
            drawText(layout, topLeft = edge + outward - Offset(layout.size.width / 2f, layout.size.height / 2f))
        }

        if (state.mapMode) {
            // Annular sectors, one per bin, coloured by mean C/N0.
            for (cell in state.obstruction) {
                val outer = radius * (90f - cell.elevationFrom) / 90f
                val inner = radius * (90f - cell.elevationTo) / 90f
                val start = cell.azimuthFrom - rotation - 90f
                val sweep = cell.azimuthTo - cell.azimuthFrom
                val path = Path().apply {
                    arcTo(Rect(centre, outer), start, sweep, true)
                    arcTo(Rect(centre, inner), start + sweep, -sweep, false)
                    close()
                }
                drawPath(path, signalColour(cell.meanCn0DbHz).copy(alpha = signalAlpha(cell.meanCn0DbHz)))
            }
        }
        if (state.compassMode) {
            // The phone's top edge.
            val tip = Offset(centre.x, centre.y - radius - 2.dp.toPx())
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x - 6.dp.toPx(), tip.y - 10.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y - 10.dp.toPx())
                    close()
                },
                labelColour,
            )
        }

        val stroke = 2.dp.toPx()
        for (marker in markers) {
            if (state.mapMode || !state.showPaths) break
            val colour = marker.constellation.color()
            val faded = if (marker.current == null) colour.copy(alpha = 0.3f) else colour.copy(alpha = 0.6f)
            marker.trail.filter { it.size > 1 }.forEach { segment ->
                drawPath(pathOf(segment.map(::at)), faded, style = Stroke(stroke))
            }
            if (marker.projection.size > 1) {
                drawPath(
                    pathOf(marker.projection.map(::at)),
                    colour.copy(alpha = 0.8f),
                    style = Stroke(
                        stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
        }
        for (marker in markers) {
            val current = marker.current ?: continue
            drawSatellite(at(current), marker, measurer)
        }
    }
}

/** Filled when used in the fix, a ring when heard, a faint ring when only known from the almanac. */
private fun DrawScope.drawSatellite(position: Offset, marker: SkyMarker, measurer: TextMeasurer) {
    val colour = marker.constellation.color()
    val dot = 6.dp.toPx()
    when {
        marker.usedInFix -> drawCircle(colour, dot, position)
        marker.tracked -> drawCircle(colour, dot, position, style = Stroke(2.dp.toPx()))
        else -> drawCircle(colour.copy(alpha = 0.4f), dot, position, style = Stroke(1.dp.toPx()))
    }
    val layout = measurer.measure(
        marker.label,
        TextStyle(
            fontFamily = MonoFamily,
            color = colour.copy(alpha = if (marker.tracked) 1f else 0.5f),
            fontSize = 10.sp,
        ),
    )
    drawText(layout, topLeft = position + Offset(dot + 2.dp.toPx(), -layout.size.height / 2f))
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}
