// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import io.github.leostumpf.positioninginfo.domain.ClockOffset
import io.github.leostumpf.positioninginfo.domain.formatDuration
import java.util.Locale
import kotlin.math.abs

/** How a timing figure should be tinted: settled, still in progress, or worth a look. */
enum class TimingTone { GOOD, PENDING, WARN, NONE }

/** A timing figure as shown, and how to tint it. */
data class TimingText(val text: String, val tone: TimingTone)

/** Time to first fix, or "searching…" with the time so far; "--" while location is off. */
fun TimingUiState.firstFixText(gpsEnabled: Boolean): TimingText = when {
    firstFixMs != null -> TimingText(formatDuration(firstFixMs), TimingTone.GOOD)
    !gpsEnabled -> TimingText("--", TimingTone.NONE)
    searchingForMs != null -> TimingText("searching… ${formatDuration(searchingForMs)}", TimingTone.PENDING)
    else -> TimingText("--", TimingTone.NONE)
}

/** Phone clock against GNSS time: from this session's fixes, else from the system's last GNSS time. */
fun TimingUiState.clockText(): TimingText {
    val offset = clockOffsetMs ?: systemGnssOffsetMs ?: return TimingText("--", TimingTone.NONE)
    return offsetText(offset, ClockOffset.SYNC_TOLERANCE_MS, "GNSS time")
}

/** Phone clock against the time the mobile network or a time server last gave it. */
fun TimingUiState.networkClockText(): TimingText {
    val offset = networkOffsetMs ?: return TimingText("not known", TimingTone.NONE)
    return offsetText(offset, NETWORK_TOLERANCE_MS, "network time")
}

/** Network time against GNSS time, when both are known: how good the network's time is. */
fun TimingUiState.networkVsGnssText(): TimingText? {
    val phoneMinusGnss = clockOffsetMs ?: systemGnssOffsetMs ?: return null
    val phoneMinusNetwork = networkOffsetMs ?: return null
    return offsetText(phoneMinusGnss - phoneMinusNetwork, ClockOffset.SYNC_TOLERANCE_MS, "GNSS time")
}

private fun offsetText(offsetMs: Long, toleranceMs: Long, reference: String) = TimingText(
    describeOffset(offsetMs, toleranceMs, reference),
    if (abs(offsetMs) < toleranceMs) TimingTone.GOOD else TimingTone.WARN,
)

/** Time servers are good to a few milliseconds, the mobile network's time signal to about a second. */
private const val NETWORK_TOLERANCE_MS = 500L

/**
 * One clock's offset in words: "in sync" inside [toleranceMs], otherwise how far and which
 * way. [offsetMs] is the first clock minus [reference], positive when it is ahead.
 */
internal fun describeOffset(offsetMs: Long, toleranceMs: Long, reference: String): String {
    if (kotlin.math.abs(offsetMs) < toleranceMs) {
        val tolerance = if (toleranceMs >= MS_PER_S) "${toleranceMs / MS_PER_S} s" else "$toleranceMs ms"
        return "in sync (within $tolerance)"
    }
    val size = kotlin.math.abs(offsetMs)
    val amount = when {
        size < HUNDREDTHS_BELOW_MS -> String.format(Locale.US, "%.2f s", size / MS_PER_S.toDouble())
        size < MS_PER_H -> "${size / MS_PER_S} s"
        else -> String.format(Locale.US, "%.1f h", size / MS_PER_H.toDouble())
    }
    return "$amount ${if (offsetMs > 0) "ahead of" else "behind"} $reference"
}

private const val MS_PER_S = 1_000L
private const val MS_PER_H = 3_600_000L

/** Offsets under ten seconds are shown to the hundredth. */
private const val HUNDREDTHS_BELOW_MS = 10_000L
