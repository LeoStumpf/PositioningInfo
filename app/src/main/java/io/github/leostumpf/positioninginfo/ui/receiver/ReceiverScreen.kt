// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.domain.counted
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section

/**
 * What the chip reports below the position: interference, its clock, its NMEA output, the
 * satellites' own messages, and what the hardware supports.
 */
@Composable
fun ReceiverScreen(state: ReceiverUiState, modifier: Modifier = Modifier) {
    PageScaffold(Page.RECEIVER, modifier) {
        interferenceSection(state)
        clockSection(state)
        nmeaSection(state)
        navigationSection(state)
        hardwareSection(state)
    }
}

/** The receiver clock as the raw measurements describe it. */
private fun LazyListScope.clockSection(state: ReceiverUiState) {
    val a = state.assessment
    section("Receiver clock")
    if (a == null) {
        item { Note(rawStatusText(state.rawStatus, "raw measurements")) }
    } else {
        item {
            ValueRow(
                "Oscillator error",
                a.clockDriftPpm?.let { "${it.fmt(decimals = 3).replace("-", "−")} ppm" } ?: DASH,
                detail = a.clockDriftStdDevPpm?.let { "±${it.fmt(decimals = 4)} ppm over the last minute" },
            )
        }
        item { ValueRow("Leap seconds GPS − UTC", a.leapSecond?.let { "$it s" } ?: DASH) }
        item {
            ValueRow(
                "GNSS time",
                when (state.hasFullBias) {
                    true -> "absolute"
                    false -> "relative only"
                    null -> DASH
                },
            )
        }
        item { ValueRow("Clock discontinuities", a.clockDiscontinuities.toString()) }
        item { ValueRow("Carrier phase valid", signals(state.carrierPhaseValid), divider = false) }
    }
}

internal fun signals(n: Int) = n.counted("signal")

/** Why a raw stream shows nothing, in words. */
internal fun rawStatusText(status: RawStreamStatus, what: String) = when (status) {
    RawStreamStatus.NOT_SUPPORTED -> "This phone does not provide $what."
    RawStreamStatus.LOCATION_DISABLED -> "Location is switched off."
    else -> "Waiting for $what from the chip…"
}
