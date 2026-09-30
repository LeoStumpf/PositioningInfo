// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color
import io.github.leostumpf.positioninginfo.ui.theme.signalAlpha

/** What the marks on the plot mean, for the mode it is in. */
@Composable
internal fun Legend(mapMode: Boolean) {
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
