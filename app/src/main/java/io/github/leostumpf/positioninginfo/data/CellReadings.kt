// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.annotation.SuppressLint
import android.os.Build
import android.telephony.CellIdentity
import android.telephony.CellInfo
import android.telephony.CellSignalStrength
import androidx.annotation.ChecksSdkIntAtLeast
import io.github.leostumpf.positioninginfo.data.model.SignalMeasure

/*
 * Small helpers for reading modem values safely: "not known" markers turned into null, and
 * the calls that only exist on newer Android versions behind a version check.
 */

// UNAVAILABLE and UNAVAILABLE_LONG are compile-time constants (Integer and Long MAX_VALUE),
// inlined into the app, so reading them is safe on every version.
/** The value, or null when it is one of the modem's "not known" markers. */
@SuppressLint("InlinedApi")
internal fun Int.valid(): Int? = takeIf { it != CellInfo.UNAVAILABLE && it != Int.MIN_VALUE }

/** The value, or null when it is one of the modem's "not known" markers. */
@SuppressLint("InlinedApi")
internal fun Long.valid(): Long? = takeIf { it != CellInfo.UNAVAILABLE_LONG && it != Long.MAX_VALUE }

/** "MCC-MNC", when both codes are known. */
internal fun network(mcc: String?, mnc: String?): String? = if (mcc != null && mnc != null) "$mcc-$mnc" else null

/** "PCI 12": a figure with its name, or null when not reported. */
internal fun labelled(label: String, value: Number?): String? = value?.let { "$label $it" }

// CellInfo.getCellIdentity() and getCellSignalStrength() exist on the base class only from
// Android 11; below that, calling them throws NoSuchMethodError. So both are read from the
// typed subclass inside each branch, never from the base type. The typed identities only
// share the CellIdentity base class from Android 9, hence the cast behind the check.
internal fun operatorOf(id: Any): String? {
    if (!sdk(Build.VERSION_CODES.P)) return null
    return (id as CellIdentity).operatorAlphaLong?.toString()?.takeIf { it.isNotBlank() }
}

/** Android's 0–4 bars, or null when out of range. */
internal fun levelOf(s: CellSignalStrength): Int? = s.level.takeIf { it in SIGNAL_LEVELS }

/** Android's bars: none or unknown, poor, moderate, good, great. */
internal val SIGNAL_LEVELS =
    CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN..CellSignalStrength.SIGNAL_STRENGTH_GREAT

/** True on Android [version] or newer; annotated so lint's API check sees the guard. */
@ChecksSdkIntAtLeast(parameter = 0)
internal fun sdk(version: Int): Boolean = Build.VERSION.SDK_INT >= version

/** A quality figure, or null when the modem does not report it. */
internal fun measure(name: String, value: Int?, unit: String): SignalMeasure? =
    value?.let { SignalMeasure(name, it, unit) }

/** The frequency bands a cell identity lists, from Android 11; empty before. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R, lambda = 0)
internal fun bandsOf(read: () -> IntArray): List<Int> =
    if (sdk(Build.VERSION_CODES.R)) runCatching { read().toList() }.getOrDefault(emptyList()) else emptyList()

/** MCC-MNC of a cell identity, which Android exposes as strings from version 9. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P, lambda = 0)
internal fun networkOf(mcc: () -> String?, mnc: () -> String?): String? =
    if (sdk(Build.VERSION_CODES.P)) network(mcc(), mnc()) else null
