// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leostumpf.positioninginfo.ui.about.AboutScreen
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageIndicator
import io.github.leostumpf.positioninginfo.ui.gnss.GnssScreen
import io.github.leostumpf.positioninginfo.ui.network.NetworkScreen
import io.github.leostumpf.positioninginfo.ui.position.PositionScreen
import io.github.leostumpf.positioninginfo.ui.receiver.ReceiverScreen
import io.github.leostumpf.positioninginfo.ui.signal.SignalScreen
import io.github.leostumpf.positioninginfo.ui.speed.SpeedScreen
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.trip.TripScreen

/**
 * Holds the app's pages in a horizontal pager, in the order of [Page].
 *
 * A pager rather than a navigation bar: it keeps the speedometer free of permanent chrome,
 * and the indicator names the neighbouring pages so the others stay discoverable.
 */
// The pages are this screen split up, not reusable components; see PageContent.
@Suppress("ViewModelForwarding")
@Composable
fun PositioningInfoApp(modifier: Modifier = Modifier, viewModel: PositioningInfoViewModel = viewModel()) {
    val positionState by viewModel.analysis.positionState.collectAsStateWithLifecycle()
    val tripState by viewModel.analysis.tripState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri -> uri?.let(viewModel.analysis.trip::export) }
    val pagerState = rememberPagerState(initialPage = Page.SPEED.ordinal) { Page.entries.size }

    // The screen stays awake where blanking would defeat the purpose: the speedometer and
    // trip pages (a car mount), and while a trip or the accuracy test is running — without
    // background mode, a screen that goes off ends the measurement. Elsewhere it times out
    // as usual.
    KeepScreenOn(
        Page.entries[pagerState.currentPage] in AWAKE_PAGES || tripState.recording || positionState.scatterRunning,
    )

    // The background runs under the system bars; the content stays clear of them and of
    // any display cutout, which matters in a landscape car mount.
    Box(modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            PageContent(
                Page.entries[index],
                viewModel,
                onExportTrip = { exportLauncher.launch(viewModel.analysis.trip.suggestedFileName()) },
            )
        }
        PageIndicator(
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).background(Palette.Background.copy(alpha = 0.85f)),
        )
    }
}

/** Pages on which the screen never times out. */
private val AWAKE_PAGES = setOf(Page.SPEED, Page.TRIP)

/** Holds the screen on while [keepAwake], and lets it time out again once left. */
@Composable
private fun KeepScreenOn(keepAwake: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * One page of the pager. Each page collects only its own state, so an update to one page's
 * figures does not recompose the others the pager keeps around.
 */
@Composable
private fun PageContent(page: Page, viewModel: PositioningInfoViewModel, onExportTrip: () -> Unit) {
    val analysis = viewModel.analysis
    val backgroundActive by viewModel.backgroundActive.collectAsStateWithLifecycle()
    val dataInventory by viewModel.dataInventory.collectAsStateWithLifecycle()
    when (page) {
        Page.SPEED -> SpeedScreen(
            state = viewModel.speed.state.collectAsStateWithLifecycle().value,
            onSetUnit = viewModel.speed::setUnit,
            onResetSession = viewModel.speed::reset,
            backgroundActive = backgroundActive,
            onSetBackground = viewModel::setBackgroundMode,
            dataInventory = dataInventory,
            onClearAllData = viewModel::clearAllData,
        )

        Page.TRIP -> TripScreen(
            state = analysis.tripState.collectAsStateWithLifecycle().value,
            onToggleRecording = analysis.trip::toggle,
            onExport = onExportTrip,
            onClear = analysis.trip::clear,
            backgroundActive = backgroundActive,
            onSetBackground = viewModel::setBackgroundMode,
        )

        Page.POSITION -> PositionScreen(
            state = analysis.positionState.collectAsStateWithLifecycle().value,
            onToggleScatter = analysis.accuracyTest::toggle,
            onResetScatter = analysis.accuracyTest::reset,
        )

        Page.GNSS -> GnssScreen(
            state = viewModel.gnssState.collectAsStateWithLifecycle().value,
            onColdStart = viewModel::coldStart,
            onFetchAssistance = viewModel::fetchAssistance,
            sky = viewModel.skyState.collectAsStateWithLifecycle().value,
            onToggleCompass = analysis.sky::toggleCompass,
            onToggleMap = analysis.sky::toggleMap,
            onToggleShowPaths = analysis.sky::toggleShowPaths,
            onClearPaths = viewModel::clearSkyPaths,
        )

        Page.SIGNAL -> SignalScreen(state = viewModel.signalState.collectAsStateWithLifecycle().value)

        Page.RECEIVER -> ReceiverScreen(state = analysis.receiverState.collectAsStateWithLifecycle().value)

        Page.NETWORK -> NetworkScreen(state = viewModel.networkState.collectAsStateWithLifecycle().value)

        Page.ABOUT -> AboutScreen(dataInventory = dataInventory, onClearAllData = viewModel::clearAllData)
    }
}
