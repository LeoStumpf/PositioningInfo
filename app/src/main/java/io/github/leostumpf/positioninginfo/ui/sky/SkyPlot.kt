// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.ObstructionCell
import io.github.leostumpf.positioninginfo.domain.SkyPoint
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.theme.MonoFamily
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.SansFamily
import io.github.leostumpf.positioninginfo.ui.theme.color
import io.github.leostumpf.positioninginfo.ui.theme.signalAlpha
import io.github.leostumpf.positioninginfo.ui.theme.signalColour
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sky disc: horizon at the rim, zenith in the centre, north up unless [SkyUiState.compassMode]
 * turns it with the phone. Draws either the satellites' paths or the signal map underneath them.
 */
@Composable
internal fun SkyPlot(state: SkyUiState, modifier: Modifier = Modifier) {
    val markers = state.markers
    val rotation = if (state.compassMode) state.headingDegrees ?: 0f else 0f
    // Room for every satellite label plus the compass letters; the default of 8 would
    // re-measure most of them on every frame.
    val measurer = rememberTextMeasurer(cacheSize = 64)
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
        val sky = SkyProjection(
            centre = Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension / 2f - 18.dp.toPx(),
            rotation = rotation,
        )
        drawSkyDisc(sky, measurer)
        if (state.mapMode) drawSignalMap(sky, state.obstruction)
        if (state.compassMode) drawPhoneTop(sky)
        if (!state.mapMode && state.showPaths) markers.forEach { drawPaths(sky, it) }
        for (marker in markers) {
            val current = marker.current ?: continue
            drawSatellite(sky.at(current), marker, measurer)
        }
    }
}

/** Places sky positions on a disc of [radius] around [centre], turned by [rotation] degrees. */
private class SkyProjection(val centre: Offset, val radius: Float, val rotation: Float) {
    /** Distance from the centre of a given elevation: zero at the zenith, [radius] on the horizon. */
    fun ring(elevationDegrees: Float) = radius * (ZENITH_DEG - elevationDegrees.coerceIn(0f, ZENITH_DEG)) / ZENITH_DEG

    fun at(point: SkyPoint): Offset {
        val r = ring(point.elevationDegrees)
        val az = Math.toRadians((point.azimuthDegrees - rotation).toDouble())
        return Offset(centre.x + r * sin(az).toFloat(), centre.y - r * cos(az).toFloat())
    }
}

/** The disc, elevation rings at 30° and 60°, the horizon, the compass cross and its letters. */
private fun DrawScope.drawSkyDisc(sky: SkyProjection, measurer: TextMeasurer) {
    val centre = sky.centre
    val radius = sky.radius
    drawCircle(Palette.PlotBackground, radius, centre)
    drawCircle(Palette.Outline, radius, centre, style = Stroke(1.dp.toPx()))
    for (elevation in ELEVATION_RINGS_DEG) {
        drawCircle(Palette.Hairline, sky.ring(elevation), centre, style = Stroke(1.dp.toPx()))
    }
    drawLine(Palette.Hairline, Offset(centre.x - radius, centre.y), Offset(centre.x + radius, centre.y))
    drawLine(Palette.Hairline, Offset(centre.x, centre.y - radius), Offset(centre.x, centre.y + radius))
    COMPASS_POINTS.forEach { (text, az) ->
        val style = TextStyle(
            fontFamily = SansFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (text == "N") Palette.TextPrimary else Palette.TextTertiary,
        )
        val layout = measurer.measure(text, style)
        val edge = sky.at(SkyPoint(az, 0f))
        val outward = Offset(edge.x - centre.x, edge.y - centre.y) / radius * 10.dp.toPx()
        drawText(layout, topLeft = edge + outward - Offset(layout.size.width / 2f, layout.size.height / 2f))
    }
}

/** Annular sectors, one per bin, coloured by mean C/N0. */
private fun DrawScope.drawSignalMap(sky: SkyProjection, cells: List<ObstructionCell>) {
    for (cell in cells) {
        val outer = sky.ring(cell.elevationFrom)
        val inner = sky.ring(cell.elevationTo)
        // Canvas arcs start at three o'clock; azimuth 0 is twelve.
        val start = cell.azimuthFrom - sky.rotation - QUARTER_TURN_DEG
        val sweep = cell.azimuthTo - cell.azimuthFrom
        val path = Path().apply {
            arcTo(Rect(sky.centre, outer), start, sweep, true)
            arcTo(Rect(sky.centre, inner), start + sweep, -sweep, false)
            close()
        }
        drawPath(path, signalColour(cell.meanCn0DbHz).copy(alpha = signalAlpha(cell.meanCn0DbHz)))
    }
}

/** A small triangle above the rim marking the phone's top edge, for compass mode. */
private fun DrawScope.drawPhoneTop(sky: SkyProjection) {
    val tip = Offset(sky.centre.x, sky.centre.y - sky.radius - 2.dp.toPx())
    drawPath(
        Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x - 6.dp.toPx(), tip.y - 10.dp.toPx())
            lineTo(tip.x + 6.dp.toPx(), tip.y - 10.dp.toPx())
            close()
        },
        Palette.TextTertiary,
    )
}

/** Where the satellite has been (solid, fainter once it is gone) and where it is heading (dashed). */
private fun DrawScope.drawPaths(sky: SkyProjection, marker: SkyMarker) {
    val stroke = 2.dp.toPx()
    val colour = marker.constellation.color()
    val trail = colour.copy(alpha = if (marker.current == null) GONE_TRAIL_ALPHA else TRAIL_ALPHA)
    marker.trail.filter { it.size > 1 }.forEach { segment ->
        drawPath(pathOf(segment.map(sky::at)), trail, style = Stroke(stroke))
    }
    if (marker.projection.size > 1) {
        drawPath(
            pathOf(marker.projection.map(sky::at)),
            colour.copy(alpha = PROJECTION_ALPHA),
            style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
        )
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

private const val ZENITH_DEG = 90f
private const val QUARTER_TURN_DEG = 90f
private val ELEVATION_RINGS_DEG = listOf(30f, 60f)
private val COMPASS_POINTS = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)

private const val TRAIL_ALPHA = 0.6f
private const val GONE_TRAIL_ALPHA = 0.3f
private const val PROJECTION_ALPHA = 0.8f
