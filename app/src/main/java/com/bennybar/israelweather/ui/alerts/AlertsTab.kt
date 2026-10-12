package com.bennybar.israelweather.ui.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.data.Warning
import com.bennybar.israelweather.ui.hhmm
import java.time.Instant
import java.time.LocalDateTime
import com.bennybar.israelweather.ui.LocalUi
import com.bennybar.israelweather.ui.appLocale
import com.bennybar.israelweather.ui.forLevel
import com.bennybar.israelweather.ui.isEnglish
import com.bennybar.israelweather.ui.theme.LocalWeatherColors
import com.bennybar.israelweather.ui.warningLabel
import com.bennybar.israelweather.ui.warningRange
import kotlinx.coroutines.launch

/** IMS warnings: first the ones covering the user's places, then the rest of the country. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsTab() {
    val ui = LocalUi.current
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val warnings by app.repo.warnings.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(ui.tab == 2) { if (ui.tab == 2) app.repo.refreshWarnings() }

    val myRegions = (settings.places + listOfNotNull(settings.currentPlace.takeIf { settings.useLocation }))
        .mapNotNull { id -> app.repo.placeFor(id, places.value)?.rid }.toSet()
    val active = warnings.value.orEmpty().filter { it.isActive() }
    val mine = active.filter { w -> w.regions.any { it in myRegions } }
    val others = active - mine.toSet()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                app.repo.refreshWarnings(force = true)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text(stringResource(R.string.tab_alerts), Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
            if (warnings.value == null) {
                // Never loaded: "no warnings" would be a claim we can't make.
                item { Empty(stringResource(if (warnings.error != null) R.string.load_failed else R.string.loading)) }
            } else {
                if (warnings.error != null) item { Empty(stringResource(R.string.warnings_stale, hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(warnings.fetchedAt), IsraelZone)))) }
                item { SectionTitle(stringResource(R.string.alerts_mine)) }
                if (mine.isEmpty()) item { Empty(stringResource(R.string.alerts_none_mine)) }
                items(mine, key = { "m${it.id}" }) { WarningItem(it) }
                item { SectionTitle(stringResource(R.string.alerts_elsewhere)) }
                if (others.isEmpty()) item { Empty(stringResource(R.string.alerts_none)) }
                items(others, key = { "o${it.id}" }) { WarningItem(it) }
            }
            item {
                Text(
                    stringResource(R.string.source_ims),
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 4.dp, top = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp)) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WarningItem(w: Warning) {
    val wc = LocalWeatherColors.current
    val english = isEnglish()
    val flash = w.isFlash
    val bg = if (flash) MaterialTheme.colorScheme.surfaceContainer else wc.warningBg
    val fg = if (flash) MaterialTheme.colorScheme.onSurface else wc.onWarningBg
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(10.dp).height(44.dp).clip(RoundedCornerShape(5.dp)).background(wc.forLevel(w.level)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(listOfNotNull(w.typeName.get(english), warningLabel(w)).joinToString(" · "), color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(warningRange(w, appLocale()), color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(w.text(english), color = fg, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
