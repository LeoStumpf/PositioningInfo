// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

/** The app's pages in swipe order: name, short name for the indicator, and glossary. */
enum class Page(val title: String, val shortName: String, val glossary: List<PrimerEntry>) {
    SPEED("Speed", "Speed", Glossary.speed),
    TRIP("Trip", "Trip", Glossary.trip),
    POSITION("Position", "Position", Glossary.position),
    GNSS("GNSS & sky", "GNSS", Glossary.gnss + Glossary.sky),
    SIGNAL("Signal & accuracy", "Signal", Glossary.signal),
    RECEIVER("Receiver internals", "Receiver", Glossary.receiver),
    NETWORK("Wi-Fi & mobile network", "Wi-Fi & cell", Glossary.network),
    ABOUT("About", "About", Glossary.about),
    ;

    /** "03 / 08". */
    val number: String get() = "%02d / %02d".format(ordinal + 1, entries.size)
}
