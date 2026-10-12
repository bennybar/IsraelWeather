package com.bennybar.israelweather.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.ui.alerts.AlertsTab
import com.bennybar.israelweather.ui.forecast.ForecastTab
import com.bennybar.israelweather.ui.places.PlacesTab
import com.bennybar.israelweather.ui.radar.RadarTab

enum class Overlay { NONE, SETTINGS, SEARCH }

/** App-wide UI state: the selected tab, full-screen overlays and cross-tab requests. */
class UiController {
    var tab by mutableIntStateOf(0)
    var overlay by mutableStateOf(Overlay.NONE)
    var editCards by mutableStateOf(false)

    /** A place the forecast pager should jump to (set after adding one). */
    var focusPlace by mutableStateOf<Int?>(null)
}

val LocalUi = staticCompositionLocalOf<UiController> { error("no UiController") }

private class NavItem(val off: ImageVector, val on: ImageVector, val label: Int)

private val navItems = listOf(
    NavItem(Icons.Outlined.WbSunny, Icons.Rounded.WbSunny, R.string.tab_forecast),
    NavItem(Icons.Outlined.Radar, Icons.Rounded.Radar, R.string.tab_radar),
    NavItem(Icons.Outlined.WarningAmber, Icons.Rounded.Warning, R.string.tab_alerts),
    NavItem(Icons.Outlined.Place, Icons.Rounded.Place, R.string.tab_places),
)

/** The home shell: Forecast / Radar / Alerts / Places with Ilay's floating "pop" pill nav. */
@Composable
fun HomeScreen() {
    val ui = LocalUi.current
    var chrome by remember { mutableStateOf(true) }
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val warnings by app.repo.warnings.collectAsStateWithLifecycle()
    val places by app.repo.places.collectAsStateWithLifecycle()

    // Badge: active weather warnings (not news flashes) for the user's places.
    val myRegions = (settings.places + listOfNotNull(settings.currentPlace.takeIf { settings.useLocation }))
        .mapNotNull { id -> app.repo.placeFor(id, places.value)?.rid }.toSet()
    val alertCount = warnings.value.orEmpty().count { w -> !w.isFlash && w.isActive() && w.regions.any { it in myRegions } }

    // Hide the nav while scrolling down a tab's list, reveal on scrolling up or at the top.
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -2f) chrome = false else if (available.y > 2f) chrome = true
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) chrome = true // overscrolling at the top
                return Offset.Zero
            }
        }
    }

    // Compose the other tabs one frame after the first paint; they then stay alive.
    var allTabs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        allTabs = true
    }

    val navOffset by animateFloatAsState(if (chrome) 0f else 1.4f, tween(220, easing = FastOutSlowInEasing), label = "nav")

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        KeepAliveTabs(
            selected = ui.tab,
            count = 4,
            composeAll = allTabs,
            modifier = Modifier.fillMaxSize().statusBarsPadding().nestedScroll(scrollConnection),
        ) { i ->
            when (i) {
                0 -> ForecastTab()
                1 -> RadarTab()
                2 -> AlertsTab()
                else -> PlacesTab()
            }
        }
        // A fade from the page colour behind the nav, so the pill never sits on a card of the same tone.
        val scrim = MaterialTheme.colorScheme.surface
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = (1f - navOffset).coerceIn(0f, 1f) }
                .background(Brush.verticalGradient(listOf(scrim.copy(alpha = 0f), scrim.copy(alpha = 0.92f))))
                .navigationBarsPadding()
                .height(110.dp),
        )
        FloatingNav(
            selected = ui.tab,
            alerts = alertCount,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = size.height * navOffset },
            onSelected = { i ->
                ui.tab = i
                chrome = true
            },
        )
    }
}

/** IndexedStack: every tab stays composed, but only the selected one is measured, placed and drawn. */
@Composable
private fun KeepAliveTabs(
    selected: Int,
    count: Int,
    composeAll: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Layout(
        content = {
            for (i in 0 until count) {
                Box(Modifier.fillMaxSize()) { if (composeAll || i == selected) content(i) }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val p = measurables[selected].measure(constraints)
        layout(p.width, p.height) { p.place(0, 0) }
    }
}

/**
 * Compact floating nav (from Ilay): a centred pill of icon buttons; the current
 * tab expands into a filled primary pill with its label. Dragging a finger
 * along it moves the selection from tab to tab.
 */
@Composable
private fun FloatingNav(
    selected: Int,
    alerts: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    val shape = RoundedCornerShape(32.dp)
    val haptic = LocalHapticFeedback.current
    val spans = remember { arrayOfNulls<ClosedFloatingPointRange<Float>>(navItems.size) }
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelected)
    Box(modifier.navigationBarsPadding().padding(bottom = 20.dp)) {
        Surface(
            Modifier
                .height(64.dp)
                .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f)),
            shape = shape,
            color = if (dark) cs.surfaceContainerHigh else cs.surfaceContainer,
            border = BorderStroke(1.dp, cs.outlineVariant),
        ) {
            Row(
                Modifier
                    .pointerInput(Unit) {
                        // Only switch once the finger is inside another tab, so a
                        // tab growing under it can't bounce the selection back.
                        fun pick(x: Float) {
                            val i = spans.indexOfFirst { it != null && x in it }
                            if (i >= 0 && i != current) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                select(i)
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { pick(it.x) },
                            onHorizontalDrag = { change, _ -> pick(change.position.x) },
                        )
                    }
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                navItems.forEachIndexed { i, item ->
                    NavButton(
                        item = item,
                        selected = selected == i,
                        badge = if (i == 2) alerts else 0,
                        onClick = { onSelected(i) },
                        modifier = Modifier.onPlaced { c ->
                            val x = c.positionInParent().x
                            spans[i] = x..(x + c.size.width)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavButton(
    item: NavItem,
    selected: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(item.label)
    val contentColor by animateColorAsState(if (selected) cs.onPrimary else cs.onSurfaceVariant, tween(250), label = "navFg")
    val pillColor by animateColorAsState(if (selected) cs.primary else cs.primary.copy(alpha = 0f), tween(250), label = "navBg")
    Row(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(pillColor)
            .clickable(onClickLabel = label, onClick = onClick)
            // The selected pill grows to fit its label with a little bounce.
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = if (selected) 18.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon: @Composable () -> Unit = {
            Icon(if (selected) item.on else item.off, label, Modifier.size(24.dp), tint = contentColor)
        }
        if (badge > 0) {
            BadgedBox(badge = { Badge { Text("$badge") } }) { icon() }
        } else {
            icon()
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = contentColor, maxLines = 1)
        }
    }
}
