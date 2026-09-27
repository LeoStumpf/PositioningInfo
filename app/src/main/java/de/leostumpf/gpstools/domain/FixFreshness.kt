// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.domain

/**
 * How much a fix should still be trusted, derived purely from its age.
 *
 * A speedometer frozen on a stale number is worse than one that admits it has lost the
 * signal, so age drives a visible state rather than being silently ignored.
 */
enum class FixFreshness {
    /** Recent enough to display at full confidence. */
    FRESH,

    /** Getting old: still shown, but visibly dimmed. */
    STALE,

    /** Too old to mean anything; the reading is withdrawn. */
    EXPIRED;

    companion object {
        const val staleAfterMs: Long = 3_000L
        const val expiredAfterMs: Long = 10_000L

        fun ofAge(ageMs: Long): FixFreshness = when {
            ageMs >= expiredAfterMs -> EXPIRED
            ageMs >= staleAfterMs -> STALE
            else -> FRESH
        }
    }
}
