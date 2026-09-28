// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import de.leostumpf.gpstools.ui.permission.PermissionScreen
import de.leostumpf.gpstools.ui.permission.PermissionState
import de.leostumpf.gpstools.ui.GpsToolsApp
import de.leostumpf.gpstools.ui.GpsToolsViewModel
import de.leostumpf.gpstools.ui.theme.GpsToolsTheme
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
        // A speedometer that blanks mid-journey is useless; the screen stays awake for as
        // long as the app is in front.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GpsToolsTheme {
                GpsToolsRoot()
            }
        }
    }
}
@Composable
private fun GpsToolsRoot() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var hasFineLocation by remember { mutableStateOf(context.hasFineLocation()) }
    // Re-checked on every resume: the "Open app settings" route grants the permission
    // outside the app, and nothing else would notice when the user comes back.
    LifecycleResumeEffect(Unit) {
        hasFineLocation = context.hasFineLocation()
        onPauseOrDispose { }
    }
    var wasDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasFineLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!hasFineLocation) wasDenied = true
    }
    if (hasFineLocation) {
        SpeedRoute()
        return
    }
    // Coarse-only counts as denied here: it cannot produce a speed, and the system will not
    // re-prompt for an upgrade to precise, so the only route left is app settings.
    val state = when {
        !wasDenied -> PermissionState.NEEDS_REQUEST
        activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ->
            PermissionState.DENIED
        else -> PermissionState.NEEDS_SETTINGS
    }
    PermissionScreen(
        state = state,
        onRequest = {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
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
private fun SpeedRoute() {
    val viewModel: GpsToolsViewModel = viewModel()
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
    GpsToolsApp(viewModel = viewModel)
}
private fun android.content.Context.hasFineLocation(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
