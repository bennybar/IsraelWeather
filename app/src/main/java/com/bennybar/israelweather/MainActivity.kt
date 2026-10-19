package com.bennybar.israelweather

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.ui.EditCardsSheet
import com.bennybar.israelweather.ui.HomeScreen
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.UiController
import com.bennybar.israelweather.ui.places.SearchScreen
import com.bennybar.israelweather.ui.settings.SettingsScreen
import com.bennybar.israelweather.ui.theme.WeatherTheme
import kotlinx.coroutines.launch
import com.bennybar.israelweather.widget.PaletteWorker
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    /** A place a widget tap asked to show; handed to the forecast pager once composed. */
    private var openPlace by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openPlace = placeFrom(intent)
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val ui = remember { UiController() }
            LaunchedEffect(openPlace) {
                val id = openPlace ?: return@LaunchedEffect
                ui.overlay = Overlay.NONE
                ui.tab = 0
                ui.focusPlace = id
                openPlace = null
            }
            WeatherTheme(settings.theme, settings.accent) {
                // Surface sets the default content colour (onSurface) for every Text below.
                CompositionLocalProvider(LocalUi provides ui) { Surface(color = MaterialTheme.colorScheme.surface) {
                    AnimatedContent(
                        targetState = ui.overlay,
                        transitionSpec = { (fadeIn() + slideInVertically { it / 12 }) togetherWith fadeOut() },
                        label = "overlay",
                    ) { overlay ->
                        when (overlay) {
                            Overlay.NONE -> HomeScreen()
                            Overlay.SETTINGS -> SettingsScreen()
                            Overlay.SEARCH -> SearchScreen()
                        }
                    }
                    BackHandler(ui.overlay != Overlay.NONE) { ui.overlay = Overlay.NONE }
                    if (ui.editCards) EditCardsSheet(onDismiss = { ui.editCards = false })
                } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Wallpaper-colored widgets catch up with a palette change the home screen didn't pick up.
        app.scope.launch { PaletteWorker.redrawWallpaperWidgets(applicationContext) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        placeFrom(intent)?.let { openPlace = it }
    }

    private fun placeFrom(intent: Intent?): Int? =
        intent?.takeIf { it.hasExtra(EXTRA_PLACE) }?.getIntExtra(EXTRA_PLACE, 0)

    companion object {
        const val EXTRA_PLACE = "place"
    }
}
