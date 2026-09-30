// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.domain.Band
import io.github.leostumpf.positioninginfo.domain.BandStatus
import io.github.leostumpf.positioninginfo.domain.InterferenceAssessment
import io.github.leostumpf.positioninginfo.domain.InterferenceMonitor
import io.github.leostumpf.positioninginfo.domain.SpoofingIndicator
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.common.tinted
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import java.util.Locale
import kotlin.math.abs

/* Interference: the verdict, AGC and signal strength per band against the baseline minute. */

/** Whether anything is interfering with the signals, band by band. */
internal fun LazyListScope.interferenceSection(state: ReceiverUiState) {
    val a = state.assessment
    section("Interference")
    if (a == null || (state.rawStatus != RawStreamStatus.READY && state.rawEpochs == 0)) {
        item { Notice(rawStatusText(state.rawStatus, "raw measurements")) }
    } else {
        item { Verdict(a) }
        if (a.bands.any { it.signals > 0 }) {
            item { BandHeader() }
            a.bands.filter { it.signals > 0 || it.band != Band.OTHER }.forEach { b -> item { BandRow(b) } }
        }
        item { ValueRow("Multipath flagged", "${a.multipathSignals} of ${a.totalSignals}", divider = false) }
    }
}

@Composable
private fun Verdict(a: InterferenceAssessment, modifier: Modifier = Modifier) {
    val (title, subtitle, tone) = when {
        a.jammingSuspected -> Triple("Possible jamming", "Gain and signal strength dropped together", Tone.BAD)

        a.spoofingIndicators.isNotEmpty() -> Triple(
            "Spoofing indicators",
            a.spoofingIndicators.first().describe(),
            Tone.DEGRADED,
        )

        a.epochs < BASELINE_EPOCHS -> Triple("Learning the baseline…", "Judged after the first minute", Tone.NEUTRAL)

        else -> Triple("No interference detected", "Gain and signal strength match the baseline", Tone.GOOD)
    }
    val shape = RoundedCornerShape(16.dp)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().tinted(tone, shape).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (tone == Tone.GOOD || tone == Tone.NEUTRAL) AppIcons.ShieldCheck else AppIcons.ShieldAlert,
                contentDescription = null,
                tint = tone.color,
                modifier = Modifier.size(28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TitleStyle, color = Palette.TextPrimary)
                Text(subtitle, style = CaptionStyle, color = Palette.TextSecondary)
            }
        }
        // The first indicator is the verdict's subtitle; any further ones follow as notes.
        for (indicator in a.spoofingIndicators.drop(1)) {
            Note("• ${indicator.describe()}", Modifier.padding(top = 6.dp), color = Palette.Degraded)
        }
    }
}

private val BandWeights = listOf(1.4f, 1f, 1f, 0.6f)

@Composable
private fun BandHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        listOf("BAND", "AGC dB", "C/N₀", "SIG").forEachIndexed { i, h ->
            Text(
                h,
                style = OverlineStyle.copy(fontWeight = FontWeight.Normal),
                color = Palette.TextTertiary,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(BandWeights[i]),
            )
        }
    }
}

@Composable
private fun BandRow(b: BandStatus) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                b.band.label(),
                style = BodyStyle.copy(fontSize = 13.sp),
                color = Palette.TextPrimary,
                modifier = Modifier.weight(BandWeights[0]),
            )
            ValueWithDelta(
                b.agcDb,
                b.agcDropDb?.let { -it },
                alarm = (b.agcDropDb ?: 0.0) >= 6.0,
                modifier = Modifier.weight(BandWeights[1]),
            )
            ValueWithDelta(
                b.meanCn0DbHz,
                if (b.meanCn0DbHz != null && b.cn0BaselineDbHz != null) b.meanCn0DbHz - b.cn0BaselineDbHz else null,
                alarm = false,
                modifier = Modifier.weight(BandWeights[2]),
            )
            Text(
                b.signals.toString(),
                style = DataStyle,
                color = Palette.TextPrimary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(BandWeights[3]),
            )
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

/** A value with its change against the baseline in small type: "−58.6 +0.1". */
@Composable
private fun ValueWithDelta(value: Double?, delta: Double?, alarm: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Text(
            value?.fmt(1)?.replace("-", "−") ?: DASH,
            style = DataStyle,
            color = if (alarm) Palette.Bad else Palette.TextPrimary,
        )
        delta?.let {
            Text(
                " ${if (it >= 0) "+" else "−"}${abs(it).fmt(1)}",
                style = DataStyle.copy(fontSize = 10.sp),
                color = Palette.TextTertiary,
            )
        }
    }
}

/** One epoch a second for [InterferenceMonitor.BASELINE_MS]: the baseline minute. */
private const val BASELINE_EPOCHS = (InterferenceMonitor.BASELINE_MS / 1_000).toInt()

internal fun Band.label(): String = when (this) {
    Band.L1_E1_B1 -> "L1/E1/B1"
    Band.L5_E5A_B2A -> "L5/E5a/B2a"
    Band.OTHER -> "Other"
}

/** An indicator in words: cautious, because each is a reason to look closer, not a verdict. */
internal fun SpoofingIndicator.describe(): String = when (this) {
    is SpoofingIndicator.UniformStrength ->
        "${band.label()}: $signals signals are unusually alike in strength " +
            "(%.0f dB-Hz ± %.1f). Real satellites at different elevations usually differ more; ".format(
                Locale.US,
                meanDbHz,
                spreadDb,
            ) +
            "one transmitter could make them alike."

    is SpoofingIndicator.PowerWithStrongerSignals ->
        "${band.label()}: more power in the band (AGC %.0f dB below baseline) ".format(Locale.US, agcDropDb) +
            "while signals got stronger. This could be a source stronger than the sky."

    is SpoofingIndicator.DriftJump ->
        "The clock drift jumped by more than %.1f ppm between epochs, which an oscillator ".format(
            Locale.US,
            thresholdPpm,
        ) +
            "rarely does on its own."
}
