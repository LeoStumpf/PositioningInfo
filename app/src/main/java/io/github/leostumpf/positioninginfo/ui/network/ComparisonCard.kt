// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.CondensedFamily
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the network position lies relative to GNSS, drawn to scale inside its claimed circle. */
@Composable
internal fun Comparison(state: NetworkUiState) {
    val c = state.comparison
    InfoCard {
        if (c == null) {
            Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
            Text(
                state.comparisonUnavailableReason,
                style = CaptionStyle,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
            return@InfoCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val claimed = state.accuracyM?.toDouble()
            Canvas(
                Modifier.size(88.dp).semantics {
                    contentDescription = "Network position ${formatDistance(c.distanceM)} from the GNSS fix" +
                        (claimed?.let { ", claimed accuracy ${formatDistance(it)}" } ?: "")
                },
            ) {
                drawComparison(c.distanceM, claimed, outside = c.withinClaimed == false)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("REAL ERROR VS GNSS", style = OverlineStyle, color = Palette.TextTertiary)
                Text(
                    buildAnnotatedString {
                        val (value, unit) = distanceParts(c.distanceM)
                        append(value)
                        withStyle(SpanStyle(fontSize = 16.sp, color = Palette.TextSecondary)) { append(" $unit off") }
                    },
                    style = BodyStyle.copy(fontFamily = CondensedFamily, fontSize = 32.sp, lineHeight = 36.sp),
                    color = Palette.TextPrimary,
                )
                c.withinClaimed?.let {
                    Text(
                        if (it) "Inside the claimed circle" else "Outside the claimed circle",
                        style = CaptionStyle,
                        color = if (it) Palette.Good else Palette.Degraded,
                    )
                }
                Text(
                    "GNSS reference ±${c.gnssAccuracyM.roundToInt()} m",
                    style = CaptionStyle.copy(fontSize = 12.sp),
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}

/**
 * The GNSS fix as a dot in the middle, the claimed accuracy as a dashed circle around it and
 * the network position as a ring at its real distance, all to one scale that fits both.
 */
private fun DrawScope.drawComparison(distanceM: Double, claimedM: Double?, outside: Boolean) {
    val centre = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension / 2 - 4.dp.toPx()
    val scale = r / maxOf(claimedM?.takeIf { it > 0 } ?: 0.0, distanceM, 1.0)
    claimedM?.let {
        drawCircle(
            Palette.TextTertiary,
            (it * scale).toFloat(),
            centre,
            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_PX, DASH_PX))),
        )
    }
    drawCircle(Palette.TextPrimary, 4.dp.toPx(), centre)
    // Drawn up and to the right: the direction is unknown, only the distance is real.
    val offset = min((distanceM * scale).toFloat(), r)
    drawCircle(
        if (outside) Palette.Degraded else Palette.Good,
        3.dp.toPx(),
        Offset(centre.x + offset * DIRECTION_X, centre.y - offset * DIRECTION_Y),
        style = Stroke(1.5.dp.toPx()),
    )
}

private const val DASH_PX = 8f

/** A 3-4-5 triangle: the unit vector pointing up and to the right. */
private const val DIRECTION_X = 0.8f
private const val DIRECTION_Y = 0.6f
