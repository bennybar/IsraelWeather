package com.bennybar.israelweather.ui.settings

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.LocaleList
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.ui.rememberLocationAccess
import com.bennybar.israelweather.BuildConfig
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.IconStyle
import com.bennybar.israelweather.data.TempUnit
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.ThemeMode
import com.bennybar.israelweather.data.WindUnit
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.theme.Accents
import com.bennybar.israelweather.widget.RefreshWorker
import com.bennybar.israelweather.widget.WidgetKind
import com.bennybar.israelweather.widget.Widgets
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import com.bennybar.israelweather.data.ForecastSource

private val refreshOptions = listOf(15, 30, 60, 180, 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val ui = LocalUi.current
    val context = LocalContext.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val localeManager = remember { context.getSystemService(LocaleManager::class.java) }
    var language by remember { mutableStateOf(localeManager.applicationLocales.toLanguageTags()) }
    // Bumped after pinning a widget so the placed-widgets list re-reads; also on
    // resume, since the system's "Add to home screen" dialog pauses us.
    var widgetsVersion by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        widgetsVersion++
        onPauseOrDispose { }
    }
    val location = rememberLocationAccess()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ui.overlay = Overlay.NONE }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section(stringResource(R.string.section_display)) {
                    Label(stringResource(R.string.temperature))
                    Segmented(listOf("°C", "°F"), settings.tempUnit.ordinal) { app.settings.setTempUnit(TempUnit.entries[it]) }
                    Label(stringResource(R.string.wind))
                    Segmented(listOf(stringResource(R.string.unit_kmh), stringResource(R.string.unit_ms)), settings.windUnit.ordinal) {
                        app.settings.setWindUnit(WindUnit.entries[it])
                    }
                    Label(stringResource(R.string.theme))
                    Segmented(
                        listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                        settings.theme.ordinal,
                    ) { app.settings.setTheme(ThemeMode.entries[it]) }
                    Label(stringResource(R.string.accent))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Accents.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(c)
                                    .then(if (i == settings.accent) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { app.settings.setAccent(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (i == settings.accent) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                    Label(stringResource(R.string.weather_icons))
                    val styles = listOf(
                        IconStyle.MATERIAL to R.string.icons_material,
                        IconStyle.METEOCONS to R.string.icons_meteocons,
                        IconStyle.FLUENT to R.string.icons_fluent,
                        IconStyle.NOTO to R.string.icons_noto,
                        IconStyle.GLASS to R.string.icons_glass,
                        IconStyle.ONEUI to R.string.icons_oneui,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        styles.forEach { (style, label) ->
                            FilterChip(
                                selected = settings.iconStyle == style,
                                onClick = {
                                    app.settings.setIconStyle(style)
                                    // Widgets draw the same set.
                                    app.scope.launch { Widgets.updateAll(context) }
                                },
                                label = { Text(stringResource(label)) },
                                leadingIcon = {
                                    Image(painterResource(WeatherCodes.drawable(1220, style = style)), null, Modifier.size(22.dp))
                                },
                            )
                        }
                    }
                    Label(stringResource(R.string.language))
                    val langs = listOf("" to stringResource(R.string.lang_system), "he" to "עברית", "en" to "English")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        langs.forEach { (tag, label) ->
                            FilterChip(
                                selected = language == tag,
                                onClick = {
                                    language = tag
                                    localeManager.applicationLocales = if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            item {
                // Where forecasts come from; switching re-fetches every forecast in use.
                Section(stringResource(R.string.section_source)) {
                    val sources = listOf(
                        ForecastSource.IMS_ECMWF to R.string.source_ims_ecmwf,
                        ForecastSource.ECMWF to R.string.source_ecmwf,
                        ForecastSource.FORECA to R.string.source_foreca_choice,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sources.forEach { (src, label) ->
                            FilterChip(
                                selected = settings.forecastSource == src,
                                onClick = {
                                    app.settings.setForecastSource(src)
                                    app.scope.launch { app.repo.refreshAllForecasts() }
                                },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Text(
                        stringResource(
                            when (settings.forecastSource) {
                                ForecastSource.IMS_ECMWF -> R.string.source_ims_ecmwf_hint
                                ForecastSource.ECMWF -> R.string.source_ecmwf_hint
                                ForecastSource.FORECA -> R.string.source_foreca_hint
                            },
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                    if (settings.forecastSource == ForecastSource.FORECA) {
                        var key by remember { mutableStateOf(settings.forecaKey) }
                        var check by remember { mutableStateOf<Boolean?>(null) }
                        var checking by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = key,
                            onValueChange = {
                                key = it
                                check = null
                                app.settings.setForecaKey(it)
                            },
                            label = { Text(stringResource(R.string.foreca_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                enabled = key.isNotBlank() && !checking,
                                onClick = {
                                    checking = true
                                    app.scope.launch {
                                        val ok = app.foreca.check(key.trim())
                                        check = ok
                                        checking = false
                                        if (ok) app.repo.refreshAllForecasts()
                                    }
                                },
                            ) { Text(stringResource(R.string.foreca_check)) }
                            Text(
                                when {
                                    checking -> stringResource(R.string.foreca_checking)
                                    check == true -> stringResource(R.string.foreca_ok)
                                    check == false -> stringResource(R.string.foreca_bad)
                                    else -> ""
                                },
                                color = if (check == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.foreca_steps), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://developer.foreca.com/checkout?plan=Freemium-EUR-Monthly&mode=paid")))
                        }) { Text(stringResource(R.string.foreca_signup)) }
                    }
                }
            }
            item {
                Section(stringResource(R.string.section_location)) {
                    // Re-read on resume: the user may have changed it in system settings.
                    var perms by remember { mutableIntStateOf(0) }
                    LifecycleResumeEffect(Unit) {
                        perms++
                        onPauseOrDispose { }
                    }
                    val access = remember(perms) {
                        when {
                            app.location.hasPrecise() -> R.string.loc_precise
                            app.location.hasPermission() -> R.string.loc_approx
                            else -> R.string.loc_denied
                        }
                    }
                    val background = remember(perms) { app.location.hasBackground() }
                    PermissionRow(stringResource(R.string.loc_access), stringResource(access), if (access == R.string.loc_denied) location.request else null)
                    PermissionRow(
                        stringResource(R.string.loc_background),
                        stringResource(if (background) R.string.loc_allowed else R.string.loc_denied),
                        if (background) null else location.requestBackground,
                    )
                    Text(stringResource(R.string.loc_background_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            item {
                Section(stringResource(R.string.section_home)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { ui.editCards = true }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.edit_cards), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.edit_cards_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    }
                }
            }
            item {
                Section(stringResource(R.string.section_refresh)) {
                    Text(stringResource(R.string.refresh_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        refreshOptions.forEach { m ->
                            FilterChip(
                                selected = settings.refreshMinutes == m,
                                onClick = {
                                    app.settings.setRefreshMinutes(m)
                                    RefreshWorker.schedule(context, m)
                                },
                                label = {
                                    Text(
                                        when {
                                            m == 0 -> stringResource(R.string.refresh_off)
                                            m < 60 -> stringResource(R.string.minutes, m)
                                            else -> stringResource(R.string.hours, m / 60)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.section_widgets)) {
                    val placed = remember(widgetsVersion) { Widgets.placed(context) }
                    if (placed.isEmpty()) Text(stringResource(R.string.widgets_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    placed.forEach { (kind, id) -> WidgetOptions(id, stringResource(kind.title)) }
                    Label(stringResource(R.string.add_widget))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetKind.entries.forEach { kind ->
                            OutlinedButton(onClick = {
                                Widgets.requestPin(context, kind)
                                widgetsVersion++
                            }) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Text(stringResource(kind.title), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.section_about)) {
                    Text(stringResource(R.string.about_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stringResource(R.string.version)} ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/bennybar/IsraelWeather")))
                    }) { Text("GitHub") }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, state: String, onFix: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(state, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        if (onFix != null) OutlinedButton(onClick = onFix) { Text(stringResource(R.string.grant)) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(top = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}
