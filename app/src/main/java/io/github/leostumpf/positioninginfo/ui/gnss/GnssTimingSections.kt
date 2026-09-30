// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.domain.AlmanacReadiness
import io.github.leostumpf.positioninginfo.domain.FixDiagnosis
import io.github.leostumpf.positioninginfo.domain.formatDuration
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.rememberLogTimeFormat
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.sky.label
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import java.util.Date

/* The GNSS page's time sections: first fix and clocks, the last 30 minutes, the first-fix log. */

/** Time to first fix, and how far the phone's clock is from GNSS and network time. */
internal fun LazyListScope.timingSection(state: GnssUiState) {
    section("Timing")
    item { TimingRow("Time to first fix", state.timing.firstFixText(state.gpsEnabled)) }
    item { TimingRow("Phone clock vs GNSS", state.timing.clockText()) }
    val networkVsGnss = state.timing.networkVsGnssText()
    item { TimingRow("Phone clock vs network", state.timing.networkClockText(), divider = networkVsGnss != null) }
    networkVsGnss?.let { item { TimingRow("Network time vs GNSS", it, divider = false) } }
}

/** Satellites in the fix, signal and accuracy over the last half hour. */
internal fun LazyListScope.historySection(state: GnssUiState) {
    if (state.history.size <= 1) return
    section("Last 30 minutes")
    item {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HistoryChart("Satellites in fix", state.history, { it.usedInFix.toFloat() }, "", decimals = 0)
            HistoryChart("Mean signal, C/N₀", state.history, { it.meanCn0 }, " dB-Hz", decimals = 0)
            HistoryChart("Accuracy", state.history, { it.accuracyM }, " m", decimals = 1, lowerIsBetter = true)
        }
    }
}

/** The newest times to first fix kept on the phone, each judged against its start type. */
internal fun LazyListScope.firstFixLogSection(state: GnssUiState) {
    if (state.ttffLog.isEmpty()) return
    section("Recent first fixes", trailing = "stored on this phone")
    val shown = state.ttffLog.takeLast(TTFF_SHOWN).reversed()
    shown.forEachIndexed { i, e ->
        item {
            val slow = e.ttffMs > FixDiagnosis.expectedMs(e.startType)
            ValueRow(
                rememberLogTimeFormat().format(Date(e.utcMs)),
                formatDuration(e.ttffMs),
                // Slow is said as well as coloured, for anyone who cannot tell the two apart.
                detail = e.startType.startLabel() + if (slow) " · slower than usual" else "",
                valueColor = if (slow) Palette.Degraded else Palette.Good,
                divider = i < shown.lastIndex,
            )
        }
    }
}

@Composable
private fun TimingRow(label: String, value: TimingText, divider: Boolean = true) {
    ValueRow(
        label,
        value.text,
        valueColor = when (value.tone) {
            TimingTone.GOOD -> Palette.Good
            TimingTone.PENDING, TimingTone.WARN -> Palette.Degraded
            TimingTone.NONE -> Palette.TextSecondary
        },
        divider = divider,
    )
}

/** The newest first fixes shown; the log keeps more. */
private const val TTFF_SHOWN = 5

private fun AlmanacReadiness.startLabel(): String = when (this) {
    AlmanacReadiness.HOT -> "hot start"
    AlmanacReadiness.WARM -> "warm start"
    AlmanacReadiness.COLD -> "cold start"
    AlmanacReadiness.UNKNOWN -> "start type unknown"
}
