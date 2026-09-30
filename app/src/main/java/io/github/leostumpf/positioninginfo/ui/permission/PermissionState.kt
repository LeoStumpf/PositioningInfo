// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.permission

/** Why the app cannot show a speed yet, and the one action that fixes it. */
enum class PermissionState {
    /** Never asked, or asked and dismissed without a decision. */
    NEEDS_REQUEST,

    /** Denied once; the system will still show the dialog again. */
    DENIED,

    /** Only approximate location; the system can still offer the upgrade to precise. */
    APPROXIMATE_ONLY,

    /** Only approximate location, and the upgrade was declined: app settings only. */
    APPROXIMATE_NEEDS_SETTINGS,

    /** Denied permanently: app settings only. */
    NEEDS_SETTINGS,
}
