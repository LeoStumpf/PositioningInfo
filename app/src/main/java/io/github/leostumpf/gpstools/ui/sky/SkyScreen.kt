// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.ui.sky

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import io.github.leostumpf.gpstools.domain.Constellation
import io.github.leostumpf.gpstools.domain.SkyPoint
import io.github.leostumpf.gpstools.ui.common.AppIcons
import io.github.leostumpf.gpstools.ui.common.Note
import io.github.leostumpf.gpstools.ui.common.Page
import io.github.leostumpf.gpstools.ui.common.PageScaffold
import io.github.leostumpf.gpstools.ui.common.SegmentedToggle
import io.github.leostumpf.gpstools.ui.common.section
import io.github.leostumpf.gpstools.ui.theme.BodyStyle
import io.github.leostumpf.gpstools.ui.theme.CaptionStyle
import io.github.leostumpf.gpstools.ui.theme.DataStyle
import io.github.leostumpf.gpstools.ui.theme.Palette
import io.github.leostumpf.gpstools.ui.theme.PlexMono
import io.github.leostumpf.gpstools.ui.theme.PlexSans
import io.github.leostumpf.gpstools.ui.theme.StatusLineStyle
import io.github.leostumpf.gpstools.ui.theme.color
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The sky as the receiver sees it: where each satellite is, where it has been, and where
 * it seems to be heading.
 *
 * North is up and the centre is straight overhead, the usual sky-plot convention. The plot
 * is not rotated with the phone, so hold the top of the phone to the north to compare it
 * with the real sky.
 */
@Composable
fun SkyScreen(
    state: SkyUiState,
    onToggleCompass: () -> Unit,
    onToggleMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PageScaffold(Page.SKY, modifier) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
        item { SkyPlot(state, Modifier.padding(top = 12.dp)) }
        item { Legend(state.mapMode) }
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

        section("Setting soon")
        if (state.upcoming.isEmpty()) {
            item { Note("No satellite expected to set in the next 15 minutes.") }
        } else {
            items(state.upcoming, key = { it.label }) {
                EventRow(it.label, null, "in ~${it.minutes.roundToInt().coerceAtLeast(1)} min", dataValue = true)
            }
        }

        section("Events")
        if (state.events.isEmpty()) {
            item { Note("Satellites appearing and disappearing will be listed here.") }
        } else {
            items(state.events, key = { it.key }) { EventRow(it.text.substringBefore(' '), it.text.substringAfter(' '), it.ago) }
        }
    }
}

@Composable
private fun CompassToggle(on: Boolean, onToggle: () -> Unit) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.height(46.dp),
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
            LegendSwatch(Palette.Good, "35+ dB-Hz")
            LegendSwatch(Palette.Degraded, "25–35")
            LegendSwatch(Palette.Bad, "weaker")
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
            if (filled) Modifier.background(Palette.TextPrimary, CircleShape) else Modifier.border(2.dp, Palette.TextPrimary, CircleShape),
        ),
    )
}

@Composable
private fun LegendSwatch(color: Color, label: String) = LegendItem(label) {
    Box(Modifier.size(10.dp).background(color.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
}

@Composable
private fun LegendLine(dashed: Boolean, label: String) = LegendItem(label) {
    Canvas(Modifier.size(18.dp, 2.dp)) {
        drawLine(
            Palette.TextSecondary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null,
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
private fun EventRow(code: String, text: String?, trailing: String, dataValue: Boolean = false) {
    val color = codeColor(code)
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, color = color)) { append(code) }
                    text?.let { withStyle(SpanStyle(color = Palette.TextPrimary)) { append(" $it") } }
                },
                style = BodyStyle,
                modifier = Modifier.weight(1f),
            )
            Text(trailing, style = if (dataValue) DataStyle else CaptionStyle, color = if (dataValue) Palette.TextPrimary else Palette.TextTertiary)
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

private fun codeColor(code: String): Color = when (code.firstOrNull()) {
    'G' -> Constellation.GPS.color()
    'R' -> Constellation.GLONASS.color()
    'E' -> Constellation.GALILEO.color()
    'C' -> Constellation.BEIDOU.color()
    'J' -> Constellation.QZSS.color()
    'I' -> Constellation.IRNSS.color()
    else -> Constellation.SBAS.color()
}

@Composable
private fun SkyPlot(state: SkyUiState, modifier: Modifier = Modifier) {
    val markers = state.markers
    val rotation = if (state.compassMode) state.headingDegrees ?: 0f else 0f
    val measurer = rememberTextMeasurer()
    val ringColour = Palette.Hairline
    val labelColour = Palette.TextTertiary
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val radius = size.minDimension / 2f - 18.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun at(point: SkyPoint): Offset {
            val r = radius * (90f - point.elevationDegrees.coerceIn(0f, 90f)) / 90f
            val az = Math.toRadians((point.azimuthDegrees - rotation).toDouble())
            return Offset(centre.x + r * sin(az).toFloat(), centre.y - r * cos(az).toFloat())
        }

        // The sky disc, elevation rings at 30° and 60°, the horizon, and the compass cross.
        drawCircle(Color(0xFF060606), radius, centre)
        drawCircle(Palette.Outline, radius, centre, style = Stroke(1.dp.toPx()))
        for (elevation in listOf(30f, 60f)) {
            drawCircle(ringColour, radius * (90f - elevation) / 90f, centre, style = Stroke(1.dp.toPx()))
        }
        drawLine(ringColour, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y))
        drawLine(ringColour, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius))
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (text, az) ->
            val style = TextStyle(
                fontFamily = PlexSans, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                color = if (text == "N") Palette.TextPrimary else labelColour,
            )
            val layout = measurer.measure(text, style)
            val edge = at(SkyPoint(az, 0f))
            val outward = Offset(edge.x - centre.x, edge.y - centre.y) / radius * 10.dp.toPx()
            drawText(
                layout,
                topLeft = edge + outward -
                    Offset(layout.size.width / 2f, layout.size.height / 2f),
            )
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
                drawPath(path, cn0Colour(cell.meanCn0DbHz).copy(alpha = 0.55f))
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
            if (state.mapMode) break
            val colour = marker.constellation.color()
            val faded = if (marker.current == null) colour.copy(alpha = 0.3f) else colour.copy(alpha = 0.6f)
            marker.trail.filter { it.size > 1 }.forEach { segment ->
                drawPath(pathOf(segment.map(::at)), faded, style = Stroke(stroke))
            }
            if (marker.projection.size > 1) {
                drawPath(
                    pathOf(marker.projection.map(::at)),
                    colour.copy(alpha = 0.8f),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
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
        TextStyle(fontFamily = PlexMono, color = colour.copy(alpha = if (marker.tracked) 1f else 0.5f), fontSize = 10.sp),
    )
    drawText(layout, topLeft = position + Offset(dot + 2.dp.toPx(), -layout.size.height / 2f))
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun cn0Colour(cn0: Float): Color = when {
    cn0 >= 35f -> Palette.Good
    cn0 >= 25f -> Palette.Degraded
    else -> Palette.Bad
}
