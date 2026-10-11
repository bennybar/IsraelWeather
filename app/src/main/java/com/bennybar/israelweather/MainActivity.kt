package com.bennybar.israelweather

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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.ui.EditCardsSheet
import com.bennybar.israelweather.ui.HomeScreen
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.UiController
import com.bennybar.israelweather.ui.places.SearchScreen
import com.bennybar.israelweather.ui.settings.SettingsScreen
import com.bennybar.israelweather.ui.theme.WeatherTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val ui = remember { UiController() }
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
}
