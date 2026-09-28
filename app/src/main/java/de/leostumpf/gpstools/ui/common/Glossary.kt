// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.common

/** The terms each page relies on, in the order a reader meets them. */
object Glossary {

    val gnss = listOf(
        PrimerEntry(
            "Fix",
            "A computed position and time. Each satellite provides one range; the receiver " +
                "solves for x, y, z and its own clock error, so a 3D fix needs at least four " +
                "satellites.",
        ),
        PrimerEntry(
            "Visible / used",
            "Visible: satellites the receiver lists, including ones it only expects from " +
                "the almanac. Used: those that went into the current fix.",
        ),
        PrimerEntry(
            "Almanac (A)",
            "Coarse orbits of the whole constellation, broadcast by every satellite and " +
                "valid for weeks. Tells the receiver which satellites should be overhead. " +
                "Receiving a full GPS almanac takes 12.5\u00A0min at 50\u00A0bit/s.",
        ),
        PrimerEntry(
            "Ephemeris (E)",
            "Precise orbit and clock parameters of one satellite, repeated by that " +
                "satellite every 30\u00A0s and valid for about 4\u00A0h. A satellite cannot be used " +
                "in a fix without it.",
        ),
        PrimerEntry(
            "Hot / warm / cold start",
            "What the receiver holds when it starts: valid ephemeris (hot, seconds), only " +
                "almanac plus rough position and time (warm, ~30\u00A0s), or nothing (cold, " +
                "minutes).",
        ),
        PrimerEntry(
            "Time to first fix (TTFF)",
            "From switching the receiver on to its first position. The practical measure " +
                "of hot, warm and cold.",
        ),
        PrimerEntry(
            "Phone clock",
            "Phone time minus GNSS time. GNSS time comes from the satellites' atomic " +
                "clocks, so this shows how far the phone's own clock has drifted.",
        ),
        PrimerEntry(
            "A-GNSS",
            "Assisted GNSS: almanac, predicted orbits and time downloaded over the " +
                "internet (SUPL, PSDS/XTRA, NTP), turning a cold start into a hot one.",
        ),
        PrimerEntry(
            "Constellations",
            "GPS (US), GLONASS (RU), Galileo (EU) and BeiDou (CN) are global; QZSS (JP) and " +
                "NavIC (IN) are regional; SBAS satellites broadcast corrections, see the " +
                "next page.",
        ),
        PrimerEntry(
            "C/N₀ (dB-Hz)",
            "Carrier-to-noise density: signal strength against the noise floor, the " +
                "number in the satellite list. Open sky gives 35–50; below about 20 a " +
                "signal is barely usable.",
        ),
    )

    val signal = listOf(
        PrimerEntry(
            "Horizontal accuracy",
            "The receiver's own estimate: a circle with 68\u00A0% confidence, so about two " +
                "fixes in three lie within it. It is an estimate, not a measurement against " +
                "the truth.",
        ),
        PrimerEntry(
            "Error sources",
            "Ionospheric delay (metres, the largest), tropospheric delay, multipath " +
                "(reflections off buildings arriving late), orbit and clock errors, and " +
                "poor geometry when the satellites are bunched together (high DOP).",
        ),
        PrimerEntry(
            "Frequency bands",
            "L1/E1/B1C at 1575\u00A0MHz is the classic civil signal; L5/E5a/B2a at 1176\u00A0MHz is " +
                "newer and more robust against multipath. The ionospheric delay depends on " +
                "frequency, so two bands let the receiver measure and remove it.",
        ),
        PrimerEntry(
            "SBAS",
            "Satellite-based augmentation: geostationary satellites broadcasting orbit, " +
                "clock and ionosphere corrections plus integrity warnings for one region — " +
                "EGNOS in Europe, WAAS in North America, MSAS, GAGAN.",
        ),
        PrimerEntry(
            "Assistance services",
            "Data delivered over the network rather than from the satellites: orbits " +
                "(A-GNSS), time injection, and on some phones correction data.",
        ),
    )

    val sky = listOf(
        PrimerEntry(
            "Sky plot",
            "Looking straight up with north at the top: the centre is overhead (90°), the " +
                "rings are 60° and 30° elevation, the outer circle is the horizon (0°). The " +
                "plot does not turn with the phone.",
        ),
        PrimerEntry(
            "Azimuth / elevation",
            "Azimuth: compass bearing to the satellite, clockwise from north. Elevation: " +
                "angle above the horizon.",
        ),
        PrimerEntry(
            "Markers",
            "Filled: used in the fix. Ring: signal received. Faint ring: position known " +
                "only from the almanac, no signal heard.",
        ),
        PrimerEntry(
            "Why they move",
            "GPS, GLONASS, Galileo and most BeiDou satellites orbit at 19,000–23,000\u00A0km " +
                "and take 11–14\u00A0h per orbit. A pass lasts a few hours, and a satellite " +
                "crosses the sky at roughly half a degree per minute.",
        ),
        PrimerEntry(
            "Satellites that do not move",
            "SBAS and some BeiDou satellites are geostationary, 36,000\u00A0km above the " +
                "equator. From Europe they sit fixed, low in the south.",
        ),
        PrimerEntry(
            "Low elevation",
            "Signals from near the horizon cross more atmosphere and are more easily " +
                "blocked or reflected, so they are weaker and receivers often ignore " +
                "satellites below 5–10°.",
        ),
    )

    val network = listOf(
        PrimerEntry(
            "Network location",
            "A position without satellites: the phone reports the Wi-Fi access points and " +
                "cell towers it hears, and a database of their known locations (Google's, " +
                "on this phone) returns an estimate. Fast, works indoors, costs little " +
                "battery.",
        ),
        PrimerEntry(
            "Wi-Fi positioning",
            "Access points have short range and are densely mapped, so in built-up areas " +
                "this gives roughly 10–50\u00A0m. Uses signal strengths, and on newer phones " +
                "round-trip time (Wi-Fi RTT) to reach a few metres.",
        ),
        PrimerEntry(
            "Cell positioning",
            "Based on the known positions of the cells in range: a few hundred metres in " +
                "cities, several kilometres in the countryside, where cells are large.",
        ),
        PrimerEntry(
            "Claimed vs. actual accuracy",
            "The ± figure is the provider's own 68\u00A0% estimate. With a good GNSS fix as " +
                "the reference the real error can be measured directly — the section below.",
        ),
        PrimerEntry(
            "Serving / neighbour cell",
            "Serving: the cell the phone is attached to. Neighbours: others it measures for " +
                "handover; usually reported with less detail.",
        ),
        PrimerEntry(
            "dBm",
            "Received power. LTE/5G: above −80 is excellent, below −110 is the cell edge. " +
                "Wi-Fi: above −60 is strong, below −85 barely usable.",
        ),
        PrimerEntry(
            "Timing advance",
            "How early the phone must transmit so its signal reaches the tower on time: the " +
                "round trip, in steps of about 78\u00A0m in LTE (553\u00A0m in GSM). It gives " +
                "the distance to the serving tower.",
        ),
        PrimerEntry(
            "Network-side positioning",
            "The operator can also locate the phone itself (cell ID, timing measurements, " +
                "LTE OTDOA, 5G positioning), for example for emergency calls. That position " +
                "is not visible to apps; emergency calls in Europe additionally send the " +
                "phone's own GNSS/Wi-Fi fix (AML).",
        ),
    )
}
