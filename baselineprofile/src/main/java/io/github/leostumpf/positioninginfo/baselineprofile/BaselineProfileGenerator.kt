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
            device.wait(Until.hasObject(By.text("01 / 08")), 5_000)
            // By screen position: the pages redraw twice a second, so a looked-up view is stale
            // by the time it would be swiped.
            val y = device.displayHeight / 2
            repeat(7) {
                device.swipe((device.displayWidth * 0.85).toInt(), y, (device.displayWidth * 0.1).toInt(), y, 20)
                device.waitForIdle()
            }
        }
    }
}
