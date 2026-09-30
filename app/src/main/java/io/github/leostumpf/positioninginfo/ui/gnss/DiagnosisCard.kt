// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.ui.common.tinted
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.leostumpf.positioninginfo.domain.CheckStatus
import io.github.leostumpf.positioninginfo.domain.Diagnosis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color

/**
 * "Why no fix?": the verdict first, then every link of the chain with its status. Open while
 * there is no fix; folded to one line once everything passes.
 */
@Composable
internal fun DiagnosisCard(d: Diagnosis, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(d.fixed) { mutableStateOf(!d.fixed || d.verdictStatus != CheckStatus.OK) }
    val tone = d.verdictStatus.tone()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().tinted(tone, shape)
            .clickable(onClickLabel = if (expanded) "Hide the checks" else "Show all checks") { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "expanded" else "collapsed" }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WHY NO FIX?", style = OverlineStyle, color = Palette.TextTertiary, modifier = Modifier.weight(1f))
            Text(
                if (expanded) "▾" else "▸", style = OverlineStyle, color = Palette.TextTertiary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).background(tone.color, CircleShape))
            Text(d.verdict, style = BodyStyle.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp), color = Palette.TextPrimary)
        }
        Text(d.detail, style = BodyStyle.copy(fontSize = 13.sp, lineHeight = 19.sp), color = Palette.TextSecondary)
        if (expanded) {
            Column(Modifier.padding(top = 6.dp)) {
                d.checks.forEachIndexed { i, check ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(6.dp).background(check.status.tone().color, CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(check.label, style = BodyStyle.copy(fontSize = 14.sp), color = Palette.TextSecondary)
                            if (check.status != CheckStatus.OK) {
                                check.hint?.let { Text(it, style = BodyStyle.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Palette.TextTertiary) }
                            }
                        }
                        Text(check.value, style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp))
                    }
                    if (i < d.checks.lastIndex) HorizontalDivider(color = Palette.Divider)
                }
            }
        }
    }
}

internal fun CheckStatus.tone(): Tone = when (this) {
    CheckStatus.OK -> Tone.GOOD
    CheckStatus.WARN -> Tone.DEGRADED
    CheckStatus.FAIL -> Tone.BAD
    CheckStatus.INFO -> Tone.NEUTRAL
}
