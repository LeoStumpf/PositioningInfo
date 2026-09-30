// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

/** One line of a detail sheet: what it is, its value, and what the value means. */
data class DetailRow(val label: String, val value: String, val explanation: String? = null)
