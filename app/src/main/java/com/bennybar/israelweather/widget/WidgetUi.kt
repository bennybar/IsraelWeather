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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bennybar.israelweather.MainActivity
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Day
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.dayName
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.hhmm
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.warningLabelRes
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToInt

// "Sunburst" palette: apricot, cream and a burnt-orange highlight.
private val Apricot = Color(0xFFFFD8A8)
private val Cream = Color(0xFFFFF6EC)
private val Peach = Color(0xFFFFE9D2)
private val Burnt = Color(0xFFB24A00)
private val Sun = Color(0xFFFF9E3D)
private val Ink = Color(0xFF2A1B0E)
private val Muted = Color(0xFF5A3A1A)
private val Lemon = Color(0xFFFFE16B)
private val LemonInk = Color(0xFF3D2E00)
private val Blue = Color(0xFF1554B8)

private fun style(size: TextUnit, color: Color, bold: Boolean = false) =
    TextStyle(fontSize = size, color = ColorProvider(color), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun english(context: Context) = context.resources.configuration.locales[0].language == "en"

private val openApp get() = actionStartActivity<MainActivity>()

@Composable
private fun Shell(bg: Color, radius: Dp, padding: Dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(bg).cornerRadius(radius).padding(padding).clickable(openApp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun NoData(bg: Color) {
    Shell(bg, 28.dp, 16.dp) {
        Text(LocalContext.current.getString(R.string.widget_open_app), style = style(14.sp, Ink))
    }
}

/** Optional "refresh now" button (per-widget setting). */
@Composable
private fun RefreshButton() {
    Box(
        GlanceModifier.size(32.dp).background(Cream).cornerRadius(16.dp).clickable(actionRunCallback<RefreshCallback>()),
        contentAlignment = Alignment.Center,
    ) { Image(ImageProvider(R.drawable.ic_refresh), LocalContext.current.getString(R.string.refresh_now), GlanceModifier.size(18.dp)) }
}

/** Tiny "updated 16:05" line for the larger widgets. */
@Composable
private fun UpdatedText(updated: Long?, color: Color) {
    if (updated == null) return
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(updated), IsraelZone)
    Text(LocalContext.current.getString(R.string.updated_at, hhmm(t)), style = style(9.sp, color), maxLines = 1)
}

/** A pill: rounded tile, highlighted for "now"/"today". */
@Composable
private fun Pill(on: Boolean, base: Color, radius: Dp, modifier: GlanceModifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(if (on) Burnt else base).cornerRadius(radius),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// ---- Hourly 4×2: now + the next hours ----

class HourlyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Widgets.load(context, id)
        provideContent { HourlyContent(data) }
    }
}

@Composable
private fun HourlyContent(data: WidgetData) {
    val context = LocalContext.current
    val f = data.forecast ?: return NoData(Apricot)
    val s = app.settings.value
    val now = israelNow()
    val h = f.currentHour(now) ?: return NoData(Apricot)
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val en = english(context)
    Shell(Apricot, 28.dp, 14.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(temp(h.temp, s), style = style(46.sp, Ink, bold = true))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.place?.name(en) ?: "", style = style(15.sp, Ink, bold = true), maxLines = 1)
                    Text(
                        WeatherCodes.describe(h.code, en) + (today?.let { " · ${temp(it.max, s)}/${temp(it.min, s)}" } ?: ""),
                        style = style(12.sp, Muted), maxLines = 1,
                    )
                    UpdatedText(data.updated, Muted)
                }
                Box(GlanceModifier.size(52.dp).background(Sun).cornerRadius(26.dp), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time))), null, GlanceModifier.size(36.dp))
                }
                if (data.showRefresh) {
                    Spacer(GlanceModifier.width(6.dp))
                    RefreshButton()
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth()) {
                f.upcoming(6, now).forEachIndexed { i, hour ->
                    Box(GlanceModifier.defaultWeight().padding(horizontal = 2.dp)) {
                        Pill(i == 0, Cream, 20.dp, GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            val fg = if (i == 0) Color.White else Ink
                            Text(if (i == 0) context.getString(R.string.now) else hh(hour.time), style = style(11.sp, fg))
                            Image(ImageProvider(WeatherCodes.drawable(hour.code, WeatherCodes.isNight(hour.time))), null, GlanceModifier.size(22.dp))
                            Text(temp(hour.temp, s), style = style(13.sp, fg, bold = true))
                        }
                    }
                }
            }
        }
    }
}

// ---- Daily 4×2: five tall day pills ----

class DailyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Widgets.load(context, id)
        provideContent { DailyContent(data) }
    }
}

private fun days(data: WidgetData): List<Day> {
    val today = israelNow().toLocalDate()
    return data.forecast?.daily.orEmpty().filter { !it.localDate.isBefore(today) }.take(5)
}

