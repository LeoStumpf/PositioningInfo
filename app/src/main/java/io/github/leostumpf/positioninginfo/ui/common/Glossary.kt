// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

/** One term and a reminder of what it means. */
data class PrimerEntry(val term: String, val meaning: String)

/**
 * The terms each page relies on, in the order a reader meets them. Written as a reminder
 * for a technical reader rather than a tutorial: enough to read the numbers correctly.
 */
object Glossary {

    val gnss = listOf(
        PrimerEntry(
            "Fixed vs. ready",
            "Two different questions. The headline says what the receiver is doing now: " +
                "fixed (computing a position from 4+ satellites) or still searching. The " +
                "hot / warm / cold badge says how much orbital data it holds, i.e. how fast " +
                "the next start would be. A receiver can be fixed and hot at the same time.",
        ),
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
        PrimerEntry(
            "Constellation table",
            "Per system: VIS satellites listed, ALM with almanac, EPH with ephemeris, FIX " +
                "used in the current fix. Counted per satellite, not per signal.",
        ),
        PrimerEntry(
            "Satellite list",
            "One row per signal, strongest in-fix first: system and number, band (L1, L5, " +
                "…) when the chip reports it, C/N₀ with a bar, and the A/E flags.",
        ),
        PrimerEntry(
            "Cold start / Fetch A-GNSS",
            "Cold start deletes the stored orbits, position and time and restarts the " +
                "receiver, so the next time to first fix shows the worst case. Fetch asks " +
                "Android to download fresh assistance data — the app itself makes no " +
                "connection.",
        ),
    )

