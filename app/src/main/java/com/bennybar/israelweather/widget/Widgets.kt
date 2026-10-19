package com.bennybar.israelweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.IconStyle
import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Warning

enum class WidgetKind(val receiver: KClass<out GlanceAppWidgetReceiver>, val title: Int, val widget: () -> GlanceAppWidget) {
    DAILY_TONAL(DailyTonalReceiver::class, R.string.widget_daily_tonal, ::DailyTonalWidget),
    QUIET(QuietWeekReceiver::class, R.string.widget_quiet, ::QuietWeekWidget),
    RANGES(RangesReceiver::class, R.string.widget_ranges, ::RangesWidget),
    SPECTRUM(SpectrumReceiver::class, R.string.widget_spectrum, ::SpectrumWidget),
}

object Widgets {
    /** Bumped whenever data changes, so running widget sessions reload it. */
    val version = MutableStateFlow(0)

    /** App widget ids whose "refresh now" is in progress. */
    val refreshing = MutableStateFlow(emptySet<Int>())

    /** App widget ids whose last "refresh now" failed (red refresh icon until the next try). */
    val failed = MutableStateFlow(emptySet<Int>())

    suspend fun updateAll(context: Context) {
        version.update { it + 1 }
        placed(context).forEach { (_, id) -> pushNow(context, id) }
        // Glance's own update too, for its running sessions.
        WidgetKind.entries.forEach { k -> runCatching { k.widget().updateAll(context) } }
    }

    /**
     * Renders one widget right here and hands it to the home screen. Glance's own updates run
     * as WorkManager jobs, which Samsung can hold back for a long time when the app is idle,
     * so taps and settings changes could appear to do nothing there.
     */
    @OptIn(ExperimentalGlanceApi::class)
    suspend fun pushNow(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider?.className ?: return
        val kind = WidgetKind.entries.firstOrNull { it.receiver.java.name == provider } ?: return
        try {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            manager.updateAppWidget(appWidgetId, kind.widget().compose(context, glanceId, manager.getAppWidgetOptions(appWidgetId)))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Glance's regular update (in updateAll) still follows.
        }
    }

    /** Every widget on the home screen, with its kind. */
    fun placed(context: Context): List<Pair<WidgetKind, Int>> {
        val m = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { k -> m.getAppWidgetIds(ComponentName(context, k.receiver.java)).map { k to it } }
    }

    /** Whether any placed widget follows the device ("My location") rather than a set place. */
    fun followsLocation(context: Context): Boolean =
        placed(context).any { (_, id) -> app.settings.widgetPlace(id)?.toIntOrNull() == null }

    /** Forecast ids that placed widgets show (resolving "current" and custom places). */
    fun placeIds(context: Context): Set<Int> =
        placed(context).mapNotNull { (_, id) -> placeIdFor(id)?.let { app.settings.value.forecastIdFor(it) } }.toSet()

    fun requestPin(context: Context, kind: WidgetKind) {
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, kind.receiver.java), null, null)
    }

    /**
     * Publishes each widget's generated preview (its real layout with sample
     * data) to the launcher's widget picker. Android drops the "has a generated
     * preview" flag whenever the app is updated or its widgets are reloaded, so
     * this re-publishes any widget whose flag is missing (or whose language
     * changed). Android rate-limits the calls; anything refused is retried on
     * the next app start or background refresh.
     */
    suspend fun publishPreviews(context: Context) {
        val prefs = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
        val lang = context.resources.configuration.locales[0].language
        val manager = GlanceAppWidgetManager(context)
        val infos = runCatching {
            AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
                .associateBy { it.provider.className }
        }.getOrDefault(emptyMap())
        WidgetKind.entries.forEach { k ->
            val key = "preview_lang_${k.name}"
            val hasPreview = (infos[k.receiver.java.name]?.generatedPreviewCategories ?: 0) != 0
            if (hasPreview && prefs.getString(key, null) == lang) return@forEach
            val result = runCatching { manager.setWidgetPreviews(k.receiver) }.getOrNull()
            if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) prefs.edit().putString(key, lang).apply()
        }
    }

    fun placeIdFor(appWidgetId: Int): Int? {
        val s = app.settings.value
        return app.settings.widgetPlace(appWidgetId)?.toIntOrNull()
            ?: s.currentForecastId.takeIf { s.useLocation }
            ?: s.places.firstOrNull()
            ?: s.currentForecastId
    }

    suspend fun load(context: Context, glanceId: GlanceId): WidgetData {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val showRefresh = app.settings.widgetShowRefresh(appWidgetId)
        val color = app.settings.widgetColor(appWidgetId)
        val showWind = app.settings.widgetShowWind(appWidgetId)
        val windDial = app.settings.widgetWindDial(appWidgetId)
        val equalPills = app.settings.widgetEqualPills(appWidgetId)
        val dayCount = app.settings.widgetDays(appWidgetId)
        val layout = app.settings.widgetLayout(appWidgetId)
        val glassClarity = app.settings.widgetGlassClarity(appWidgetId)
        val glassBorderless = app.settings.widgetGlassBorderless(appWidgetId)
        val id = placeIdFor(appWidgetId)
            ?: return WidgetData(null, null, showRefresh, null, color, showWind, windDial = windDial, equalPills = equalPills, iconStyle = app.settings.value.iconStyle, dayCount = dayCount, layout = layout, glassClarity = glassClarity, glassBorderless = glassBorderless)
        val place = app.repo.placeFor(id, app.repo.ensurePlacesLoaded())
        val forecastId = app.settings.value.forecastIdFor(id)
        var cached = app.repo.cachedForecast(forecastId)
        if (cached?.value == null && app.repo.refreshForecast(forecastId)) cached = app.repo.cachedForecast(forecastId)
        val forecast = cached?.value
        return WidgetData(place, forecast, showRefresh, cached?.fetchedAt?.takeIf { it > 0 }, color, showWind, id, windDial, equalPills, app.settings.value.iconStyle, dayCount, layout, glassClarity, glassBorderless)
    }
}

