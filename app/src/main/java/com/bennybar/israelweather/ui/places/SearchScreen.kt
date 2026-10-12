package com.bennybar.israelweather.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.CustomPlace
import com.bennybar.israelweather.data.GeoPlace
import com.bennybar.israelweather.data.IMS_RADIUS_KM
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.location.LocationResolver
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.theme.LocalWeatherColors
import kotlinx.coroutines.delay

/** Hebrew/English names compared without punctuation, gershayim or case. */
fun normalize(s: String) = s.lowercase().replace(Regex("[\\s\\-'\"׳״`,.()]"), "")

/** Open-Meteo towns in Israel for [query], a moment after typing stops, minus IMS matches already listed. */
@Composable
fun moreTowns(query: String, imsResults: List<Place>): List<GeoPlace> {
    val towns by produceState(emptyList<GeoPlace>(), query) {
        value = emptyList()
        if (query.trim().length < 2) return@produceState
        delay(350)
        value = runCatching { app.openMeteo.searchIsrael(query.trim()) }.getOrDefault(emptyList())
    }
    val imsNames = imsResults.flatMap { listOf(normalize(it.he), normalize(it.en)) }.toSet()
    return towns.filter { normalize(it.he) !in imsNames && normalize(it.en) !in imsNames }
}

/** A searched town as a custom place: IMS's forecast if a point is within IMS_RADIUS_KM, else its own. */
fun customPlaceFor(g: GeoPlace, ims: List<Place>): CustomPlace {
    val nearest = LocationResolver.nearest(ims, g.lat, g.lon)
    val near = nearest?.takeIf { LocationResolver.distanceKm(it, g.lat, g.lon) <= IMS_RADIUS_KM }
    return CustomPlace(-g.id, g.he, g.en, g.lat, g.lon, near?.id, nearest?.id)
}

/**
 * Search the 175 IMS locations, plus (as you type) other towns in Israel from Open-Meteo's
 * place search. Those are saved as custom places: IMS's forecast if one of its points is
 * within IMS_RADIUS_KM, else Open-Meteo for the exact spot.
 */
@Composable
fun SearchScreen() {
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val english = isEnglish()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        app.repo.refreshPlaces()
        focus.requestFocus()
    }
    val q = normalize(query)
    val all = places.value.orEmpty().sortedBy { it.name(english) }
    val results = if (q.isEmpty()) all else all
        .filter { normalize(it.he).contains(q) || normalize(it.en).contains(q) }
        .sortedBy { p -> if (normalize(p.he).startsWith(q) || normalize(p.en).startsWith(q)) 0 else 1 }

    val more = moreTowns(query, results)

    fun pickTown(g: GeoPlace) {
        val custom = customPlaceFor(g, places.value.orEmpty())
        app.settings.addCustomPlace(custom)
        ui.focusPlace = custom.id
        ui.tab = 0
        ui.overlay = Overlay.NONE
    }

    fun pick(p: Place) {
        app.settings.addPlace(p.id)
        ui.focusPlace = p.id
        ui.tab = 0
        ui.overlay = Overlay.NONE
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ui.overlay = Overlay.NONE }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            if (places.value == null) item {
                Text(stringResource(if (places.error != null) R.string.load_failed else R.string.loading), Modifier.padding(16.dp))
            }
            items(results, key = { it.id }) { p ->
                val saved = p.id in settings.places
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { pick(p) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name(english), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(p.name(!english), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                    if (saved) Icon(Icons.Rounded.Check, stringResource(R.string.saved), tint = LocalWeatherColors.current.accentInk)
                }
            }
            if (more.isNotEmpty()) item {
                Text(
                    stringResource(R.string.search_more), Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
            items(more, key = { "om${it.id}" }) { g ->
                val saved = -g.id in settings.places
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { pickTown(g) }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (english) g.en else g.he, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            listOfNotNull(if (english) g.he else g.en, g.region?.takeIf { !english }).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                        )
                    }
                    if (saved) Icon(Icons.Rounded.Check, stringResource(R.string.saved), tint = LocalWeatherColors.current.accentInk)
                }
            }
        }
    }
}
