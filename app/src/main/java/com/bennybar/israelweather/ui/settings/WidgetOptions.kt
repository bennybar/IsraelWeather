package com.bennybar.israelweather.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.places.PlacePickerDialog
import com.bennybar.israelweather.widget.SPECTRUM_LAYOUT_CLASSIC
import com.bennybar.israelweather.widget.SPECTRUM_LAYOUT_REFINED
import com.bennybar.israelweather.widget.SPECTRUM_LAYOUT_TILES
import com.bennybar.israelweather.widget.WIDGET_COLOR_GLASS
import com.bennybar.israelweather.widget.WIDGET_COLOR_LAVENDER
import com.bennybar.israelweather.widget.WIDGET_COLOR_MINT
import com.bennybar.israelweather.widget.WIDGET_COLOR_ORANGE
import com.bennybar.israelweather.widget.WIDGET_COLOR_ROSE
import com.bennybar.israelweather.widget.WIDGET_COLOR_SKY
import com.bennybar.israelweather.widget.WIDGET_COLOR_TWILIGHT
import com.bennybar.israelweather.widget.WIDGET_COLOR_WALLPAPER
import com.bennybar.israelweather.widget.WidgetKind
import com.bennybar.israelweather.widget.Widgets
import com.bennybar.israelweather.widget.spectrum
import com.bennybar.israelweather.widget.spectrumDayChoices
import com.bennybar.israelweather.widget.spectrumDays
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One widget's options: which place it shows and whether it has a "refresh now" button. */
@OptIn(ExperimentalLayoutApi::class)
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
        if (kind == WidgetKind.SPECTRUM) {
            listOf(
                WIDGET_COLOR_TWILIGHT to R.string.color_twilight,
                WIDGET_COLOR_SKY to R.string.color_sky,
                WIDGET_COLOR_MINT to R.string.color_mint,
                WIDGET_COLOR_ROSE to R.string.color_rose,
                WIDGET_COLOR_GLASS to R.string.color_glass,
                WIDGET_COLOR_WALLPAPER to R.string.color_wallpaper,
            )
        } else {
            emptyList()
        }
    var showWind by remember(appWidgetId) { mutableStateOf(app.settings.widgetShowWind(appWidgetId)) }
    var windDial by remember(appWidgetId) { mutableStateOf(app.settings.widgetWindDial(appWidgetId)) }
    var equalPills by remember(appWidgetId) { mutableStateOf(app.settings.widgetEqualPills(appWidgetId)) }
    var dayCount by remember(appWidgetId) { mutableStateOf(app.settings.widgetDays(appWidgetId)) }
    var glassBorderless by remember(appWidgetId) { mutableStateOf(app.settings.widgetGlassBorderless(appWidgetId)) }
    var glassClarity by remember(appWidgetId) { mutableFloatStateOf(app.settings.widgetGlassClarity(appWidgetId).toFloat()) }
    var layout by remember(appWidgetId) { mutableStateOf(app.settings.widgetLayout(appWidgetId)) }
    // The three-day layouts fix days, wind and pill width themselves.
    val classic = kind != WidgetKind.SPECTRUM || layout == SPECTRUM_LAYOUT_CLASSIC
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
        if (kind == WidgetKind.SPECTRUM) {
            Text(stringResource(R.string.widget_layout), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SPECTRUM_LAYOUT_CLASSIC to R.string.layout_classic,
                    SPECTRUM_LAYOUT_REFINED to R.string.layout_refined,
                    SPECTRUM_LAYOUT_TILES to R.string.layout_tiles,
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = layout == key,
                        onClick = {
                            layout = key
                            app.settings.setWidgetLayout(appWidgetId, key)
                            update()
                        },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            if (!classic) {
                Text(stringResource(R.string.layout_three_day_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        if (kind == WidgetKind.RANGES || (kind == WidgetKind.SPECTRUM && classic)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_wind), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = showWind, onCheckedChange = {
                    showWind = it
                    app.settings.setWidgetShowWind(appWidgetId, it)
                    update()
                })
            }
        }
        if (kind == WidgetKind.SPECTRUM && classic && showWind) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wind_dial), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = windDial, onCheckedChange = {
                    windDial = it
                    app.settings.setWidgetWindDial(appWidgetId, it)
                    update()
                })
            }
        }
        val dayChoices = if (kind == WidgetKind.SPECTRUM) spectrumDayChoices(layout) else emptyList()
        if (dayChoices.isNotEmpty()) {
            val shown = spectrumDays(layout, dayCount)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.widget_days), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow {
                    dayChoices.forEachIndexed { i, n ->
                        SegmentedButton(
                            selected = shown == n,
                            onClick = {
                                dayCount = n
                                app.settings.setWidgetDays(appWidgetId, n)
                                update()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, dayChoices.size),
                        ) { Text("$n") }
                    }
                }
            }
        }
        if (kind == WidgetKind.SPECTRUM && classic) {
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
            Text(stringResource(R.string.widget_color), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colors.forEach { (key, label) ->
                    FilterChip(
                        selected = color == key,
                        onClick = {
                            color = key
                            app.settings.setWidgetColor(appWidgetId, key)
                            update()
                        },
                        label = { Text(stringResource(label)) },
                        // A swatch of the palette: its background with its accent inside.
                        leadingIcon = {
                            val swatch = spectrum(key, context)
                            Box(
                                Modifier.size(18.dp).clip(CircleShape).background(swatch.bgColor).border(1.dp, swatch.swatchRing, CircleShape),
                            )
                        },
                    )
                }
            }
            if (color == WIDGET_COLOR_GLASS) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.glass_borderless), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Switch(checked = glassBorderless, onCheckedChange = {
                        glassBorderless = it
                        app.settings.setWidgetGlassBorderless(appWidgetId, it)
                        update()
                    })
                }
                // How see-through the glass is; the widget redraws when the slider is let go.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.glass_clarity), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${glassClarity.roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(
                    value = glassClarity,
                    onValueChange = { glassClarity = it },
                    valueRange = 0f..100f,
                    onValueChangeFinished = {
                        app.settings.setWidgetGlassClarity(appWidgetId, glassClarity.roundToInt())
                        update()
                    },
                )
            }
        }
    }
}
