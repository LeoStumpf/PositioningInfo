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
    val s = clamped / MS_PER_S
    val min = s / S_PER_MIN
    return when {
        clamped < TENTHS_BELOW_MS -> String.format(Locale.US, "%.1f s", clamped / MS_PER_S.toDouble())
        s < S_PER_MIN -> "$s s"
        s < S_PER_H -> if (s % S_PER_MIN == 0L) "$min min" else "$min min ${s % S_PER_MIN} s"
        else -> if (min % MIN_PER_H == 0L) "${s / S_PER_H} h" else "${s / S_PER_H} h ${min % MIN_PER_H} min"
    }
}

/** How long ago something happened: "just now", "37 s ago", "12 min ago", "2 h 5 min ago". */
fun formatAgo(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val s = clamped / MS_PER_S
    val msPerMin = S_PER_MIN * MS_PER_S
    return when {
        clamped < JUST_NOW_MS -> "just now"
        s < S_PER_MIN -> "$s s ago"
        s < S_PER_H -> "${s / S_PER_MIN} min ago"
        else -> "${formatDuration(clamped / msPerMin * msPerMin)} ago"
    }
}

private const val MS_PER_S = 1_000L
private const val S_PER_MIN = 60L
private const val MIN_PER_H = 60L
private const val S_PER_H = S_PER_MIN * MIN_PER_H

/** Below this a tenth of a second still tells something; above it, it is noise. */
private const val TENTHS_BELOW_MS = 10_000L

/** Anything younger than this is "just now". */
private const val JUST_NOW_MS = 2_000L
