package com.bennybar.israelweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import kotlin.reflect.KClass
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
    DAILY(DailyWidgetReceiver::class, R.string.widget_daily, ::DailyWidget),
    DAILY_TABLE(DailyTableReceiver::class, R.string.widget_daily_table, ::DailyTableWidget),
    DAILY_TONAL(DailyTonalReceiver::class, R.string.widget_daily_tonal, ::DailyTonalWidget),
    COMBO(ClassicComboReceiver::class, R.string.widget_combo, ::ClassicComboWidget),
    COMBO_TABLE(TableComboReceiver::class, R.string.widget_combo_table, ::TableComboWidget),
    COMBO_TONAL(SoftComboReceiver::class, R.string.widget_combo_tonal, ::SoftComboWidget),
    STRIP(StripWidgetReceiver::class, R.string.widget_strip, ::StripWidget),
    ALERTS(AlertsWidgetReceiver::class, R.string.widget_alerts, ::AlertsWidget),
}

object Widgets {
    suspend fun updateAll(context: Context) {
        WidgetKind.entries.forEach { k -> runCatching { k.widget().updateAll(context) } }
    }

    /** Every widget on the home screen, with its kind. */
    fun placed(context: Context): List<Pair<WidgetKind, Int>> {
        val m = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { k -> m.getAppWidgetIds(ComponentName(context, k.receiver.java)).map { k to it } }
    }

    /** IMS location ids that placed widgets show (resolving "current"). */
    fun placeIds(context: Context): Set<Int> = placed(context).mapNotNull { (_, id) -> placeIdFor(id) }.toSet()

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
        val id = placeIdFor(appWidgetId) ?: return WidgetData(null, null, emptyList(), showRefresh, null)
        val place = app.repo.placeFor(id, app.repo.ensurePlacesLoaded())
        var cached = app.repo.cachedForecast(id)
        if (cached?.value == null && app.repo.refreshForecast(id)) cached = app.repo.cachedForecast(id)
        val forecast = cached?.value
        val warnings = place?.let { app.repo.warningsFor(it.rid) }.orEmpty()
        return WidgetData(place, forecast, warnings, showRefresh, cached?.fetchedAt?.takeIf { it > 0 })
    }
}

data class WidgetData(
    val place: Place?,
    val forecast: Forecast?,
    val warnings: List<Warning>,
    val showRefresh: Boolean,
    /** When the forecast was last fetched (epoch millis). */
    val updated: Long?,
)

/** The widgets' "refresh now" button: re-fetch this widget's place, then redraw every widget. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        Widgets.placeIdFor(appWidgetId)?.let { app.repo.refreshForecast(it, force = true) }
        app.repo.refreshWarnings(force = true)
        Widgets.updateAll(context)
    }
}