data class WidgetData(
    val place: Place?,
    val forecast: Forecast?,
    val showRefresh: Boolean,
    /** When the forecast was last fetched (epoch millis). */
    val updated: Long?,
    /** [WIDGET_COLOR_LAVENDER], [WIDGET_COLOR_ORANGE], or (Spectrum only) twilight, sky, mint, rose or wallpaper. */
    val color: String = WIDGET_COLOR_LAVENDER,
    /** Ranges widget: show each day's strongest wind. */
    val showWind: Boolean = false,
    /** The app page a tap opens (a saved place id, or the current-location forecast id). */
    val placeId: Int? = null,
    /** Spectrum: wind as an arrow + speed capsule per day. */
    val windDial: Boolean = false,
    /** Spectrum: rain pills all the same width. */
    val equalPills: Boolean = false,
    /** The app's icon set: part of the data so a running widget redraws when it changes. */
    val iconStyle: IconStyle = IconStyle.MATERIAL,
    /** Spectrum: days shown (2, 3 or 5). */
    val dayCount: Int = 5,
    /** Spectrum: [SPECTRUM_LAYOUT_CLASSIC] or a three-day layout. */
    val layout: String = SPECTRUM_LAYOUT_CLASSIC,
    /** Spectrum, Liquid glass: 0–100, higher = more see-through. */
    val glassClarity: Int = 65,
    /** Spectrum, Liquid glass: borderless tiles. */
    val glassBorderless: Boolean = false,
)

const val WIDGET_COLOR_LAVENDER = "lavender"
const val WIDGET_COLOR_ORANGE = "orange"
const val WIDGET_COLOR_TWILIGHT = "twilight"
const val WIDGET_COLOR_SKY = "sky"
const val WIDGET_COLOR_MINT = "mint"
const val WIDGET_COLOR_ROSE = "rose"
/** Translucent "liquid glass": the wallpaper shows through a frosted card. */
const val WIDGET_COLOR_GLASS = "glass"
/** Material You: the phone's wallpaper-based palette. */
const val WIDGET_COLOR_WALLPAPER = "wallpaper"

/** The widgets' "refresh now" button: re-fetch this widget's place, then redraw every widget. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        Widgets.failed.update { it - appWidgetId }
        Widgets.refreshing.update { it + appWidgetId }
        // Spinner right away, drawn directly (no background job to wait for).
        Widgets.pushNow(context, appWidgetId)
        val started = System.currentTimeMillis()
        // Only this widget's forecast. The fetch runs in the app's scope, and we wait at most
        // 8 s: a tap's callback gets limited time. If the answer comes later, it still redraws.
        val fetch = app.scope.async {
            val id = Widgets.placeIdFor(appWidgetId) ?: return@async false
            app.repo.refreshForecast(app.settings.value.forecastIdFor(id), force = true, updateWidgets = false).also { ok ->
                if (ok && appWidgetId in Widgets.failed.value) {
                    Widgets.failed.update { it - appWidgetId }
                    Widgets.updateAll(context)
                }
            }
        }
        var ok = false
        try {
            ok = withTimeoutOrNull(8_000) { fetch.await() } == true
            // Keep the spinner up long enough to be seen, even when the fetch is instant.
            delay(1_000 - (System.currentTimeMillis() - started))
        } finally {
            Widgets.refreshing.update { it - appWidgetId }
            if (!ok) Widgets.failed.update { it + appWidgetId }
            Widgets.updateAll(context)
        }
    }
}
