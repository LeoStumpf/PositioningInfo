// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.leostumpf.gpstools.ui.gnss.GnssScreen
import de.leostumpf.gpstools.ui.signal.SignalScreen
import de.leostumpf.gpstools.ui.speed.SpeedScreen
import de.leostumpf.gpstools.ui.theme.DimGrey

/** The tools, in swipe order. Adding a screen later means adding an entry here. */
private const val PAGE_SPEED = 0
private const val PAGE_GNSS = 1
private const val PAGE_SIGNAL = 2
private const val PAGE_COUNT = 3

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
    val pagerState = rememberPagerState(initialPage = PAGE_SPEED) { PAGE_COUNT }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_SPEED -> SpeedScreen(
                    state = speedState,
                    onCycleUnit = viewModel::cycleUnit,
                    onResetSession = viewModel::resetSession,
                    onShowAbout = onShowAbout,
                )

                PAGE_GNSS -> GnssScreen(state = gnssState)

                PAGE_SIGNAL -> SignalScreen(state = signalState)
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

/** Two dots, so the second screen is discoverable without spending a toolbar on it. */
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
