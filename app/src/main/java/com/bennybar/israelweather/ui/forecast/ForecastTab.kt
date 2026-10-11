package com.bennybar.israelweather.ui.forecast

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Card
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.location.CurrentPlace
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.Overlay
import com.bennybar.israelweather.ui.hhmm
import com.bennybar.israelweather.ui.isEnglish
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime

/** One forecast page: the current location (id may still be unknown) or a saved place. */
private data class Page(val id: Int?, val current: Boolean)

@Composable
fun ForecastTab() {
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var locResult by remember { mutableStateOf<CurrentPlace.Result?>(null) }

    LaunchedEffect(Unit) {
        app.repo.refreshPlaces()
        app.repo.refreshWarnings()
    }
    // Locate on open (when allowed); the last fix keeps the page usable meanwhile.
    LaunchedEffect(settings.useLocation) {
        if (settings.useLocation && app.location.hasPermission()) locResult = CurrentPlace.resolve()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { locResult = if (granted) CurrentPlace.resolve() else CurrentPlace.Result.NO_PERMISSION }
    }

    val pages = buildList {
        if (settings.useLocation) add(Page(settings.currentPlace, current = true))
        settings.places.filter { !settings.useLocation || it != settings.currentPlace }.forEach { add(Page(it, current = false)) }
    }
    if (pages.isEmpty()) {
        EmptyState(
            onAdd = { ui.overlay = Overlay.SEARCH },
            onLocate = {
                app.settings.setUseLocation(true)
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
        )
        return
    }

    val pager = rememberPagerState { pages.size }
    LaunchedEffect(ui.focusPlace, pages.size) {
        val target = ui.focusPlace ?: return@LaunchedEffect
        val i = pages.indexOfFirst { it.id == target }
        if (i >= 0) {
            pager.animateScrollToPage(i)
            ui.focusPlace = null
        }
    }
    HorizontalPager(pager, Modifier.fillMaxSize(), key = { pages.getOrNull(it)?.let { p -> "${p.current}-${p.id}" } ?: it }) { i ->
        val page = pages[i]
        val dots: @Composable () -> Unit = { PageDots(pages.size, pager.currentPage, pages.first().current) }
        if (page.id == null) {
            LocatePage(
                result = locResult ?: if (app.location.hasPermission()) null else CurrentPlace.Result.NO_PERMISSION,
                dots = dots,
                onAllow = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                onRetry = { scope.launch { locResult = null; locResult = CurrentPlace.resolve() } },
            )
        } else {
            ForecastPage(page.id, page.current, dots)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ForecastPage(id: Int, current: Boolean, dots: @Composable () -> Unit) {
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val forecasts by app.repo.forecasts.collectAsStateWithLifecycle()
    val warnings by app.repo.warnings.collectAsStateWithLifecycle()
    val observations by app.repo.now.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val place = places.value?.firstOrNull { it.id == id }
    val f = forecasts[id]
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        app.repo.refreshForecast(id)
        app.repo.refreshNow(id)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                if (current) CurrentPlace.resolve()
                app.repo.refreshForecast(id, force = true)
                app.repo.refreshWarnings(force = true)
                app.repo.refreshNow(id)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Header(
                    name = place?.name(isEnglish()) ?: "",
                    current = current,
                    updated = f?.fetchedAt?.takeIf { it > 0 },
                    dots = dots,
                    onEdit = { ui.editCards = true },
                    onSettings = { ui.overlay = Overlay.SETTINGS },
                )
            }
            val data = f?.value
            when {
                data == null && f?.error != null && f.loading.not() -> item(key = "error") {
                    Message(stringResource(R.string.load_failed), stringResource(R.string.retry)) {
                        scope.launch { app.repo.refreshForecast(id, force = true) }
                    }
                }
                data == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                }
                else -> {
                    val local = place?.let { app.repo.warningsFor(it.rid, warnings.value) }.orEmpty()
                    items(settings.visibleCards, key = { it.name }) { card ->
                        when (card) {
                            Card.HERO -> HeroCard(data, observations[id], settings)
                            Card.ALERTS -> AlertsCard(local) { ui.tab = 2 }
                            Card.HOURLY -> HourlyCard(data, settings)
                            Card.WEEK -> WeekCard(data, settings)
                            Card.DETAILS -> DetailsCard(data, settings)
                            Card.RADAR -> RadarCard(place) { ui.tab = 1 }
                            Card.SUMMARY -> SummaryCard(data)
                        }
                    }
                    item(key = "source") {
                        Text(
                            stringResource(R.string.source_ims),
                            Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, current: Boolean, updated: Long?, dots: @Composable () -> Unit, onEdit: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Icon(Icons.Rounded.MyLocation, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                if (updated != null) {
                    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(updated), IsraelZone)
                    Text(
                        stringResource(R.string.updated_at, hhmm(t)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Rounded.Tune, stringResource(R.string.edit_cards)) }
            FilledTonalIconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
        }
        dots()
    }
}

@Composable
private fun PageDots(count: Int, selected: Int, firstIsCurrent: Boolean) {
    if (count < 2) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == selected
            if (i == 0 && firstIsCurrent) {
                Icon(
                    Icons.Rounded.MyLocation, null, Modifier.padding(horizontal = 3.dp).size(10.dp),
                    tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            } else {
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (on) 18.dp else 7.dp, 7.dp).clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun LocatePage(result: CurrentPlace.Result?, dots: @Composable () -> Unit, onAllow: () -> Unit, onRetry: () -> Unit) {
    val ui = LocalUi.current
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.current_location), true, null, dots, { ui.editCards = true }, { ui.overlay = Overlay.SETTINGS })
        when (result) {
            null -> Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(Modifier.size(64.dp))
            }
            CurrentPlace.Result.NO_PERMISSION -> Message(stringResource(R.string.location_why), stringResource(R.string.allow_location), onAllow)
            CurrentPlace.Result.OUTSIDE_ISRAEL -> Message(stringResource(R.string.outside_israel), stringResource(R.string.retry), onRetry)
            else -> Message(stringResource(R.string.no_fix), stringResource(R.string.retry), onRetry)
        }
    }
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onLocate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.empty_title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.empty_body), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLocate) {
            Icon(Icons.Rounded.MyLocation, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.use_my_location))
        }
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_place))
        }
    }
}
