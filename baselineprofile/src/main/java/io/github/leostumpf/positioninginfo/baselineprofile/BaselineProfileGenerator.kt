// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a user does first: open the app and swipe through the pages. The code this runs is
 * what the profile tells Android to compile ahead of time.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Grants location, starts the app on its first page and swipes once to each of the others. */
    @Test
    fun startAndSwipeThroughThePages() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "io.github.leostumpf.positioninginfo"
        rule.collect(packageName = packageName) {
            // Location granted up front: otherwise the permission screen is all that runs.
            device.executeShellCommand("pm grant $packageName android.permission.ACCESS_FINE_LOCATION")
            device.executeShellCommand("pm grant $packageName android.permission.ACCESS_COARSE_LOCATION")
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("01 / $PAGES")), START_TIMEOUT_MS)
            // By screen position: the pages redraw twice a second, so a looked-up view is stale
            // by the time it would be swiped.
            val y = device.displayHeight / 2
            val from = (device.displayWidth * SWIPE_FROM).toInt()
            val to = (device.displayWidth * SWIPE_TO).toInt()
            repeat(PAGES.toInt() - 1) {
                device.swipe(from, y, to, y, SWIPE_STEPS)
                device.waitForIdle()
            }
        }
    }
}

/** The app's page count, as its indicator writes it ("01 / 08"). */
private const val PAGES = "08"
private const val START_TIMEOUT_MS = 5_000L

/** A swipe from near the right edge to near the left: one page on. */
private const val SWIPE_FROM = 0.85
private const val SWIPE_TO = 0.1
private const val SWIPE_STEPS = 20
