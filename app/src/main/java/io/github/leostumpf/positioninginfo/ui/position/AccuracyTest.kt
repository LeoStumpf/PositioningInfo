// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.position

import io.github.leostumpf.positioninginfo.domain.PositionScatter
import io.github.leostumpf.positioninginfo.domain.ScatterStats

/**
 * The accuracy test on the position page: while running, every fix is added to a scatter
 * whose spread shows how much the position really wanders when standing still.
 */
class AccuracyTest internal constructor(private val onChanged: () -> Unit) {
    private var scatter = PositionScatter()

    /** Built from [scatter] only when it changes, not on every tick. */
    private var stats: ScatterStats? = null
    private var statsFor: PositionScatter? = null

    var running = false
        private set

    /** Starts or pauses collecting; the samples so far are kept. */
    fun toggle() {
        running = !running
        onChanged()
    }

    /** Stops the test and discards its samples. */
    fun reset() {
        scatter = PositionScatter()
        running = false
        onChanged()
    }

    /** Adds one fix, if the test is running. */
    internal fun add(sample: PositionScatter.Sample) {
        if (running) scatter = scatter.add(sample)
    }

    /** Spread of the samples so far; cached, since it walks all of them. */
    internal fun stats(): ScatterStats? {
        if (scatter !== statsFor) {
            stats = scatter.stats()
            statsFor = scatter
        }
        return stats
    }
}
