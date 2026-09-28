// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.leostumpf.gpstools.ui.gnss.GnssScreen
import de.leostumpf.gpstools.ui.network.NetworkScreen
import de.leostumpf.gpstools.ui.position.PositionScreen
import de.leostumpf.gpstools.ui.receiver.ReceiverScreen
import de.leostumpf.gpstools.ui.signal.SignalScreen
import de.leostumpf.gpstools.ui.sky.SkyScreen
import de.leostumpf.gpstools.ui.speed.SpeedScreen
import de.leostumpf.gpstools.ui.trip.TripScreen
import de.leostumpf.gpstools.ui.theme.DimGrey

/** The tools, in swipe order. Adding a screen later means adding an entry here. */
private const val PAGE_SPEED = 0
private const val PAGE_TRIP = 1
private const val PAGE_POSITION = 2
private const val PAGE_GNSS = 3
private const val PAGE_SKY = 4
private const val PAGE_SIGNAL = 5
private const val PAGE_RECEIVER = 6
private const val PAGE_NETWORK = 7
private const val PAGE_COUNT = 8

/**
 * Holds the app's screens in a horizontal pager.
 *
 * A pager rather than a navigation bar: it keeps the speedometer free of permanent chrome,
 * costs one gesture to reach the GNSS detail, and is the shape this app grows into as more
 * tools are added.
 */
@Composable
fun GpsToolsApp(
    viewModel: GpsToolsViewModel,
    onShowAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedState by viewModel.speedState.collectAsStateWithLifecycle()
    val gnssState by viewModel.gnssState.collectAsStateWithLifecycle()
    val signalState by viewModel.signalState.collectAsStateWithLifecycle()
    val skyState by viewModel.skyState.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val positionState by viewModel.analysis.positionState.collectAsStateWithLifecycle()
    val tripState by viewModel.analysis.tripState.collectAsStateWithLifecycle()
    val receiverState by viewModel.analysis.receiverState.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml"),
    ) { uri -> uri?.let(viewModel.analysis::exportTrip) }

    // A recording only runs while the app is in front, so the screen stays on meanwhile.
    val view = LocalView.current
    DisposableEffect(tripState.recording) {
        view.keepScreenOn = tripState.recording
        onDispose { view.keepScreenOn = false }
    }
    val pagerState = rememberPagerState(initialPage = PAGE_SPEED) { PAGE_COUNT }

    // The background runs under the system bars; the content stays clear of them and of
    // any display cutout, which matters in a landscape car mount.
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_SPEED -> SpeedScreen(
                    state = speedState,
                    onCycleUnit = viewModel::cycleUnit,
                    onResetSession = viewModel::resetSession,
                    onShowAbout = onShowAbout,
                )

                PAGE_GNSS -> GnssScreen(
                    state = gnssState,
                    onColdStart = viewModel::coldStart,
                    onFetchAssistance = viewModel::fetchAssistance,
                )

                PAGE_SIGNAL -> SignalScreen(state = signalState)

                PAGE_TRIP -> TripScreen(
                    state = tripState,
                    onToggleRecording = viewModel.analysis::toggleTrip,
                    onExport = { exportLauncher.launch(viewModel.analysis.suggestedTripFileName()) },
                    onClear = viewModel.analysis::clearTrip,
                )

                PAGE_POSITION -> PositionScreen(
                    state = positionState,
                    onToggleScatter = viewModel.analysis::toggleScatter,
                    onResetScatter = viewModel.analysis::resetScatter,
                )

                PAGE_SKY -> SkyScreen(
                    state = skyState,
                    onToggleCompass = viewModel.analysis::toggleCompass,
                    onToggleMap = viewModel.analysis::toggleMap,
                )

                PAGE_RECEIVER -> ReceiverScreen(state = receiverState)

                PAGE_NETWORK -> NetworkScreen(state = networkState)
            }
        }

        PageIndicator(
            current = pagerState.currentPage,
            count = PAGE_COUNT,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

/** One dot per page, so the other screens are discoverable without spending a toolbar on it. */
@Composable
private fun PageIndicator(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (index == current) 7.dp else 5.dp)
                    .background(
                        color = if (index == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            DimGrey
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}
