// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.ui.theme.signalColour
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.background
import io.github.leostumpf.positioninginfo.domain.AcquisitionStage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.SatelliteInfo
import io.github.leostumpf.positioninginfo.domain.ConstellationSummary
import io.github.leostumpf.positioninginfo.domain.SatelliteId
import io.github.leostumpf.positioninginfo.domain.SignalBand
import io.github.leostumpf.positioninginfo.domain.band
import io.github.leostumpf.positioninginfo.ui.common.LevelBar
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.color

private val ColumnWeights = listOf(2.4f, 1f, 1f, 1f, 1f)

@Composable
internal fun ConstellationHeader() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        listOf("SYSTEM", "VIS", "ALM", "EPH", "FIX").forEachIndexed { i, h ->
            Text(h, style = OverlineStyle.copy(fontWeight = FontWeight.Normal), color = Palette.TextTertiary,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(ColumnWeights[i]))
        }
    }
}

@Composable
internal fun ConstellationRow(summary: ConstellationSummary, last: Boolean) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(ColumnWeights[0]), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(summary.constellation.color(), CircleShape))
                Column {
                    Text(summary.constellation.label, style = BodyStyle, color = Palette.TextPrimary)
                    if (summary.constellation.operator.isNotEmpty()) {
                        Text(summary.constellation.operator, style = BodyStyle.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Palette.TextTertiary)
                    }
                }
            }
            listOf(summary.visible, summary.almanac, summary.ephemeris).forEachIndexed { i, v ->
                Text(v.toString(), style = DataStyle, color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(ColumnWeights[i + 1]))
            }
            Text(
                summary.usedInFix.toString(), style = DataStyle, textAlign = TextAlign.End,
                color = if (summary.usedInFix > 0) Palette.Good else Palette.TextPrimary, modifier = Modifier.weight(ColumnWeights[4]),
            )
        }
        if (!last) HorizontalDivider(color = Palette.Divider)
    }
}

@Composable
internal fun SatelliteRow(satellite: SatelliteInfo, detail: SignalDetail?, onClick: () -> Unit) {
    val color = satellite.constellation.color()
    val heard = satellite.cn0DbHz > 0f
    // One spoken line instead of "G07, L1, 42, A E": the letters and grey levels mean nothing aloud.
    val spoken = buildString {
        append("${satellite.code()}, ${satellite.constellation.label}")
        satellite.band?.shortLabel()?.let { append(", $it") }
        append(if (heard) ", ${satellite.cn0DbHz.roundToInt()} dB-Hz" else ", not heard")
        if (satellite.usedInFix) append(", in the fix")
        detail?.stage?.takeIf { heard }?.let { append(", ${it.label}") }
        append(if (satellite.hasAlmanac) ", almanac" else ", no almanac")
        append(if (satellite.hasEphemeris) ", ephemeris" else ", no ephemeris")
    }
    Column(
        Modifier.alpha(if (heard) 1f else 0.55f)
            .clickable(onClickLabel = "Show satellite details", onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(8.dp).then(
                    if (satellite.usedInFix) Modifier.background(color, CircleShape)
                    else Modifier.border(if (heard) 1.5.dp else 1.dp, color, CircleShape),
                ),
            )
            Text(satellite.code(), style = DataStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = color, modifier = Modifier.widthIn(min = 40.dp))
            Text(satellite.band?.shortLabel() ?: "—", style = DataStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary, modifier = Modifier.widthIn(min = 24.dp))
            StageGauge(if (heard) detail?.stage else null)
            LevelBar((satellite.cn0DbHz / GOOD_SIGNAL_DB_HZ), signalColour(satellite.cn0DbHz), Modifier.weight(1f))
            Text(if (heard) "%.0f".format(Locale.US, satellite.cn0DbHz) else "—", style = DataStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 24.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = if (satellite.hasAlmanac) Palette.TextPrimary else Palette.Inactive)) { append("A ") }
                    withStyle(SpanStyle(color = if (satellite.hasEphemeris) Palette.TextPrimary else Palette.Inactive)) { append("E") }
                },
                style = DataStyle.copy(fontSize = 11.sp),
                modifier = Modifier.widthIn(min = 32.dp),
            )
        }
        HorizontalDivider(color = Palette.RowDivider)
    }
}

private const val GOOD_SIGNAL_DB_HZ = 50f

/** "G07", "E24": the RINEX letter plus a two-digit number. */
internal fun SatelliteInfo.code(): String = SatelliteId(constellation, svid).label()

/** "L5 / E5a / B2a" is too wide for a list row; the first name identifies the band. */
internal fun SignalBand.shortLabel(): String? =
    if (this == SignalBand.UNKNOWN) null else label.substringBefore(" /")


/** Four ticks, one per acquisition step; filled up to the step reached. */
@Composable
private fun StageGauge(stage: AcquisitionStage?) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(AcquisitionStage.STEPS) { i ->
            val done = stage != null && i < stage.step
            Box(
                Modifier.size(width = 4.dp, height = 10.dp).background(
                    when {
                        stage == null -> Palette.Divider
                        !done -> Palette.Hairline
                        stage == AcquisitionStage.TIME_DECODED -> Palette.Good
                        else -> Palette.TextSecondary
                    },
                    RoundedCornerShape(1.dp),
                ),
            )
        }
    }
}
