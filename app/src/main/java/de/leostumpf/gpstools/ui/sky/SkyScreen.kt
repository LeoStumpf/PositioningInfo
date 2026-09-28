// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.sky

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.domain.Constellation
import de.leostumpf.gpstools.domain.SkyPoint
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.ErrorRed
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.WarnAmber
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.sky) }
        item { Spacer(Modifier.height(20.dp)) }
        item { SectionLabel("SKY") }
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.compassMode,
                    onClick = onToggleCompass,
                    label = { Text("Compass") },
                )
                FilterChip(
                    selected = state.mapMode,
                    onClick = onToggleMap,
                    label = { Text("Signal map") },
                )
            }
        }
        state.headingText?.let { text ->
            item {
                Text(
                    text = text + if (state.compassUnreliable) " · compass needs calibrating (figure-eight)" else "",
                    style = StatusLineStyle,
                    color = if (state.compassUnreliable) WarnAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SkyPlot(state) }
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Text(
                text = if (state.mapMode) {
                    "Average signal strength by direction, from ${state.obstructionSamples} " +
                        "measurements: green strong (35+ dB-Hz), amber 25–35, red weak. Weak or " +
                        "empty regions near the horizon are buildings and trees. Fills in as " +
                        "satellites move — give it half an hour."
                } else {
                    "Solid: path so far. Dashed: next 15 minutes, estimated from recent " +
                        "motion — needs a couple of minutes of history, and cannot show " +
                        "satellites that have not risen yet."
                } + if (state.compassMode) " Hold the phone flat; the plot turns with it." else "",
                style = MaterialTheme.typography.bodySmall,
                color = DimGrey,
            )
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("SETTING SOON") }
        item { Spacer(Modifier.height(6.dp)) }
        if (state.upcoming.isEmpty()) {
            item { Note("No satellite expected to set in the next 15 minutes.") }
        } else {
            items(state.upcoming, key = { it.label }) {
                ListRow(it.label, "in ~${it.minutes.roundToInt().coerceAtLeast(1)} min")
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("EVENTS") }
        item { Spacer(Modifier.height(6.dp)) }
        if (state.events.isEmpty()) {
            item { Note("Satellites appearing and disappearing will be listed here.") }
        } else {
            items(state.events, key = { it.key }) { ListRow(it.text, it.ago) }
        }
    }
}

@Composable
private fun SkyPlot(state: SkyUiState) {
    val markers = state.markers
    val rotation = if (state.compassMode) state.headingDegrees ?: 0f else 0f
    val measurer = rememberTextMeasurer()
    val ringColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        Modifier
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

        // Elevation rings at 0°, 30° and 60°, and the compass cross.
        for (elevation in listOf(0f, 30f, 60f)) {
            drawCircle(ringColour, radius * (90f - elevation) / 90f, centre, style = Stroke(1.dp.toPx()))
        }
        drawLine(ringColour, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y))
        drawLine(ringColour, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius))
        val compassStyle = TextStyle(color = labelColour, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (text, az) ->
            val layout = measurer.measure(text, compassStyle)
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
            val colour = marker.constellation.colour()
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
    val colour = marker.constellation.colour()
    val dot = 6.dp.toPx()
    when {
        marker.usedInFix -> drawCircle(colour, dot, position)
        marker.tracked -> drawCircle(colour, dot, position, style = Stroke(2.dp.toPx()))
        else -> drawCircle(colour.copy(alpha = 0.4f), dot, position, style = Stroke(1.dp.toPx()))
    }
    val layout = measurer.measure(
        marker.label,
        TextStyle(color = colour.copy(alpha = if (marker.tracked) 1f else 0.5f), fontSize = 10.sp),
    )
    drawText(layout, topLeft = position + Offset(dot + 2.dp.toPx(), -layout.size.height / 2f))
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    points.drop(1).forEach { lineTo(it.x, it.y) }
}

private fun cn0Colour(cn0: Float): Color = when {
    cn0 >= 35f -> OkGreen
    cn0 >= 25f -> WarnAmber
    else -> ErrorRed
}

private fun Constellation.colour(): Color = when (this) {
    Constellation.GPS -> Color(0xFF7FD1FF)
    Constellation.GLONASS -> Color(0xFFFF8A80)
    Constellation.GALILEO -> Color(0xFFFFD54F)
    Constellation.BEIDOU -> Color(0xFFB39DDB)
    Constellation.QZSS -> Color(0xFF80CBC4)
    Constellation.IRNSS -> Color(0xFFF48FB1)
    Constellation.SBAS, Constellation.UNKNOWN -> Color(0xFF9E9E9E)
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
private fun ListRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = StatusLineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = DimGrey)
}
