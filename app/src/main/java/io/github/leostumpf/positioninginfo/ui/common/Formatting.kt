// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Locale

// --- Formatting ------------------------------------------------------------------------

const val DASH = "—"

/** The number with a fixed count of decimals and a point, whatever the phone's locale. */
fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)

/** The number with a fixed count of decimals and a point, whatever the phone's locale. */
fun Float.fmt(decimals: Int): String = toDouble().fmt(decimals)

/** A count with a narrow space between thousands, "12 345", the same on every phone. */
fun Long.grouped(): String = String.format(Locale.US, "%,d", this).replace(',', '\u202F')

/** A count with a narrow space between thousands; see [Long.grouped]. */
fun Int.grouped(): String = toLong().grouped()

/**
 * Date and time for a log line: English like the rest of the app, but on the phone's 12- or
 * 24-hour clock.
 */
@Composable
fun rememberLogTimeFormat(): SimpleDateFormat {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { SimpleDateFormat(if (is24Hour) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a", Locale.US) }
}

/** "12.3 m", or "1.2 km" beyond a kilometre. */
fun metres(value: Double?, decimals: Int = 1): String = when {
    value == null -> DASH
    kotlin.math.abs(value) >= M_PER_KM -> "${(value / M_PER_KM).fmt(2)} km"
    else -> "${value.fmt(decimals)} m"
}

/** "1:02:03" or "4:05": a stopwatch. */
fun duration(ms: Long): String {
    val s = ms / MS_PER_S
    val min = s / S_PER_MIN
    return if (s >= S_PER_H) {
        String.format(Locale.US, "%d:%02d:%02d", s / S_PER_H, min % S_PER_MIN, s % S_PER_MIN)
    } else {
        String.format(Locale.US, "%d:%02d", min, s % S_PER_MIN)
    }
}

private const val M_PER_KM = 1_000.0
private const val MS_PER_S = 1_000L
private const val S_PER_MIN = 60L
private const val S_PER_H = 3_600L
