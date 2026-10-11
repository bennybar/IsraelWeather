package com.bennybar.israelweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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

enum class WidgetKind(val receiver: Class<out GlanceAppWidgetReceiver>, val title: Int) {
    HOURLY(HourlyWidgetReceiver::class.java, R.string.widget_hourly),
    DAILY(DailyWidgetReceiver::class.java, R.string.widget_daily),
    STRIP(StripWidgetReceiver::class.java, R.string.widget_strip),
    ALERTS(AlertsWidgetReceiver::class.java, R.string.widget_alerts),
}

object Widgets {
    suspend fun updateAll(context: Context) {
        runCatching {
            HourlyWidget().updateAll(context)
            DailyWidget().updateAll(context)
            StripWidget().updateAll(context)
            AlertsWidget().updateAll(context)
        }
    }

    /** Every widget on the home screen, with its kind. */
    fun placed(context: Context): List<Pair<WidgetKind, Int>> {
        val m = AppWidgetManager.getInstance(context)
        return WidgetKind.entries.flatMap { k -> m.getAppWidgetIds(ComponentName(context, k.receiver)).map { k to it } }
    }

    /** IMS location ids that placed widgets show (resolving "current"). */
    fun placeIds(context: Context): Set<Int> = placed(context).mapNotNull { (_, id) -> placeIdFor(id) }.toSet()

    fun requestPin(context: Context, kind: WidgetKind) {
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, kind.receiver), null, null)
    }

    fun placeIdFor(appWidgetId: Int): Int? {
        val s = app.settings.value
        return app.settings.widgetPlace(appWidgetId)?.toIntOrNull()
            ?: s.currentPlace.takeIf { s.useLocation }
            ?: s.places.firstOrNull()
            ?: s.currentPlace
    }

    suspend fun load(context: Context, glanceId: GlanceId): WidgetData {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val showRefresh = app.settings.widgetShowRefresh(appWidgetId)
        val id = placeIdFor(appWidgetId) ?: return WidgetData(null, null, emptyList(), showRefresh, null)
        val place = app.repo.ensurePlacesLoaded().firstOrNull { it.id == id }
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
