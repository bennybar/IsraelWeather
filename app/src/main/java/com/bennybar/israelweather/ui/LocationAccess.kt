package com.bennybar.israelweather.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.location.CurrentPlace
import com.bennybar.israelweather.widget.Widgets
import kotlinx.coroutines.launch

/** Entry points into the location-permission flow (see [rememberLocationAccess]). */
class LocationAccess(val request: () -> Unit, val requestBackground: () -> Unit)

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The location-permission flow: precise/approximate first (the user picks),
 * then, once, an explanation and the "Allow all the time" request that
 * widgets and background refresh need to follow the user. Android only
 * allows asking for background access after foreground access is granted.
 * When a permission was permanently denied, the request opens app settings.
 */
@Composable
fun rememberLocationAccess(): LocationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var explainBackground by remember { mutableStateOf(false) }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch {
            CurrentPlace.resolve()
            Widgets.updateAll(context)
        }
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            app.settings.setUseLocation(true)
            scope.launch { CurrentPlace.resolve() }
            if (!app.location.hasBackground() && !app.settings.backgroundAsked()) explainBackground = true
        } else if (activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false) {
            // "Don't ask again" (or denied twice): only app settings can grant it now.
            openAppSettings(context)
        }
    }

    if (explainBackground) {
        AlertDialog(
            onDismissRequest = { explainBackground = false },
            icon = { Icon(Icons.Rounded.MyLocation, null) },
            title = { Text(stringResource(R.string.bg_title)) },
            text = { Text(stringResource(R.string.bg_body)) },
            confirmButton = {
                Button(onClick = {
                    explainBackground = false
                    app.settings.setBackgroundAsked()
                    background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.bg_continue)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainBackground = false
                    app.settings.setBackgroundAsked()
                }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }

    return remember {
        LocationAccess(
            request = {
                app.settings.setUseLocation(true)
                if (app.location.hasPermission()) scope.launch { CurrentPlace.resolve() }
                else foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            requestBackground = {
                if (!app.location.hasPermission()) {
                    foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    explainBackground = true
                }
            },
        )
    }
}
