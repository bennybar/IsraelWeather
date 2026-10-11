package com.bennybar.israelweather.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.rememberLocationAccess
import com.bennybar.israelweather.ui.temp

/** Saved places: current location toggle, reorder, remove, add via search. */
@Composable
fun PlacesTab() {
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val forecasts by app.repo.forecasts.collectAsStateWithLifecycle()
    val english = isEnglish()
    val location = rememberLocationAccess()
    fun name(id: Int) = places.value?.firstOrNull { it.id == id }?.name(english) ?: "…"

    @Composable
    fun Summary(id: Int) {
        val f = forecasts[id]?.value ?: return
        val h = f.currentHour(israelNow()) ?: return
        Image(painterResource(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time))), null, Modifier.size(28.dp))
        Spacer(Modifier.width(6.dp))
        Text(temp(h.temp, settings), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_places), Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                FilledTonalIconButton(onClick = { ui.overlay = Overlay.SETTINGS }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = settings.useLocation && settings.currentForecastId != null) {
                        ui.focusPlace = settings.currentForecastId
                        ui.tab = 0
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.current_location), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    val cur = app.repo.placeFor(settings.currentForecastId, places.value)
                    if (settings.useLocation && cur != null) Text(cur.name(english), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.useLocation,
                    onCheckedChange = { on -> if (on) location.request() else app.settings.setUseLocation(false) },
                )
            }
        }
        itemsIndexed(settings.places, key = { _, id -> id }) { i, id ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable {
                        ui.focusPlace = id
                        ui.tab = 0
                    }
                    .padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name(id), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Summary(id)
                IconButton(onClick = { app.settings.movePlace(id, -1) }, enabled = i > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                }
                IconButton(onClick = { app.settings.movePlace(id, 1) }, enabled = i < settings.places.size - 1) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                }
                IconButton(onClick = { app.settings.removePlace(id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove)) }
            }
        }
        item {
            Button(onClick = { ui.overlay = Overlay.SEARCH }, Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.add_place))
            }
        }
    }
}
