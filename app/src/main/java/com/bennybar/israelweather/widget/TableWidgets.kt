package com.bennybar.israelweather.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.text.Text
import com.bennybar.israelweather.R
import com.bennybar.israelweather.data.WindUnit
import com.bennybar.israelweather.data.Hour
import androidx.compose.ui.unit.TextUnit
import androidx.glance.layout.RowScope
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windShort

// "Now + table" palette.
private val Card = Color.White
private val NowBg = Color(0xFFEEF3FF)
private val Ink = Color(0xFF1A1D26)
private val Muted = Color(0xFF4A5368)
private val Blue = Color(0xFF1F5FD1)
private val Line = Color(0xFFE6EAF2)
private val Amber = Color(0xFFF59E0B)

/** A fixed-width table cell, content aligned to the start edge. */
@Composable
private fun Cell(width: Dp, content: @Composable () -> Unit) {
    Box(GlanceModifier.width(width), contentAlignment = Alignment.CenterStart) { content() }
}

/** A table cell sharing the leftover width equally, so columns spread across the card. */
@Composable
private fun RowScope.WCell(content: @Composable () -> Unit) {
    Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.CenterStart) { content() }
}

/** Current rain (chance · mm), wind and humidity: fills the "now" panel's middle. */
@Composable
private fun NowStats(cur: Hour, size: TextUnit) {
    val context = LocalContext.current
    val s = app.settings.value
    val windUnit = context.getString(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh)
    Text("${pct(cur.chance())} · ${mm(cur.rain)} ${context.getString(R.string.unit_mm)}", style = style(size, Blue, bold = true), maxLines = 1)
    Text("${windShort(cur.wind, cur.windDir, s)} $windUnit", style = style(size, Muted), maxLines = 1)
}

// ---- Hourly body: "now" panel beside a table of the next hours ----

/**
 * The "now" panel + hourly table, without the card. [compact] (used by the combo) puts icon and
 * temperature on one line and shows 4 hours instead of 5, so it fits in about half a 4×3 card.
 * Callers check for missing data first; this draws nothing if the forecast is gone.
 */
@Composable
fun TableHourlyBody(data: WidgetData, modifier: GlanceModifier, compact: Boolean = false) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val now = israelNow()
    val cur = f.currentHour(now) ?: return
    val en = english(context)
    val s = app.settings.value
    val rows = f.upcoming(if (compact) 5 else 6, now).drop(1)
    val icon = ImageProvider(WeatherCodes.drawable(cur.code, WeatherCodes.isNight(cur.time)))
    Row(modifier) {
        Column(GlanceModifier.width(112.dp).fillMaxHeight().background(NowBg).cornerRadius(20.dp).padding(if (compact) 8.dp else 10.dp)) {
            if (compact) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(icon, null, GlanceModifier.size(24.dp))
                    Spacer(GlanceModifier.width(4.dp))
                    Text(temp(cur.temp, s), style = style(24.sp, Ink, bold = true), maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    if (data.showRefresh) RefreshButton(Card, 24.dp)
                }
                Spacer(GlanceModifier.defaultWeight())
                NowStats(cur, 10.sp)
                Spacer(GlanceModifier.defaultWeight())
                data.place?.name(en)?.let { Text(it, style = style(12.sp, Ink, bold = true), maxLines = 1) }
                updatedText(context, data.updated)?.let { Text(it, style = style(10.sp, Muted), maxLines = 1) }
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(icon, null, GlanceModifier.size(26.dp))
                    Spacer(GlanceModifier.defaultWeight())
                    if (data.showRefresh) RefreshButton(Card, 28.dp)
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(temp(cur.temp, s), style = style(34.sp, Ink, bold = true), maxLines = 1)
                NowStats(cur, 10.sp)
                Spacer(GlanceModifier.defaultWeight())
                data.place?.name(en)?.let { Text(it, style = style(13.sp, Ink, bold = true), maxLines = 1) }
                updatedText(context, data.updated)?.let { Text(it, style = style(10.sp, Muted), maxLines = 1) }
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val h = style(10.sp, Muted)
                Cell(42.dp) { Text(context.getString(R.string.col_hour), style = h, maxLines = 1) }
                WCell {}
                WCell { Text(context.getString(R.string.col_temp), style = h, maxLines = 1) }
                WCell { Text(context.getString(R.string.col_chance), style = h, maxLines = 1) }
                WCell { Text(context.getString(R.string.unit_mm), style = h, maxLines = 1) }
                WCell { Text(context.getString(R.string.col_wind), style = h, maxLines = 1) }
            }
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Line)) {}
            rows.forEach { hr ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Cell(42.dp) { Text(hh(hr.time), style = style(13.sp, Muted), maxLines = 1) }
                    WCell { Image(ImageProvider(WeatherCodes.drawable(hr.code, WeatherCodes.isNight(hr.time))), null, GlanceModifier.size(20.dp)) }
                    WCell { Text(temp(hr.temp, s), style = style(13.sp, Ink, bold = true), maxLines = 1) }
                    WCell { Text(pct(hr.chance()), style = style(13.sp, Blue, bold = true), maxLines = 1) }
                    WCell { Text(mm(hr.rain), style = style(12.sp, Blue), maxLines = 1) }
                    WCell { Text(windShort(hr.wind, hr.windDir, s), style = style(12.sp, Muted), maxLines = 1) }
                }
            }
        }
    }
}

