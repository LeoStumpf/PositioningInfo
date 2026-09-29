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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageIndicator
import io.github.leostumpf.positioninginfo.ui.gnss.GnssScreen
import io.github.leostumpf.positioninginfo.ui.network.NetworkScreen
import io.github.leostumpf.positioninginfo.ui.position.PositionScreen
import io.github.leostumpf.positioninginfo.ui.receiver.ReceiverScreen
import io.github.leostumpf.positioninginfo.ui.signal.SignalScreen
import io.github.leostumpf.positioninginfo.ui.sky.SkyScreen
import io.github.leostumpf.positioninginfo.ui.speed.SpeedScreen
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.trip.TripScreen

/**
 * Holds the app's pages in a horizontal pager, in the order of [Page].
 *
 * A pager rather than a navigation bar: it keeps the speedometer free of permanent chrome,
 * and the indicator names the neighbouring pages so the others stay discoverable.
 */
@Composable
fun PositioningInfoApp(viewModel: PositioningInfoViewModel, modifier: Modifier = Modifier) {
    val speedState by viewModel.speedState.collectAsStateWithLifecycle()
    val gnssState by viewModel.gnssState.collectAsStateWithLifecycle()
    val signalState by viewModel.signalState.collectAsStateWithLifecycle()
    val skyState by viewModel.skyState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val positionState by viewModel.analysis.positionState.collectAsStateWithLifecycle()
    val tripState by viewModel.analysis.tripState.collectAsStateWithLifecycle()
    val receiverState by viewModel.analysis.receiverState.collectAsStateWithLifecycle()
    val backgroundActive by viewModel.backgroundActive.collectAsStateWithLifecycle()
    val dataInventory by viewModel.dataInventory.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri -> uri?.let(viewModel.analysis::exportTrip) }

    val pagerState = rememberPagerState(initialPage = Page.SPEED.ordinal) { Page.entries.size }

    // The background runs under the system bars; the content stays clear of them and of
    // any display cutout, which matters in a landscape car mount.
    Box(modifier.fillMaxSize().background(Palette.Background).safeDrawingPadding()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            when (Page.entries[index]) {
                Page.SPEED -> SpeedScreen(
                    state = speedState,
                    onSetUnit = viewModel::setUnit,
                    onResetSession = viewModel::resetSession,
                    backgroundActive = backgroundActive,
                    onSetBackground = viewModel::setBackgroundMode,
                    dataInventory = dataInventory,
                    onClearAllData = viewModel::clearAllData,
                )

                Page.TRIP -> TripScreen(
                    state = tripState,
                    onToggleRecording = viewModel.analysis::toggleTrip,
                    onExport = { exportLauncher.launch(viewModel.analysis.suggestedTripFileName()) },
                    onClear = viewModel.analysis::clearTrip,
                    backgroundActive = backgroundActive,
                    onSetBackground = viewModel::setBackgroundMode,
                )

                Page.POSITION -> PositionScreen(
                    state = positionState,
                    onToggleScatter = viewModel.analysis::toggleScatter,
                    onResetScatter = viewModel.analysis::resetScatter,
                )

                Page.GNSS -> GnssScreen(
                    state = gnssState,
                    onColdStart = viewModel::coldStart,
                    onFetchAssistance = viewModel::fetchAssistance,
                )

                Page.SKY -> SkyScreen(
                    state = skyState,
                    onToggleCompass = viewModel.analysis::toggleCompass,
                    onToggleMap = viewModel.analysis::toggleMap,
                )

                Page.SIGNAL -> SignalScreen(state = signalState)
                Page.RECEIVER -> ReceiverScreen(state = receiverState)
                Page.NETWORK -> NetworkScreen(
                    state = networkState,
                    dataInventory = dataInventory,
                    onClearAllData = viewModel::clearAllData,
                )
            }
        }

        PageIndicator(
            current = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).background(Palette.Background.copy(alpha = 0.85f)),
        )
    }
}