private fun rainChance(data: WidgetData, d: Day) =
    data.forecast?.hoursOn(d.localDate)?.mapNotNull { it.rainChance }?.maxOrNull() ?: 0.0

private fun dayLabel(context: Context, d: Day, short: Boolean): String =
    if (d.localDate == israelNow().toLocalDate()) context.getString(R.string.today)
    else dayName(d.localDate, context.resources.configuration.locales[0], short)

@Composable
private fun DailyContent(data: WidgetData) {
    val context = LocalContext.current
    val list = days(data)
    if (list.isEmpty()) return NoData(Cream)
    val s = app.settings.value
    Shell(Cream, 28.dp, 10.dp) {
        Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            list.forEachIndexed { i, d ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 3.dp)) {
                    Pill(i == 0, Peach, 26.dp, GlanceModifier.fillMaxSize().padding(vertical = 8.dp)) {
                        val fg = if (i == 0) Color.White else Ink
                        Text(dayLabel(context, d, short = true), style = style(13.sp, fg, bold = true))
                        Spacer(GlanceModifier.height(4.dp))
                        Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(30.dp))
                        val rain = rainChance(data, d)
                        Text(if (rain >= 20) "${rain.roundToInt()}%" else " ", style = style(11.sp, if (i == 0) Color.White else Blue, bold = true))
                        Text(temp(d.max, s), style = style(18.sp, fg, bold = true))
                        Text(temp(d.min, s), style = style(13.sp, if (i == 0) Peach else Muted))
                    }
                }
            }
            if (data.showRefresh) {
                Box(GlanceModifier.fillMaxHeight().padding(start = 3.dp), contentAlignment = Alignment.TopCenter) { RefreshButton() }
            }
        }
        Box(GlanceModifier.fillMaxWidth().padding(top = 3.dp, start = 6.dp)) { UpdatedText(data.updated, Muted) }
        }
    }
}

// ---- Daily 4×1: compact pill strip ----

class StripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Widgets.load(context, id)
        provideContent { StripContent(data) }
    }
}

@Composable
private fun StripContent(data: WidgetData) {
    val context = LocalContext.current
    val list = days(data)
    if (list.isEmpty()) return NoData(Peach)
    val s = app.settings.value
    Shell(Peach, 40.dp, 8.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            list.forEachIndexed { i, d ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Pill(i == 0, Cream, 32.dp, GlanceModifier.fillMaxSize()) {
                        val fg = if (i == 0) Color.White else Ink
                        Text(dayLabel(context, d, short = true), style = style(11.sp, fg, bold = true))
                        Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(20.dp))
                        Text("${temp(d.max, s)} ${temp(d.min, s)}", style = style(12.sp, fg, bold = true))
                    }
                }
            }
            if (data.showRefresh) {
                Spacer(GlanceModifier.width(4.dp))
                RefreshButton()
            }
        }
    }
}

// ---- Alerts & rain 4×1 ----

class AlertsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Widgets.load(context, id)
        provideContent { AlertsContent(data) }
    }
}

@Composable
private fun AlertsContent(data: WidgetData) {
    val context = LocalContext.current
    val f = data.forecast ?: return NoData(Lemon)
    val en = english(context)
    val warning = data.warnings.firstOrNull { !it.isFlash }
    val now = israelNow()
    val nextRain = f.hourly.firstOrNull { !it.time.isBefore(now.withMinute(0)) && it.time.isBefore(now.plusHours(48)) && (it.rainChance ?: 0.0) >= 40 }
    val title = warning?.let { listOfNotNull(it.typeName.get(en), context.getString(warningLabelRes(it))).joinToString(" · ") }
        ?: context.getString(R.string.alerts_none_short)
    val rainLine = nextRain?.let {
        val day = if (it.time.toLocalDate() == now.toLocalDate()) "" else dayName(it.time.toLocalDate(), context.resources.configuration.locales[0], short = true) + " "
        context.getString(R.string.rain_expected, day + hh(it.time), it.rainChance!!.roundToInt())
    } ?: context.getString(R.string.no_rain_48h)
    Shell(Lemon, 40.dp, 10.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(52.dp).background(LemonInk).cornerRadius(26.dp), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(if (warning != null) R.drawable.ic_warning else if (nextRain != null) R.drawable.w_rain else R.drawable.w_sun),
                    null, GlanceModifier.size(30.dp),
                )
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(title, style = style(15.sp, LemonInk, bold = true), maxLines = 1)
                Text(data.place?.name(en)?.let { "$it · $rainLine" } ?: rainLine, style = style(12.sp, LemonInk), maxLines = 2)
            }
            if (data.showRefresh) {
                Spacer(GlanceModifier.width(8.dp))
                RefreshButton()
            }
        }
    }
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
class DailyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyWidget() }
class StripWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StripWidget() }
class AlertsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AlertsWidget() }
