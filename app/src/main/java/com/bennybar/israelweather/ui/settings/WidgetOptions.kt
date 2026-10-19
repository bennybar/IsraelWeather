package com.bennybar.israelweather.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.places.PlacePickerDialog
import com.bennybar.israelweather.widget.WIDGET_COLOR_LAVENDER
import com.bennybar.israelweather.widget.WIDGET_COLOR_ORANGE
import com.bennybar.israelweather.widget.WIDGET_COLOR_TWILIGHT
import com.bennybar.israelweather.widget.WidgetKind
import com.bennybar.israelweather.widget.Widgets
import kotlinx.coroutines.launch

/** One widget's options: which place it shows and whether it has a "refresh now" button. */
@Composable
fun WidgetOptions(appWidgetId: Int, title: String?) {
    val context = LocalContext.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val english = isEnglish()
    val scope = rememberCoroutineScope()
    var place by remember(appWidgetId) { mutableStateOf(app.settings.widgetPlace(appWidgetId) ?: "current") }
    var showRefresh by remember(appWidgetId) { mutableStateOf(app.settings.widgetShowRefresh(appWidgetId)) }
    var color by remember(appWidgetId) { mutableStateOf(app.settings.widgetColor(appWidgetId)) }
    val kind = remember(appWidgetId) { Widgets.placed(context).firstOrNull { it.second == appWidgetId }?.first }
    val hasColor = kind in setOf(WidgetKind.QUIET, WidgetKind.RANGES, WidgetKind.SPECTRUM)
    val colors = listOf(WIDGET_COLOR_LAVENDER to R.string.color_lavender, WIDGET_COLOR_ORANGE to R.string.color_orange) +
        if (kind == WidgetKind.SPECTRUM) listOf(WIDGET_COLOR_TWILIGHT to R.string.color_twilight) else emptyList()
    var showWind by remember(appWidgetId) { mutableStateOf(app.settings.widgetShowWind(appWidgetId)) }
    var windDial by remember(appWidgetId) { mutableStateOf(app.settings.widgetWindDial(appWidgetId)) }
    var equalPills by remember(appWidgetId) { mutableStateOf(app.settings.widgetEqualPills(appWidgetId)) }
    var dayCount by remember(appWidgetId) { mutableStateOf(app.settings.widgetDays(appWidgetId)) }
    var open by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    fun nameOf(key: String) = key.toIntOrNull()?.let { id -> app.repo.placeFor(id, places.value)?.name(english) ?: "$id" }
    val update: () -> Unit = { scope.launch { Widgets.updateAll(context) } }
    fun choose(key: String) {
        place = key
        app.settings.setWidgetPlace(appWidgetId, key)
        update()
    }
    // Saved places, plus the widget's own pick when it isn't one of them.
    val keys = (listOf("current") + settings.places.map { it.toString() } + place).distinct()

    if (picking) {
        PlacePickerDialog(
            onPick = { p ->
                picking = false
                choose(p.id.toString())
            },
            onDismiss = { picking = false },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) Text(title, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.widget_place), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick = { open = true }) { Text(nameOf(place) ?: stringResource(R.string.current_location)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    keys.forEach { key ->
                        DropdownMenuItem(
                            text = { Text(nameOf(key) ?: stringResource(R.string.current_location)) },
                            onClick = {
                                open = false
                                choose(key)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.other_place)) },
                        onClick = {
                            open = false
                            picking = true
                        },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.show_refresh), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = showRefresh, onCheckedChange = {
                showRefresh = it
                app.settings.setWidgetShowRefresh(appWidgetId, it)
                update()
            })
        }
        if (kind == WidgetKind.RANGES || kind == WidgetKind.SPECTRUM) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_wind), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = showWind, onCheckedChange = {
                    showWind = it
                    app.settings.setWidgetShowWind(appWidgetId, it)
                    update()
                })
            }
        }
        if (kind == WidgetKind.SPECTRUM && showWind) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wind_dial), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = windDial, onCheckedChange = {
                    windDial = it
                    app.settings.setWidgetWindDial(appWidgetId, it)
                    update()
                })
            }
        }
        if (kind == WidgetKind.SPECTRUM) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_days), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow {
                    listOf(2, 3, 5).forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = dayCount == n,
                            onClick = {
                                dayCount = n
                                app.settings.setWidgetDays(appWidgetId, n)
                                update()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, 3),
                        ) { Text("$n") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.equal_pills), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = equalPills, onCheckedChange = {
                    equalPills = it
                    app.settings.setWidgetEqualPills(appWidgetId, it)
                    update()
                })
            }
        }
        if (hasColor) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_color), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow {
                    colors.forEachIndexed { i, (key, label) ->
                        SegmentedButton(
                            selected = color == key,
                            onClick = {
                                color = key
                                app.settings.setWidgetColor(appWidgetId, key)
                                update()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, colors.size),
                        ) { Text(stringResource(label)) }
                    }
                }
            }
        }
    }
}
