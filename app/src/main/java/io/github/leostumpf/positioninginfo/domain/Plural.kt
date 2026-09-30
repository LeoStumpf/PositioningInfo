// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.domain

/** "1 satellite", "6 satellites": a count with its noun in the right number. */
fun Int.counted(one: String, other: String = one + "s"): String = "$this ${if (this == 1) one else other}"
