package com.bennybar.israelweather.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.ui.settings.WidgetOptions
import com.bennybar.israelweather.ui.theme.WeatherTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A widget's own settings screen: opened from the widget's long-press
 * "Widget settings" (reconfigurable, optional on first placement).
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val done = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Settings apply as they change, so leaving any way keeps the widget.
        setResult(RESULT_OK, done)
        enableEdgeToEdge()
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            val kind = Widgets.placed(this).firstOrNull { it.second == id }?.first
            WeatherTheme(settings.theme, settings.accent) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.systemBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.widget_settings), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
                        ) {
                            WidgetOptions(id, kind?.let { stringResource(it.title) })
                        }
                        Button(onClick = { finish() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Some launchers (Samsung) ignore widget updates while this screen covers them and
        // show the old picture on return: redraw again once the home screen is back.
        if (isFinishing) {
            val context = applicationContext
            app.scope.launch {
                delay(1_500)
                Widgets.updateAll(context)
            }
        }
    }
}
