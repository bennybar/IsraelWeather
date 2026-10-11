package com.bennybar.israelweather.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Radar
import com.bennybar.israelweather.data.parseImsTime
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.hhmm
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadarTab() {
    val context = LocalContext.current
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val place = (settings.currentForecastId.takeIf { settings.useLocation } ?: settings.places.firstOrNull())
        .let { id -> app.repo.placeFor(id, places.value) }

    var radar by remember { mutableStateOf<Radar?>(null) }
    var failed by remember { mutableStateOf(false) }
    val frames = remember { mutableStateListOf<ImageBitmap?>() }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val visible = ui.tab == 1

    // Load (and every 5 minutes reload) the frame list while the tab is showing.
    LaunchedEffect(visible) {
        while (visible) {
            val r = runCatching { app.repo.radar() }.getOrNull()
            failed = r == null && radar == null
            if (r != null && r.frames.map { it.url } != radar?.frames?.map { it.url }) {
                radar = r
                frames.clear()
                frames.addAll(List(r.frames.size) { null })
                index = r.frames.size - 1
                // Newest first, so the latest picture appears right away.
                for (i in r.frames.indices.reversed()) frames[i] = loadFrame(context, r.frames[i].url)
            }
            delay(5 * 60_000L)
        }
    }
    // Animation loop: ~0.4 s per frame, a longer pause on the newest one.
    LaunchedEffect(playing, visible, radar) {
        val n = radar?.frames?.size ?: 0
        while (playing && visible && n > 1) {
            delay(if (index == n - 1) 1400L else 400L)
            index = (index + 1) % n
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.ims_radar), Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            radar?.frames?.getOrNull(index)?.let {
                Text(hhmm(parseImsTime(it.t)), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    detectTransformGestures { _, p, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 10f)
                        pan += p
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = 1f
                        pan = Offset.Zero
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            RadarMap(radar, frames.getOrNull(index), place, Modifier.fillMaxSize(), scale = scale, pan = pan)
            when {
                failed -> Text(stringResource(R.string.load_failed))
                radar == null -> LoadingIndicator(Modifier.size(64.dp))
            }
        }
        val n = radar?.frames?.size ?: 0
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 108.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = { playing = !playing }, Modifier.size(52.dp)) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play))
            }
            Spacer(Modifier.width(12.dp))
            Slider(
                value = index.toFloat(),
                onValueChange = {
                    playing = false
                    index = it.toInt()
                },
                valueRange = 0f..(n - 1).coerceAtLeast(1).toFloat(),
                steps = (n - 2).coerceAtLeast(0),
                enabled = n > 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