    val signal = listOf(
        PrimerEntry(
            "Reported accuracies",
            "Android reports a 68\u00A0% uncertainty for more than the position: height, speed and " +
                "direction of travel (only while moving). The fix interval is how often fixes " +
                "actually arrive — slower than requested means the receiver is struggling or saving power.",
        ),
        PrimerEntry(
            "DOP (dilution of precision)",
            "How much the satellite geometry magnifies range errors into position errors. " +
                "Error ≈ DOP × range error. Below 2 is excellent, above 5 noticeably poor. " +
                "HDOP is horizontal, VDOP vertical (always worse: all satellites are above " +
                "you), TDOP the receiver clock.",
        ),
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
            "Expected resolution",
            "The typical open-sky accuracy of the technique in use — single-constellation, " +
                "multi-constellation, SBAS-corrected, dual-frequency — as a yardstick for the " +
                "measured figure above it.",
        ),
    )

    val sky = listOf(
        PrimerEntry(
            "Compass mode",
            "Turns the plot with the phone, using the rotation sensor, so it matches the " +
                "real sky. The magnetometer points to magnetic north; the app adds the local " +
                "magnetic declination (from the World Magnetic Model built into Android) to " +
                "get true north.",
        ),
        PrimerEntry(
            "Signal map",
            "Average signal strength per 10°\u00A0×\u00A010° patch of sky, collected over time. " +
                "Patches that stay weak or empty while satellites cross them are blocked — " +
                "a map of your horizon.",
        ),
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
        PrimerEntry(
            "Projection (dashed)",
            "Where a satellite should be over the next 15\u00A0min, continued from its last " +
                "≤10\u00A0min of movement. Needs 2\u00A0min of history; satellites below the " +
                "horizon cannot be predicted offline.",
        ),
        PrimerEntry(
            "Setting soon",
            "Satellites whose projection reaches the horizon within 15\u00A0min.",
        ),
        PrimerEntry(
            "Events",
            "Satellites entering or leaving the receiver's list. Below 15° this is rising " +
                "or setting; higher up it means acquired or lost — usually something blocking " +
                "the view.",
        ),
        PrimerEntry(
            "Heading, declination, course",
            "Heading: where the top of the phone points (compass). Declination: the local " +
                "angle between magnetic and true north. Course: direction of travel from GNSS, " +
                "shown while moving.",
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
        PrimerEntry(
            "Cell identifiers",
            "MCC-MNC: country and operator (262 = Germany). TAC/LAC: tracking or location " +
                "area. CI/CID/NCI: the cell's identity. PCI, BSIC, PSC: the short physical code " +
                "that tells neighbouring cells apart on the air.",
        ),
        PrimerEntry(
            "Wi-Fi bands",
            "2.4\u00A0GHz reaches furthest; 5 and 6\u00A0GHz have shorter range, so hearing " +
                "them means the access point is close.",
        ),
    )

    val position = listOf(
        PrimerEntry(
            "WGS84",
            "The reference frame GNSS positions are expressed in. Coordinates below are all " +
                "the same point, in different notations.",
        ),
        PrimerEntry(
            "UTM / MGRS",
            "UTM cuts the Earth into 60 zones of 6° longitude and gives metres east and north " +
                "within the zone. MGRS is the military grid built on UTM: zone, 100\u00A0km square " +
                "letters, then metres.",
        ),
        PrimerEntry(
            "Plus Code / Maidenhead",
            "Short alphanumeric grid codes. A 10-digit Plus Code is a ~14\u00A0m square; the " +
                "6-character Maidenhead locator (used in amateur radio) a ~5\u00A0×\u00A02.5\u00A0km one.",
        ),
        PrimerEntry(
            "Ellipsoid, geoid, sea level",
            "GNSS measures height above the WGS84 ellipsoid, a smooth mathematical shape. Sea " +
                "level follows the geoid, which is up to ±100\u00A0m away from it (about +45\u00A0m in " +
                "Germany). Height above sea level = ellipsoid height − geoid height.",
        ),
        PrimerEntry(
            "Barometric altitude",
            "Air pressure falls about 1\u00A0hPa per 8\u00A0m of height, so the barometer resolves " +
                "changes of under a metre — far smoother than GNSS, which is 1.5–3× worse " +
                "vertically than horizontally. But weather shifts the pressure, so it is " +
                "calibrated against good GNSS heights, which yields the sea-level pressure.",
        ),
        PrimerEntry(
            "CEP50 / CEP95 / 2DRMS",
            "Measured accuracy of a stationary receiver: the radius around the mean position " +
                "holding 50\u00A0% / 95\u00A0% of the fixes. 2DRMS is twice the root-mean-square " +
                "distance, roughly a 95\u00A0% radius. Compare with the claimed ±, a 68\u00A0% radius.",
        ),
        PrimerEntry(
            "Derived sea-level pressure",
            "The pressure at sea level that makes the barometer agree with GNSS — " +
                "equivalent to the QNH pilots set. It changes with the weather.",
        ),
        PrimerEntry(
            "Vertical speed",
            "Rate of climb or descent from the barometer over the last few seconds.",
        ),
    )

    val trip = listOf(
        PrimerEntry(
            "Distance",
            "Summed from fix to fix, ignoring the jitter of a phone standing still and fixes " +
                "worse than ±30\u00A0m.",
        ),
        PrimerEntry(
            "Moving time",
            "Time spent above 0.5\u00A0m/s, so stops at traffic lights do not dilute the average.",
        ),
        PrimerEntry(
            "Ascent / descent",
            "Height gained and lost, counted only in steps of 3\u00A0m so noise does not add up. " +
                "Uses the barometer when there is one.",
        ),
        PrimerEntry(
            "GPX",
            "The standard open file format for tracks; readable by practically every map and " +
                "sports app. Export writes it to a file you choose.",
        ),
        PrimerEntry(
            "Max / average speed",
            "Highest recorded speed, and distance divided by moving time.",
        ),
        PrimerEntry(
            "Background mode",
            "Without it, recording stops as soon as you leave the app. With it, a " +
                "notification keeps the receiver running with the screen off or in other apps.",
        ),
    )

    val receiver = listOf(
        PrimerEntry(
            "Raw measurements",
            "What the chip measures per signal before computing a position: pseudorange, " +
                "Doppler, C/N₀, and its own clock. Android exposes them since version 7.",
        ),
        PrimerEntry(
            "AGC (automatic gain control)",
            "The receiver's input amplification. GNSS signals are below the noise floor, so " +
                "the gain normally sits steady. Extra power in the band — a jammer — makes the " +
                "receiver turn the gain down.",
        ),
        PrimerEntry(
            "Jamming / spoofing",
            "Jamming drowns the signals: AGC and C/N₀ fall together. Spoofing fakes them: " +
                "signals that are unnaturally even or stronger than a real sky can deliver, or " +
                "jumps in time. These are indicators, not proof.",
        ),
        PrimerEntry(
            "Multipath",
            "A signal arriving reflected as well as direct, which biases the range. The chip " +
                "flags signals where it detects this.",
        ),
        PrimerEntry(
            "Clock drift (ppm)",
            "How fast the phone's crystal oscillator runs off its nominal frequency, measured " +
                "against the satellites' atomic clocks. A few ppm is normal; it changes with " +
                "temperature.",
        ),
        PrimerEntry(
            "NMEA",
            "The text sentences GNSS receivers have output since the 1980s: GGA position, GSA " +
                "DOP and fix type, GSV satellites, GST error estimate, RMC summary.",
        ),
        PrimerEntry(
            "Navigation message",
            "The data each satellite broadcasts at 50\u00A0bit/s: its ephemeris, the almanac, " +
                "health flags, an ionosphere model and GPS−UTC offset including announced leap " +
                "seconds. Decoded here from the bits the chip passes on.",
        ),
        PrimerEntry(
            "GPS week",
            "GPS counts time in weeks since 6 January 1980, broadcast with only 10 bits, so it " +
                "rolls over every 1024 weeks (last in April 2019); the phone's date resolves it.",
        ),
        PrimerEntry(
            "Band rows",
            "Per frequency band: signals, AGC level and mean C/N₀, with the change against " +
                "the baseline learnt during the first minute in brackets (− a drop, + a rise).",
        ),
        PrimerEntry(
            "Leap seconds, discontinuities",
            "GPS time runs ahead of UTC by the leap seconds added since 1980 (18 so far). A " +
                "discontinuity is a reset of the chip's clock, which would break any " +
                "measurement across it.",
        ),
        PrimerEntry(
            "Absolute time, carrier phase",
            "Absolute: the chip knows GNSS time outright, not just relative to its start. " +
                "Carrier phase: signals tracked with centimetre-precise phase, the basis of " +
                "high-precision positioning; phones rarely keep it.",
        ),
        PrimerEntry(
            "Fix type, GGA quality",
            "From NMEA: 3D fix, 2D (height assumed) or none; quality 1 = plain GNSS, 2 = " +
                "SBAS-corrected, 4/5 = RTK.",
        ),
        PrimerEntry(
            "Error ellipse (GST)",
            "The chip's own 1σ estimate of the position error as an ellipse: half-axes and " +
                "orientation, plus latitude, longitude and height sigmas. Not every chip " +
                "reports it.",
        ),
        PrimerEntry(
            "Health, almanac, ionosphere",
            "Health: each satellite's own status flag, 0 = usable. Almanac pages: how much " +
                "of the 12.5-minute almanac has been decoded. Klobuchar α/β: the broadcast " +
                "model single-frequency receivers use to correct ionospheric delay. A0/A1: " +
                "GPS-to-UTC offset and its rate.",
        ),
        PrimerEntry(
            "Chipset, generation",
            "The GNSS chipset model and its hardware generation (year), as the platform " +
                "reports them. Not every phone does.",
        ),
        PrimerEntry(
            "Assistance services",
            "Data delivered over the network rather than from the satellites: orbits " +
                "(A-GNSS), time injection, and on some phones correction data.",
        ),
    )

    val speed = listOf(
        PrimerEntry(
            "Speed",
            "Measured by the receiver from the Doppler shift of the satellite signals, not " +
                "from position changes, so it reacts at once without jitter. Readings inside " +
                "the receiver's own speed uncertainty show as 0. km/h by default; the gear " +
                "button switches to mph or knots.",
        ),
        PrimerEntry(
            "Max / avg",
            "Highest and time-weighted average speed since the app was opened or RESET was " +
                "tapped. Stale readings are not counted.",
        ),
        PrimerEntry(
            "± under the unit",
            "The receiver's own 68\u00A0% uncertainty of the speed shown. Standing still, a " +
                "speed smaller than this is indistinguishable from zero.",
        ),
        PrimerEntry(
            "Simulated",
            "Shown instead of FIX when a mock-location app is supplying the position. Android " +
                "marks such positions; everything the app shows then describes the fake.",
        ),
        PrimerEntry(
            "Status line",
            "FIX (green): a fresh fix. STALE (amber): older than 3\u00A0s. NO FIX: older " +
                "than 10\u00A0s or none. Then the horizontal accuracy (a 68\u00A0% radius) and " +
                "satellites used in the fix / visible.",
        ),
        PrimerEntry(
            "Background mode",
            "Off by default: the receiver is released the moment you leave the app. " +
                "Switched on, it keeps running for trips and measurements, announced by a " +
                "notification with a Stop button.",
        ),
        PrimerEntry(
            "More tools",
            "Swipe left: trip, position, GNSS status, sky, signal, receiver internals and " +
                "network location. Each has a glossary like this one; the last page also " +
                "says what the app is, its licence and where the source lives.",
        ),
    )
}
