package com.bennybar.israelweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
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
import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Warning

enum class WidgetKind(val receiver: KClass<out GlanceAppWidgetReceiver>, val title: Int, val widget: () -> GlanceAppWidget) {
    HOURLY(HourlyWidgetReceiver::class, R.string.widget_hourly, ::HourlyWidget),
    HOURLY_TABLE(HourlyTableReceiver::class, R.string.widget_hourly_table, ::HourlyTableWidget),
    HOURLY_TONAL(HourlyTonalReceiver::class, R.string.widget_hourly_tonal, ::HourlyTonalWidget),
    DAILY_TABLE(DailyTableReceiver::class, R.string.widget_daily_table, ::DailyTableWidget),
    DAILY_TONAL(DailyTonalReceiver::class, R.string.widget_daily_tonal, ::DailyTonalWidget),
    QUIET(QuietWeekReceiver::class, R.string.widget_quiet, ::QuietWeekWidget),
    RANGES(RangesReceiver::class, R.string.widget_ranges, ::RangesWidget),
    SPECTRUM(SpectrumReceiver::class, R.string.widget_spectrum, ::SpectrumWidget),
    STRIP(StripWidgetReceiver::class, R.string.widget_strip, ::StripWidget),
}

object Widgets {
    /** Bumped whenever data changes, so running widget sessions reload it. */
    val version = MutableStateFlow(0)

    /** App widget ids whose "refresh now" is in progress. */
    val refreshing = MutableStateFlow(emptySet<Int>())

    suspend fun updateAll(context: Context) {
        version.update { it + 1 }
        WidgetKind.entries.forEach { k -> runCatching { k.widget().updateAll(context) } }
    }

    /** Every widget on the home screen, with its kind. */
    fun placed(context: Context): List<Pair<WidgetKind, Int>> {
        val m = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { k -> m.getAppWidgetIds(ComponentName(context, k.receiver.java)).map { k to it } }
    }

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
        val id = placeIdFor(appWidgetId) ?: return WidgetData(null, null, showRefresh, null, color, showWind)
        val place = app.repo.placeFor(id, app.repo.ensurePlacesLoaded())
        val forecastId = app.settings.value.forecastIdFor(id)
        var cached = app.repo.cachedForecast(forecastId)
        if (cached?.value == null && app.repo.refreshForecast(forecastId)) cached = app.repo.cachedForecast(forecastId)
        val forecast = cached?.value
        return WidgetData(place, forecast, showRefresh, cached?.fetchedAt?.takeIf { it > 0 }, color, showWind)
    }
}

data class WidgetData(
    val place: Place?,
    val forecast: Forecast?,
    val showRefresh: Boolean,
    /** When the forecast was last fetched (epoch millis). */
    val updated: Long?,
    /** [WIDGET_COLOR_LAVENDER], [WIDGET_COLOR_ORANGE] or (Spectrum only) [WIDGET_COLOR_TWILIGHT]. */
    val color: String = WIDGET_COLOR_LAVENDER,
    /** Ranges widget: show each day's strongest wind. */
    val showWind: Boolean = false,
)

const val WIDGET_COLOR_LAVENDER = "lavender"
const val WIDGET_COLOR_ORANGE = "orange"
const val WIDGET_COLOR_TWILIGHT = "twilight"

/** The widgets' "refresh now" button: re-fetch this widget's place, then redraw every widget. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        Widgets.refreshing.update { it + appWidgetId }
        // Redraw this widget now, so the spinner shows while we fetch.
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className
        WidgetKind.entries.firstOrNull { it.receiver.java.name == provider }?.let { runCatching { it.widget().update(context, glanceId) } }
        val started = System.currentTimeMillis()
        try {
            Widgets.placeIdFor(appWidgetId)?.let { app.repo.refreshForecast(app.settings.value.forecastIdFor(it), force = true, updateWidgets = false) }
            app.repo.refreshWarnings(force = true)
            // Keep the spinner up long enough to be seen, even when the fetch is instant.
            delay(1_000 - (System.currentTimeMillis() - started))
        } finally {
            Widgets.refreshing.update { it - appWidgetId }
            Widgets.updateAll(context)
        }
    }
}
