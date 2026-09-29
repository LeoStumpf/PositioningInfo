// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.domain

/**
 * Speed units offered by the speedometer. The GNSS chip always reports metres per
 * second, so every unit is expressed as a factor applied to that base value.
 */
enum class SpeedUnit(val symbol: String, private val factorFromMps: Double) {
    KMH("km/h", 3.6),
    MPH("mph", 2.236936),
    KNOTS("kn", 1.943844);

    fun fromMps(mps: Double): Double = mps * factorFromMps

    fun next(): SpeedUnit = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = KMH

        fun fromName(name: String?): SpeedUnit =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
