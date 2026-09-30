// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

// --- Formatting ------------------------------------------------------------------------

const val DASH = "—"

fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)
fun Float.fmt(decimals: Int): String = toDouble().fmt(decimals)

/** A count with a narrow space between thousands, "12 345", the same on every phone. */
fun Long.grouped(): String = String.format(Locale.US, "%,d", this).replace(',', '\u202F')
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
    kotlin.math.abs(value) >= 1_000 -> "${(value / 1_000).fmt(2)} km"
    else -> "${value.fmt(decimals)} m"
}

/** "1:02:03" or "4:05". */
fun duration(ms: Long): String {
    val s = ms / 1_000
    return if (s >= 3_600) String.format(Locale.US, "%d:%02d:%02d", s / 3_600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
