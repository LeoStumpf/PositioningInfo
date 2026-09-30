// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.HeroStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TileValueStyle

// --- Values ----------------------------------------------------------------------------

/** A page's main reading: big value, lighter unit, caption. */
@Composable
fun HeroValue(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = Palette.TextPrimary,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = valueColor)) { append(value) }
                unit?.let { withStyle(SpanStyle(fontSize = 24.sp, color = Palette.TextSecondary)) { append(" $it") } }
            },
            style = HeroStyle,
        )
        caption?.let { Text(it, style = CaptionStyle, color = Palette.TextTertiary) }
    }
}

/** A label on the left, a monospaced value on the right, an optional detail line. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    valueColor: Color? = null,
    divider: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = BodyStyle, color = Palette.TextSecondary)
                detail?.let { Text(it, style = CaptionStyle, color = Palette.TextTertiary) }
            }
            // Capped so a long value (a chipset name) wraps instead of squeezing the label.
            Text(
                value,
                style = DataStyle,
                textAlign = TextAlign.End,
                color = valueColor ?: Palette.TextPrimary,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        if (divider) HorizontalDivider(color = Palette.Divider)
    }
}

/**
 * Stat tiles side by side, as many as fit: all in one row normally, two per row when the
 * system font is large, so a label such as "ephemeris" is never broken mid-word.
 */
@Composable
fun TileRow(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier, spacing: Dp = 8.dp) {
    val perRow = if (LocalDensity.current.fontScale > 1.3f && tiles.size > 2) 2 else tiles.size.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A small tile in a grid of figures. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    footnote: String? = null,
    tone: Tone? = null,
) {
    val accent = tone?.color
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent?.copy(alpha = 0.10f) ?: Palette.Surface)
            .border(1.dp, accent?.copy(alpha = 0.35f) ?: Palette.CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent ?: Palette.TextPrimary)) { append(value) }
                unit?.let { withStyle(SpanStyle(fontSize = 15.sp, color = Palette.TextSecondary)) { append(" $it") } }
            },
            style = TileValueStyle,
            maxLines = 1,
        )
        footnote?.let {
            Text(
                it,
                style = OverlineStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
                color = Palette.Inactive,
            )
        }
    }
}
