// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data.model

import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation

/**
 * The phone settings around positioning, read-only. Null wherever the phone does not let
 * an app read the setting.
 */
data class PhoneSettings(
    /** Precise location granted, rather than only approximate. */
    val preciseLocation: Boolean? = null,
    val locationOn: Boolean? = null,
    /** The network provider (Wi-Fi and cell positioning) is switched on. */
    val networkLocation: Boolean? = null,
    /** Wi-Fi scanning for location even while Wi-Fi is off. */
    val wifiScanning: Boolean? = null,
    /** Bluetooth scanning for location even while Bluetooth is off. */
    val bluetoothScanning: Boolean? = null,
    val autoTime: Boolean? = null,
    val autoTimeZone: Boolean? = null,
    val timeZone: String? = null,
    val powerSave: PowerSaveLocation = PowerSaveLocation.UNRESTRICTED,
)
