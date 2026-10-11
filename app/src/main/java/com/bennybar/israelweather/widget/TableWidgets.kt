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
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.temp

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

// ---- Hourly 4×2: "now" panel beside a table of the next 5 hours ----

class HourlyTableWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val context = LocalContext.current
        val f = data.forecast ?: return NoData(Card, Ink)
        val now = israelNow()
        val cur = f.currentHour(now) ?: return NoData(Card, Ink)
        val en = english(context)
        val s = app.settings.value
        val today = days(data, 1).firstOrNull()
        val rows = f.upcoming(6, now).drop(1)
        Shell(Card, 28.dp, 12.dp) {
            Row(GlanceModifier.fillMaxSize()) {
                Column(GlanceModifier.width(112.dp).fillMaxHeight().background(NowBg).cornerRadius(20.dp).padding(10.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Image(ImageProvider(WeatherCodes.drawable(cur.code, WeatherCodes.isNight(cur.time))), null, GlanceModifier.size(30.dp))
                        Spacer(GlanceModifier.defaultWeight())
                        if (data.showRefresh) RefreshButton(Card, 28.dp)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Text(temp(cur.temp, s), style = style(40.sp, Ink, bold = true), maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    data.place?.name(en)?.let { Text(it, style = style(13.sp, Ink, bold = true), maxLines = 1) }
                    val desc = WeatherCodes.describe(cur.code, en)
                    Text(
                        today?.let { "$desc · ${temp(it.max, s)}/${temp(it.min, s)}" } ?: desc,
                        style = style(11.sp, Muted), maxLines = 1,
                    )
                    updatedText(context, data.updated)?.let { Text(it, style = style(10.sp, Muted), maxLines = 1) }
                }
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Row(GlanceModifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val h = style(10.sp, Muted)
                        Cell(44.dp) { Text(context.getString(R.string.col_hour), style = h, maxLines = 1) }
                        Spacer(GlanceModifier.width(26.dp))
                        Cell(30.dp) { Text(context.getString(R.string.col_temp), style = h, maxLines = 1) }
                        Cell(38.dp) { Text(context.getString(R.string.col_chance), style = h, maxLines = 1) }
                        Text(context.getString(R.string.unit_mm), style = h, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    }
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Line)) {}
                    rows.forEach { hr ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                            Cell(44.dp) { Text(hh(hr.time), style = style(13.sp, Muted), maxLines = 1) }
                            Cell(26.dp) { Image(ImageProvider(WeatherCodes.drawable(hr.code, WeatherCodes.isNight(hr.time))), null, GlanceModifier.size(20.dp)) }
                            Cell(30.dp) { Text(temp(hr.temp, s), style = style(13.sp, Ink, bold = true), maxLines = 1) }
                            Cell(38.dp) { Text("${hr.chance()}%", style = style(13.sp, Blue, bold = true), maxLines = 1) }
                            Text(mm(hr.rain), style = style(13.sp, Blue), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

// ---- Daily 4×2: header + 5 rows with a min→max range bar ----

class DailyTableWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val context = LocalContext.current
        val list = days(data)
        if (list.isEmpty()) return NoData(Card, Ink)
        val en = english(context)
        val s = app.settings.value
        val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
        val hi = list.mapNotNull { it.max }.maxOrNull() ?: lo
        val span = (hi - lo).coerceAtLeast(1.0)
        // Glance has no percent sizes: the bar gets whatever the fixed columns leave over.
        val unit = context.getString(R.string.unit_mm)
        Shell(Card, 28.dp, 0.dp) {
            Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                list.forEach { d ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Cell(48.dp) { Text(dayLabel(context, d), style = style(13.sp, Ink, bold = true), maxLines = 1) }
                        Cell(30.dp) { Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(22.dp)) }
                        Cell(42.dp) { Text("${dayRainChance(data, d)}%", style = style(13.sp, Blue, bold = true), maxLines = 1) }
                        Cell(48.dp) { Text("${mm(dayRainMm(data, d))} $unit", style = style(11.sp, Blue), maxLines = 1) }
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
    }
}

class HourlyTableReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyTableWidget() }
class DailyTableReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTableWidget() }
