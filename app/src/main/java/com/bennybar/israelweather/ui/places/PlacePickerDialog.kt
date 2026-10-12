package com.bennybar.israelweather.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.ui.isEnglish

/** Pick any IMS location or town in Israel (for a widget), without adding it to the saved places. */
@Composable
fun PlacePickerDialog(onPick: (Place) -> Unit, onDismiss: () -> Unit) {
    val places by app.repo.places.collectAsStateWithLifecycle()
    val english = isEnglish()
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { app.repo.refreshPlaces() }
    val q = normalize(query)
    val results = places.value.orEmpty()
        .filter { q.isEmpty() || normalize(it.he).contains(q) || normalize(it.en).contains(q) }
        .sortedWith(compareBy<Place> { p -> if (normalize(p.he).startsWith(q) || normalize(p.en).startsWith(q)) 0 else 1 }.thenBy { it.name(english) })
    val more = moreTowns(query, results)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.other_place)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { p ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                            Text(p.name(english), fontWeight = FontWeight.SemiBold)
                            Text(p.name(!english), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(more, key = { "om${it.id}" }) { g ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                val custom = customPlaceFor(g, places.value.orEmpty())
                                app.settings.rememberCustomPlace(custom)
                                onPick(Place(custom.id, custom.he, custom.en, custom.lat, custom.lon, 0))
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            Text(if (english) g.en else g.he, fontWeight = FontWeight.SemiBold)
                            Text(if (english) g.he else g.en, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
