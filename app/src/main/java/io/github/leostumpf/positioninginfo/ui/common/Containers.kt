// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import java.util.Locale

// --- Containers ------------------------------------------------------------------------

/** The standard card: a bordered, rounded surface that stacks its content. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
            .padding(padding),
        content = content,
    )
}

/** How good a state is, and the colour that says so. */
enum class Tone(val color: Color) {
    GOOD(Palette.Good),
    DEGRADED(Palette.Degraded),
    BAD(Palette.Bad),
    NEUTRAL(Palette.TextTertiary),
}

/** A short explanatory box; tinted when it reports a state, dashed when it only explains. */
@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tone: Tone = Tone.NEUTRAL) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .tinted(tone, shape, neutralBorder = Palette.Outline)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(tone.color, CircleShape))
        Text(
            text,
            style = CaptionStyle,
            color = if (tone == Tone.NEUTRAL) Palette.TextSecondary else Palette.TextPrimary,
        )
    }
}

/**
 * A card or badge tinted by its tone: plain surface and border when neutral, a wash of the
 * tone's colour otherwise. Clips to [shape].
 */
fun Modifier.tinted(
    tone: Tone,
    shape: Shape,
    neutralFill: Color = Palette.Surface,
    neutralBorder: Color = Palette.CardBorder,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.3f,
): Modifier = clip(shape)
    .background(if (tone == Tone.NEUTRAL) neutralFill else tone.color.copy(alpha = fillAlpha))
    .border(1.dp, if (tone == Tone.NEUTRAL) neutralBorder else tone.color.copy(alpha = borderAlpha), shape)

/** Title (and whatever goes under it) on the left, a close button on the right: every sheet's top. */
@Composable
fun SheetHeader(onClose: () -> Unit, modifier: Modifier = Modifier, title: @Composable ColumnScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = title)
        CircleIconButton(AppIcons.Close, contentDescription = "Close", onClick = onClose)
    }
}

/** Asks before something that cannot be undone; the confirming action is shown in red. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        title = { Text(title) },
        text = { Text(text, color = Palette.TextSecondary) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            ) { Text(confirmLabel, color = Palette.Bad) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    )
}

/** Plain explanatory text under a section. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(text, style = CaptionStyle, color = color, modifier = modifier)
}

/** A small pill with a coloured dot and an upper-case label, tinted by its tone. */
@Composable
fun StatusBadge(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier
            .heightIn(min = 26.dp)
            .tinted(tone, shape, Palette.SurfaceRaised, Palette.Outline, fillAlpha = 0.14f, borderAlpha = 0.4f)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(tone.color, CircleShape))
        Text(
            text.uppercase(Locale.ROOT),
            style = OverlineStyle.copy(letterSpacing = 0.06.em()),
            color = if (tone == Tone.NEUTRAL) Palette.TextSecondary else tone.color,
        )
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)
