// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.gnss

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.leostumpf.positioninginfo.data.model.PhoneSettings
import io.github.leostumpf.positioninginfo.domain.PowerSaveLocation
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.theme.Palette

/** What the user has switched on or off around positioning; all read-only. */
@Composable
internal fun PhoneSettingsRows(s: PhoneSettings) {
    fun onOff(v: Boolean?) = when (v) {
        true -> "on"
        false -> "off"
        null -> "not readable"
    }
    Column {
        ValueRow("Location", onOff(s.locationOn), valueColor = if (s.locationOn == false) Palette.Bad else null)
        ValueRow(
            "Location access",
            when (s.preciseLocation) {
                true -> "precise"
                false -> "approximate"
                null -> "—"
            },
            detail = "approximate access hides the GNSS receiver entirely".takeIf { s.preciseLocation == false },
            valueColor = if (s.preciseLocation == false) Palette.Degraded else null,
        )
        ValueRow(
            "Wi-Fi & cell positioning",
            onOff(s.networkLocation),
            detail = "the network provider; faster, rougher fixes",
        )
        ValueRow(
            "Wi-Fi scanning",
            onOff(s.wifiScanning),
            detail = "finds access points for positioning even with Wi-Fi off",
        )
        ValueRow(
            "Bluetooth scanning",
            onOff(s.bluetoothScanning),
            detail = "finds beacons for positioning even with Bluetooth off",
        )
        ValueRow(
            "Battery saver",
            when (s.powerSave) {
                PowerSaveLocation.UNRESTRICTED -> "no effect"
                PowerSaveLocation.GNSS_OFF_SCREEN_OFF -> "GNSS off with screen off"
                PowerSaveLocation.ALL_OFF_SCREEN_OFF -> "location off with screen off"
                PowerSaveLocation.FOREGROUND_ONLY -> "foreground apps only"
                PowerSaveLocation.THROTTLED_SCREEN_OFF -> "throttled with screen off"
            },
            valueColor = if (s.powerSave != PowerSaveLocation.UNRESTRICTED) Palette.Degraded else null,
        )
        ValueRow("Automatic time", onOff(s.autoTime), detail = "set from the network, not from GNSS")
        ValueRow("Automatic time zone", onOff(s.autoTimeZone), detail = s.timeZone?.let { "now $it" }, divider = false)
        Note("Change these in Android's settings; the app only reads them.", modifier = Modifier.padding(top = 6.dp))
    }
}
