// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import io.github.leostumpf.positioninginfo.ui.common.Page
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The app as a user meets it, on a device or emulator with location granted: every page
 * opens, and the flows that change stored data or start background work behave.
 *
 * Run with ./gradlew connectedDebugAndroidTest. An emulator reports its own (fixed) GPS
 * position, which is enough for the trip to record points.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    private val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissions).around(compose)

    /** The page's own list; the pager around it scrolls too, but sideways. */
    private val verticalList = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Test
    fun everyPageOpens() {
        compose.onNodeWithText(Page.SPEED.number).assertExists()
        for (page in Page.entries.drop(1)) {
            compose.onRoot().performTouchInput { swipeLeft() }
            compose.waitUntilAtLeastOneExists(hasText(page.number), timeoutMillis = 5_000)
            compose.onNodeWithText(page.title).assertExists()
        }
    }

    @Test
    fun tripRecordsAndDeletes() {
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntilAtLeastOneExists(hasText(Page.TRIP.number), timeoutMillis = 5_000)

        compose.waitUntilAtLeastOneExists(hasText("Start recording").or(hasText("Resume")), timeoutMillis = 10_000)
        compose.onNode(hasText("Start recording").or(hasText("Resume"))).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Pause"), timeoutMillis = 5_000)
        // Let a few fixes arrive, then pause: a paused trip with points offers "Delete this
        // trip", further down the list.
        // Export needs two points, so its button coming on means the track is recording.
        compose.waitUntilAtLeastOneExists(hasText("Export GPX") and isEnabled(), timeoutMillis = 15_000)
        compose.onNodeWithText("Pause").performClick()
        compose.onAllNodes(verticalList).onFirst().performScrollToNode(hasText("Delete this trip"))
        compose.onNodeWithText("Delete this trip").performClick()
        compose.onNodeWithText("Delete the trip?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Trip deleted."), timeoutMillis = 5_000)
        compose.onAllNodes(verticalList).onFirst().performScrollToNode(hasText("Start recording"))
        compose.onNodeWithText("Start recording").assertExists()
    }

    @Test
    fun clearAllDataEmptiesTheInventory() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Clear all data").performScrollTo().performClick()
        compose.onNodeWithText("Clear all data?").assertExists()
        compose.onNodeWithText("Clear").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Recorded trip").performScrollTo()
        compose.waitUntilAtLeastOneExists(hasText("none"), timeoutMillis = 5_000)
    }

    @Test
    fun backgroundModeIsExplainedBeforeItStarts() {
        compose.onNodeWithText("Background").performClick()
        compose.onNodeWithText("Keep running in the background?").assertExists()
        compose.onNodeWithText("keeps using your location", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntilDoesNotExist(hasText("Keep running in the background?"), timeoutMillis = 5_000)
    }
}
