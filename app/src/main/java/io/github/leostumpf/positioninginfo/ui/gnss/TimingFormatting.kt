// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.domain.ClockOffset
import java.util.Locale
import kotlin.math.abs

/** How a timing figure should be tinted: settled, still in progress, or worth a look. */
enum class TimingTone { GOOD, PENDING, WARN, NONE }

data class TimingText(val text: String, val tone: TimingTone)

/** Tenths of a second below a minute, where they carry information; m:ss above it. */
fun formatDuration(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    return if (clamped < 60_000L) {
        String.format(Locale.US, "%.1f s", clamped / 1000.0)
    } else {
        val totalSeconds = clamped / 1000L
        String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

fun TimingUiState.firstFixText(gpsEnabled: Boolean): TimingText = when {
    firstFixMs != null -> TimingText(formatDuration(firstFixMs), TimingTone.GOOD)
    !gpsEnabled -> TimingText("--", TimingTone.NONE)
    searchingForMs != null -> TimingText("searching… ${formatDuration(searchingForMs)}", TimingTone.PENDING)
    else -> TimingText("--", TimingTone.NONE)
}

fun TimingUiState.clockText(): TimingText {
    val offset = clockOffsetMs ?: return TimingText("--", TimingTone.NONE)
    if (abs(offset) < ClockOffset.SYNC_TOLERANCE_MS) {
        return TimingText("in sync (within 1 s)", TimingTone.GOOD)
    }
    val direction = if (offset > 0) "ahead of" else "behind"
    return TimingText("${formatDuration(abs(offset))} $direction GNSS time", TimingTone.WARN)
}
