// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette

// --- Page indicator --------------------------------------------------------------------

/** Tick marks with the neighbouring pages named, so the other tools are discoverable. */
@Composable
fun PageIndicator(current: Int, modifier: Modifier = Modifier) {
    val pages = Page.entries
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pages.getOrNull(current - 1)?.shortName.orEmpty(),
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = Palette.TextTertiary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.size(width = if (i == current) 22.dp else 8.dp, height = 3.dp)
                        .background(if (i == current) Palette.TextPrimary else Palette.Outline, RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            pages.getOrNull(current + 1)?.shortName.orEmpty(),
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = Palette.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
    }
}
