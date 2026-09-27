// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.data.model

/**
 * What auxiliary services the GNSS hardware and driver say they support.
 *
 * These are the assistance mechanisms that shorten time-to-first-fix and improve accuracy:
 * orbital data and corrections delivered by some route other than the satellites' own slow
 * broadcast.
 *
 * [reported] is false on devices below Android 11, where the platform has no API to ask.
 * Everything else is then meaningless and the screen says so rather than showing a column
 * of "no" that would misrepresent a capable receiver.
 */
data class AssistanceCapabilities(
    val reported: Boolean = false,
    /**
     * True only from Android 14, where the assistance and correction queries became public
     * API. On Android 11-13 the capabilities object exists but answers none of them, so
     * the screen must say "not available on this Android version" rather than "no".
     */
    val assistanceReported: Boolean = false,
    /** A-GNSS, mobile-station-assisted: the network computes the position from raw measurements. */
    val assistedMsa: Boolean = false,
    /** A-GNSS, mobile-station-based: the network supplies orbital data, the phone computes the fix. */
    val assistedMsb: Boolean = false,
    /** The network can inject accurate time, removing a slow step from a cold start. */
    val onDemandTime: Boolean = false,
    /** Raw pseudorange measurements are exposed to apps. */
    val rawMeasurements: Boolean = false,
    /** The raw satellite navigation message is exposed to apps. */
    val navigationMessages: Boolean = false,
    /** Carrier-phase and path corrections, the basis of centimetre-level positioning. */
    val measurementCorrections: Boolean = false,
    /** Accumulated delta range, i.e. usable carrier-phase tracking. */
    val carrierPhase: Boolean = false,
    val hardwareModel: String? = null,
    val hardwareYear: Int? = null,
)