// ---- Daily body: optional header + 5 rows with a min→max range bar ----

/**
 * The 5-day table, without the card. [compact] (used by the combo) drops the place/updated/refresh
 * header (the hourly part already shows them) and uses smaller icons so 5 rows fit in half a 4×3 card.
 * Callers check for missing data first; this draws nothing if there are no days.
 */
@Composable
fun TableDailyBody(data: WidgetData, modifier: GlanceModifier, compact: Boolean = false) {
    val context = LocalContext.current
    val list = days(data)
    if (list.isEmpty()) return
    val en = english(context)
    val s = app.settings.value
    val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
    val hi = list.mapNotNull { it.max }.maxOrNull() ?: lo
    val span = (hi - lo).coerceAtLeast(1.0)
    // Glance has no percent sizes: the bar gets whatever the fixed columns leave over.
    val unit = context.getString(R.string.unit_mm)
    Column(modifier) {
        if (!compact) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val title = listOfNotNull(data.place?.name(en), context.getString(R.string.days_5)).joinToString(" · ")
                Text(title, style = style(13.sp, Ink, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                updatedText(context, data.updated)?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(it, style = style(10.sp, Muted), maxLines = 1)
                }
                if (data.showRefresh) {
                    Spacer(GlanceModifier.width(8.dp))
                    RefreshButton(NowBg, 28.dp)
                }
            }
        }
        list.forEach { d ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Cell(44.dp) { Text(dayLabel(context, d), style = style(13.sp, Ink, bold = true), maxLines = 1) }
                Cell(26.dp) { Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(if (compact) 18.dp else 22.dp)) }
                Cell(36.dp) { Text("${pct(dayRainChance(data, d))}", style = style(13.sp, Blue, bold = true), maxLines = 1) }
                Cell(54.dp) { Text("${mm(dayRainMm(data, d))} $unit", style = style(11.sp, Blue), maxLines = 1) }
                val (w, dir) = dayWind(data, d)
                Cell(36.dp) { Text(windShort(w, dir, s), style = style(11.sp, Muted), maxLines = 1) }
                Box(GlanceModifier.width(28.dp), contentAlignment = Alignment.Center) {
                    Text(temp(d.min, s), style = style(13.sp, Muted), maxLines = 1)
                }
                // Min→max range as 10 equal segments (weights work at any widget width;
                // a Glance Row takes at most 10 children).
                val from = ((((d.min ?: lo) - lo) / span) * 10).toInt().coerceIn(0, 9)
                val to = ((((d.max ?: hi) - lo) / span) * 10).toInt().coerceIn(from, 9)
                Row(GlanceModifier.defaultWeight().height(6.dp).padding(end = 4.dp)) {
                    for (i in 0 until 10) {
                        Box(GlanceModifier.defaultWeight().fillMaxHeight().background(if (i in from..to) Amber else Line)) {}
                    }
                }
                Box(GlanceModifier.width(26.dp), contentAlignment = Alignment.Center) {
                    Text(temp(d.max, s), style = style(13.sp, Ink, bold = true), maxLines = 1)
                }
            }
        }
    }
}

// ---- Hourly 4×2 ----

class HourlyTableWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val f = data.forecast ?: return NoData(Card, Ink)
        f.currentHour(israelNow()) ?: return NoData(Card, Ink)
        Shell(Card, 28.dp, 12.dp) { TableHourlyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

// ---- Daily 4×2 ----

class DailyTableWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (days(data).isEmpty()) return NoData(Card, Ink)
        Shell(Card, 28.dp, 0.dp) {
            TableDailyBody(data, GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

// ---- Combo 4×3: hourly on top, 5-day table below ----

class TableComboWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val f = data.forecast ?: return NoData(Card, Ink)
        f.currentHour(israelNow()) ?: return NoData(Card, Ink)
        if (days(data).isEmpty()) return NoData(Card, Ink)
        Shell(Card, 28.dp, 12.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                // Glance weights are all equal, so the two halves split the height evenly.
                TableHourlyBody(data, GlanceModifier.fillMaxWidth().defaultWeight(), compact = true)
                Box(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Line)) {}
                }
                TableDailyBody(data, GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 4.dp), compact = true)
            }
        }
    }
}

class HourlyTableReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyTableWidget() }
class DailyTableReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTableWidget() }
class TableComboReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TableComboWidget() }
