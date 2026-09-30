// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.domain.GpsNavState
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs

/* The GPS navigation messages: health, week, leap seconds, ionosphere and UTC parameters. */

/** What the satellites broadcast, decoded from the navigation messages the chip passes on. */
internal fun LazyListScope.navigationSection(state: ReceiverUiState) {
    section("Navigation messages")
    if (state.navFrames.isEmpty()) {
        item {
            Notice(
                if (state.navStatus == RawStreamStatus.UNKNOWN && state.navSilentMs > NAV_PATIENCE_MS) {
                    "Nothing received after ${state.navSilentMs / MS_PER_S} s. This chip doesn't pass the " +
                        "satellites' broadcast messages on to Android — many phone chips don't."
                } else {
                    rawStatusText(state.navStatus, "navigation messages")
                },
            )
        }
    } else {
        val gps = state.gps
        item {
            ValueRow(
                "Frames received",
                state.navFrames.values.sum().toString(),
                detail = state.navFrames.entries.sortedByDescending { it.value }.joinToString(
                    " · ",
                ) { "${it.key} ${it.value}" },
            )
        }
        item {
            ValueRow(
                "GPS subframes decoded",
                gps.subframesDecoded.toString(),
                detail = if (gps.subframesRejected > 0) "${gps.subframesRejected} failed parity" else null,
            )
        }
        item {
            ValueRow(
                "GPS week",
                gps.weekNumber?.let { wn ->
                    fullWeek(wn, state.currentGpsWeek)?.let { "$it" } ?: "$wn mod 1024"
                } ?: DASH,
                detail = gps.weekNumber?.let { "broadcast as $it (10 bits)" },
            )
        }
        item {
            val unhealthy = gps.health.filterValues { it != 0 }.keys.sorted()
            ValueRow(
                "Satellite health",
                when {
                    gps.health.isEmpty() -> DASH
                    unhealthy.isEmpty() -> "all ${gps.health.size} healthy"
                    else -> unhealthy.joinToString { "G%02d".format(Locale.US, it) }
                },
                valueColor = if (unhealthy.isNotEmpty()) Palette.Degraded else null,
            )
        }
        item { ValueRow("Almanac pages", "${gps.almanacSvids.size} of 32") }
        page18Items(gps)
    }
}

/** Subframe 4 page 18: the leap-second schedule, GPS−UTC offset and ionosphere model. */
private fun LazyListScope.page18Items(gps: GpsNavState) {
    gps.utc?.let { utc ->
        item {
            ValueRow(
                "Leap seconds broadcast",
                "${utc.deltaTls} s",
                detail = if (utc.leapSecondPending) {
                    "change to ${utc.deltaTlsf} s announced (week ${utc.wnLsf} mod 256, day ${utc.dn})"
                } else {
                    "none announced"
                },
                valueColor = if (utc.leapSecondPending) Palette.Degraded else null,
            )
        }
        item {
            ValueRow(
                "GPS − UTC offset",
                "A0 ${(utc.a0 * NS_PER_S).fmt(decimals = 2)} ns",
                detail = "A1 ${(utc.a1 * FS_PER_S).fmt(decimals = 3)} fs/s",
            )
        }
    }
    gps.ionosphere?.let { k ->
        item {
            ValueRow(
                "Ionosphere model",
                "received",
                detail = "α ${k.alpha.scientific()} · β ${k.beta.scientific()}",
                divider = false,
            )
        }
    }
}

private const val NAV_PATIENCE_MS = 90_000L

/** Resolves the 10-bit broadcast week against the phone's date: the candidate nearest to it. */
internal fun fullWeek(broadcast: Int, current: Int?): Int? {
    if (current == null) return null
    val base = current - current % WEEK_ROLLOVER + broadcast
    return listOf(base - WEEK_ROLLOVER, base, base + WEEK_ROLLOVER).minBy { abs(it - current) }
}

/** The broadcast week has 10 bits, so it rolls over every 1024 weeks. */
private const val WEEK_ROLLOVER = 1024

/** The model's coefficients in scientific notation: "1.21e-08 1.49e-08 …". */
private fun List<Double>.scientific(): String = joinToString(" ") { "%.2e".format(Locale.US, it) }

private const val MS_PER_S = 1_000L

/** A0 is broadcast in seconds, A1 in seconds per second; shown in ns and fs/s. */
private const val NS_PER_S = 1e9
private const val FS_PER_S = 1e15
