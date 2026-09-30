// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

import java.util.Locale

/**
 * A duration as people say it: tenths only below ten seconds, where they carry information
 * ("4.2 s"), then whole seconds, minutes and hours ("37 s", "1 min 20 s", "2 h 5 min").
 * One format for every duration in the app, so the same first fix never reads two ways.
 */
fun formatDuration(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val s = clamped / 1_000
    return when {
        clamped < 10_000 -> String.format(Locale.US, "%.1f s", clamped / 1_000.0)
        s < 60 -> "$s s"
        s < 3_600 -> if (s % 60 == 0L) "${s / 60} min" else "${s / 60} min ${s % 60} s"
        else -> if (s / 60 % 60 == 0L) "${s / 3_600} h" else "${s / 3_600} h ${s / 60 % 60} min"
    }
}

/** How long ago something happened: "just now", "37 s ago", "12 min ago", "2 h 5 min ago". */
fun formatAgo(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val s = clamped / 1_000
    return when {
        clamped < 2_000 -> "just now"
        s < 60 -> "$s s ago"
        s < 3_600 -> "${s / 60} min ago"
        else -> "${formatDuration(clamped / 60_000 * 60_000)} ago"
    }
}
