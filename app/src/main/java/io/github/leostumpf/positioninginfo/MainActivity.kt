// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leostumpf.positioninginfo.ui.PositioningInfoApp
import io.github.leostumpf.positioninginfo.ui.PositioningInfoViewModel
import io.github.leostumpf.positioninginfo.ui.permission.PermissionScreen
import io.github.leostumpf.positioninginfo.ui.permission.PermissionState
import io.github.leostumpf.positioninginfo.ui.theme.PositioningInfoTheme
/**
 * The app's only activity: asks for precise location, then shows the pages and ties tracking
 * to whether the app is on screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge is enforced from Android 15 at this targetSdk, so it is opted into on
        // every version for one consistent layout; the screens pad themselves for the system
        // bars. The bars are always styled dark because the app is always black — following
        // the system theme would put dark icons on a black background in light mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Back on the last screen only moves the app behind others, as Android 12 and newer
        // do anyway. Before that it finished the activity, and with it the session — which
        // silently ended background mode, although the user had only left the app. Handlers
        // added later by the screens take precedence over this one.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        }
        setContent {
            PositioningInfoTheme {
                PositioningInfoRoot()
            }
        }
    }
}

@Composable
private fun PositioningInfoRoot() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasFineLocation by remember { mutableStateOf(context.hasFineLocation()) }
    // Re-checked on every resume: the "Open app settings" route grants the permission
    // outside the app, and nothing else would notice when the user comes back.
    LifecycleResumeEffect(Unit) {
        hasFineLocation = context.hasFineLocation()
        onPauseOrDispose { }
    }
    var wasDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasFineLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!hasFineLocation) wasDenied = true
    }
    if (hasFineLocation) {
        SpeedRoute()
        return
    }
    // Approximate location cannot produce a speed or satellites, so it is not enough on its
    // own. Android 12+ offers an upgrade to precise when asked again, until the user has
    // declined that too; after that, only the app's settings can change it.
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val mayAskAgain = !wasDenied ||
        activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == true
    val state = when {
        hasCoarse && mayAskAgain -> PermissionState.APPROXIMATE_ONLY
        hasCoarse -> PermissionState.APPROXIMATE_NEEDS_SETTINGS
        !wasDenied -> PermissionState.NEEDS_REQUEST
        mayAskAgain -> PermissionState.DENIED
        else -> PermissionState.NEEDS_SETTINGS
    }
    PermissionScreen(
        state = state,
        onRequest = {
            launcher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

@Composable
private fun SpeedRoute(viewModel: PositioningInfoViewModel = viewModel()) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Tracking is bound to STARTED, so the receiver is released the moment the app is no
    // longer in front — unless the user has switched background mode on.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onUiStart()
                Lifecycle.Event.ON_STOP -> viewModel.onUiStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onUiStop()
        }
    }
    // The pages get the same activity-scoped ViewModel for themselves.
    PositioningInfoApp()
}
private fun android.content.Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
