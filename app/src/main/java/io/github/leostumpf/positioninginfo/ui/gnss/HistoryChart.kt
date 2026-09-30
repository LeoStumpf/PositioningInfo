// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.domain.History
import io.github.leostumpf.positioninginfo.domain.HistorySample
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color
import java.util.Locale

/**
 * One quantity over the last half hour: a line on a fixed time axis ending now, broken
 * where the app was in the background, with the current value and the range beside it.
 */
@Composable
internal fun HistoryChart(
    label: String,
    samples: List<HistorySample>,
    value: (HistorySample) -> Float?,
    unit: String,
    decimals: Int,
    lowerIsBetter: Boolean = false,
) {
    val points = samples.mapNotNull { s -> value(s)?.let { s.atMs to it } }
    val fmt = { v: Float -> String.format(Locale.US, "%.${decimals}f", v) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                label,
                style = BodyStyle.copy(fontSize = 14.sp),
                color = Palette.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                points.lastOrNull()?.let { fmt(it.second) + unit } ?: "—",
                style = DataStyle,
                color = Palette.TextPrimary,
            )
        }
        Canvas(
            Modifier.fillMaxWidth().height(44.dp).semantics {
                val range = points.takeIf { it.isNotEmpty() }?.let { p ->
                    ", from ${fmt(p.minOf { it.second })} to ${fmt(p.maxOf { it.second })}$unit"
                }
                contentDescription = "$label over the last 30 minutes${range.orEmpty()}"
            },
        ) {
            drawHistory(points, samples.lastOrNull()?.atMs ?: 0L)
        }
        Row {
            Text(
                "30 min ago",
                style = CaptionStyle.copy(fontSize = 11.sp),
                color = Palette.TextTertiary,
                modifier = Modifier.weight(1f),
            )
            if (points.isNotEmpty()) {
                val lo = points.minOf { it.second }
                val hi = points.maxOf { it.second }
                Text(
                    (if (lowerIsBetter) "best ${fmt(lo)} · worst ${fmt(hi)}" else "range ${fmt(lo)}–${fmt(hi)}") + unit,
                    style = CaptionStyle.copy(fontSize = 11.sp),
                    color = Palette.TextTertiary,
                )
            }
        }
    }
}

/**
 * The trace over the last [History.WINDOW_MS] up to [endMs], scaled to its own range, broken
 * where samples are missing for longer than [History.GAP_MS], with a dot on the latest value.
 */
private fun DrawScope.drawHistory(points: List<Pair<Long, Float>>, endMs: Long) {
    drawLine(Palette.Hairline, Offset(0f, size.height), Offset(size.width, size.height))
    if (points.size < 2) return
    val start = endMs - History.WINDOW_MS
    val min = points.minOf { it.second }
    val span = (points.maxOf { it.second } - min).takeIf { it > 0f } ?: 1f
    fun x(t: Long) = ((t - start).toFloat() / History.WINDOW_MS) * size.width
    fun y(v: Float) = size.height - 3.dp.toPx() - ((v - min) / span) * (size.height - 6.dp.toPx())
    val path = Path()
    var previous: Long? = null
    points.forEach { (t, v) ->
        if (previous == null || t - previous > History.GAP_MS) path.moveTo(x(t), y(v)) else path.lineTo(x(t), y(v))
        previous = t
    }
    drawPath(path, Palette.TextPrimary, style = Stroke(1.5.dp.toPx()))
    drawCircle(Palette.TextPrimary, 2.5.dp.toPx(), Offset(x(points.last().first), y(points.last().second)))
}
