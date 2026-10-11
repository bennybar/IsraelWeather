package com.bennybar.israelweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bennybar.israelweather.MainActivity
import com.bennybar.israelweather.R
import com.bennybar.israelweather.data.Day
import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.dayName
import com.bennybar.israelweather.ui.hhmm
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Base for every home-screen widget: real data on the home screen, and the
 * same layout with [sampleData] as the preview in the launcher's widget picker.
 */
abstract class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    @Composable
    abstract fun Content(data: WidgetData)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Widgets.load(context, id)
        provideContent { Content(data) }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val data = sampleData(context)
        provideContent { Content(data) }
    }
}

// ---- text, shells, shared bits ----

fun style(size: TextUnit, color: Color, bold: Boolean = false) =
    TextStyle(fontSize = size, color = ColorProvider(color), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

fun english(context: Context) = context.resources.configuration.locales[0].language == "en"

/** The whole widget opens the app; inner buttons (refresh) take their own clicks. */
@Composable
fun Shell(bg: Color, radius: Dp, padding: Dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(bg).cornerRadius(radius).padding(padding).clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun NoData(bg: Color, ink: Color) {
    Shell(bg, 28.dp, 16.dp) {
        Text(LocalContext.current.getString(R.string.widget_open_app), style = style(14.sp, ink))
    }
}

/** Optional "refresh now" button (per-widget setting); [bg] is the circle behind the dark refresh glyph. */
@Composable
fun RefreshButton(bg: Color, size: Dp = 32.dp) {
    Box(
        GlanceModifier.size(size).background(bg).cornerRadius(size / 2).clickable(actionRunCallback<RefreshCallback>()),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(R.drawable.ic_refresh), LocalContext.current.getString(R.string.refresh_now), GlanceModifier.size(size * 0.55f)) }
}

/** "Updated 16:05". */
fun updatedText(context: Context, updated: Long?): String? = updated?.let {
    context.getString(R.string.updated_at, hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), IsraelZone)))
}

// ---- data helpers ----

/** The next [n] days starting today. */
fun days(data: WidgetData, n: Int = 5): List<Day> {
    val today = israelNow().toLocalDate()
    return data.forecast?.daily.orEmpty().filter { !it.localDate.isBefore(today) }.take(n)
}

fun dayLabel(context: Context, d: Day, short: Boolean = true): String =
    if (d.localDate == israelNow().toLocalDate()) context.getString(R.string.today)
    else dayName(d.localDate, context.resources.configuration.locales[0], short)

/** A day's rain chance: its highest hourly chance (%); null when IMS gives none (beyond ~5 days). */
fun dayRainChance(data: WidgetData, d: Day): Int? =
    data.forecast?.hoursOn(d.localDate)?.mapNotNull { it.rainChance }?.maxOrNull()?.roundToInt()

/** A day's rain amount: the sum of its hourly amounts (mm). */
fun dayRainMm(data: WidgetData, d: Day): Double =
    data.forecast?.hoursOn(d.localDate)?.sumOf { it.rain ?: 0.0 } ?: 0.0

/** A day's wind: its strongest hourly wind (km/h) and that hour's direction. */
fun dayWind(data: WidgetData, d: Day): Pair<Double?, Double?> {
    val h = data.forecast?.hoursOn(d.localDate)?.filter { it.wind != null }?.maxByOrNull { it.wind!! }
    return h?.wind to h?.windDir
}

fun Hour.chance(): Int? = rainChance?.roundToInt()

/** "70%", or "–" when IMS gives no chance (it gives amounts further ahead than chances). */
fun pct(v: Int?): String = v?.let { "$it%" } ?: "–"

/** "1.4", "12", "0" — one decimal below 10 mm. */
fun mm(v: Double?): String {
    val x = v ?: 0.0
    return when {
        x < 0.05 -> "0"
        x < 10 -> "%.1f".format(x)
        else -> x.roundToInt().toString()
    }
}

// ---- preview sample: a rainy day, so the widget picker shows rain % and mm ----

fun sampleData(context: Context): WidgetData {
    val now = israelNow().withMinute(0).withSecond(0).withNano(0)
    val temps = listOf(21, 20, 19, 18, 17, 16, 16, 15, 15, 14, 14, 14, 15, 17, 19, 20, 21, 21, 20, 19, 18, 17, 16, 16)
    val chances = listOf(40, 70, 80, 60, 30, 10, 5, 5, 10, 20, 30, 40, 50, 60, 70, 60, 40, 30, 20, 10, 10, 5, 5, 0)
    val amounts = listOf(0.2, 1.4, 2.6, 0.8, 0.1, 0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.6, 0.9, 1.2, 1.8, 1.1, 0.4, 0.2, 0.1, 0.0, 0.0, 0.0, 0.0, 0.0)
    val winds = listOf(14, 18, 22, 20, 16, 12, 10, 9, 8, 8, 9, 11, 13, 16, 19, 21, 20, 18, 15, 13, 12, 11, 10, 9)
    val codes = listOf(1230, 1560, 1140, 1560, 1230, 1230, 1220, 1220, 1220, 1230, 1540, 1560, 1140, 1140, 1140, 1560, 1230, 1220, 1220, 1220, 1250, 1250, 1250, 1250)
    val fmt = { t: LocalDateTime -> "%04d-%02d-%02d %02d:00".format(t.year, t.monthValue, t.dayOfMonth, t.hour) }
    val hourly = (0 until 24 * 5).map { i ->
        val t = now.plusHours(i.toLong())
        val k = i % 24
        // Rainy today and tomorrow, a few drops on day 3, then dry.
        val f = listOf(1.0, 1.6, 0.2, 0.0, 0.0).getOrElse(java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), t.toLocalDate()).toInt()) { 0.0 }
        Hour(fmt(t), if (f == 0.0) 1250 else codes[k], temps[k].toDouble(), (chances[k] * minOf(f, 1.15)).coerceAtMost(95.0), amounts[k] * f, rh = 70.0, wind = winds[k].toDouble(), windDir = 292.5)
    }
    val dayCodes = listOf(1140, 1140, 1220, 1250, 1250)
    val his = listOf(22.0, 19.0, 20.0, 23.0, 25.0)
    val los = listOf(15.0, 13.0, 12.0, 13.0, 15.0)
    val daily = (0 until 5).map { i ->
        Day(now.toLocalDate().plusDays(i.toLong()).toString(), dayCodes[i], los[i], his[i], 4.0)
    }
    val place = Place(0, context.getString(R.string.preview_place), context.getString(R.string.preview_place), 31.73, 34.74, 0)
    return WidgetData(place, Forecast(0, hourly = hourly, daily = daily), emptyList(), showRefresh = true, updated = System.currentTimeMillis())
}
