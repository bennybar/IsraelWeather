package com.bennybar.israelweather.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
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
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.hhmm
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windShort
import java.time.Instant
import java.time.LocalDateTime

// "Soft" style: lavender tonal card, a scalloped cookie for the current conditions.
private val Card = Color(0xFFEFE9F8)
private val Chip = Color(0xFFE1D8F2)
private val Deep = Color(0xFF4F3D85)
private val Ink = Color(0xFF1E1633)
private val Muted = Color(0xFF4A4060)
private val Rain = Color(0xFF2F5FC8)
private val Lilac = Color(0xFFD9CFF2)
private val BadgeLight = Color(0xFFF4F0FB)
private val BadgeDeep = Color(0xFF6A58A6)
private val Lavender = Color(0xFFE6DCF7)

class HourlyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (data.forecast?.currentHour() == null) return NoData(Card, Ink)
        Shell(Card, 28.dp, 12.dp) { SoftHourlyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

/** 4x2: header (now, place, summary, updated) over five day pills, like the Classic hourly, in lavender. */
class DailyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (days(data).isEmpty() || data.forecast?.currentHour() == null) return NoData(Lavender, Ink)
        Shell(Lavender, 28.dp, 10.dp) { SoftDayPills(data, GlanceModifier.fillMaxSize()) }
    }
}

@Composable
private fun SoftDayPills(data: WidgetData, modifier: GlanceModifier) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val s = app.settings.value
    val en = english(context)
    val now = f.currentHour() ?: return
    val list = days(data)
    // Home screens often give a 4x2 far more height than the picker preview: use it for bigger text.
    val tall = LocalSize.current.height >= 210.dp
    Column(modifier) {
        Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(temp(now.temp, s), style = style(36.sp, Ink, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(data.place?.name(en) ?: "", style = style(14.sp, Ink, bold = true), maxLines = 1)
                val line = listOfNotNull(
                    WeatherCodes.describe(now.code, en),
                    list.firstOrNull()?.let { "${temp(it.max, s)}/${temp(it.min, s)}" },
                    updatedText(context, data.updated),
                ).joinToString(" · ")
                Text(line, style = style(11.sp, Muted), maxLines = 1)
            }
            if (data.showRefresh) {
                Spacer(GlanceModifier.width(6.dp))
                RefreshButton(BadgeLight)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            list.forEachIndexed { i, d ->
                val on = i == 0
                val fg = if (on) Color.White else Ink
                val rainFg = if (on) Lilac else Rain
                val (w, dir) = dayWind(data, d)
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Column(
                        GlanceModifier.fillMaxSize().background(if (on) Deep else BadgeLight).cornerRadius(18.dp).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dayLabel(context, d, short = true), style = style(if (tall) 14.sp else 11.sp, fg, bold = true), maxLines = 1)
                        if (tall) Spacer(GlanceModifier.height(4.dp))
                        Image(ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, english(LocalContext.current)), GlanceModifier.size(if (tall) 30.dp else 20.dp))
                        if (tall) Spacer(GlanceModifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(temp(d.max, s), style = style(if (tall) 18.sp else 14.sp, fg, bold = true), maxLines = 1)
                            Text(" ${temp(d.min, s)}", style = style(if (tall) 14.sp else 11.sp, if (on) Lilac else Muted), maxLines = 1)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pct(dayRainChance(data, d)), style = style(if (tall) 13.sp else 11.sp, rainFg, bold = true), maxLines = 1)
                            Text(" · ${mm(dayRainMm(data, d))}", style = style(if (tall) 12.sp else 10.sp, rainFg), maxLines = 1)
                        }
                        Text(windShort(w, dir, s), style = style(if (tall) 12.sp else 10.sp, if (on) Color.White else Muted), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Cookie with the current conditions + 3x2 hour chips. */
@Composable
fun SoftHourlyBody(data: WidgetData, modifier: GlanceModifier) {
    val f = data.forecast ?: return
    val now = f.currentHour() ?: return
    val context = LocalContext.current
    val en = english(context)
    val s = app.settings.value
    val today = days(data, 1).firstOrNull()
    val hours = f.upcoming(7).drop(1)
    val sub = listOfNotNull(WeatherCodes.describe(now.code, en).ifEmpty { null }, updatedText(context, data.updated)).joinToString(" · ")

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(112.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.tonal_cookie), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), WeatherCodes.describe(now.code, english(LocalContext.current)), GlanceModifier.size(24.dp))
                Text(temp(now.temp, s), style = style(30.sp, Color.White, bold = true))
                if (today != null) Text("${temp(today.max, s)} / ${temp(today.min, s)}", style = style(11.sp, Color.White))
                Text(windShort(now.wind, now.windDir, s), style = style(10.sp, Color.White), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(data.place?.name(en) ?: "", style = style(14.sp, Ink, bold = true), maxLines = 1)
                    if (sub.isNotEmpty()) Text(sub, style = style(10.sp, Muted), maxLines = 1)
                }
                if (data.showRefresh) RefreshButton(Chip, 30.dp)
            }
            Spacer(GlanceModifier.height(6.dp))
            hours.chunked(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.height(5.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { j, h ->
                        if (j > 0) Spacer(GlanceModifier.width(5.dp))
                        SoftHourChip(h)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.SoftHourChip(h: Hour) {
    val s = app.settings.value
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().background(Chip).cornerRadius(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = ImageProvider(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time)))
        // Hour ("21"), icon and temp share one line so the chip stays 3 lines tall.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(h.time.hour.toString(), style = style(9.sp, Muted), maxLines = 1)
            Spacer(GlanceModifier.width(3.dp))
            Image(icon, WeatherCodes.describe(h.code, english(LocalContext.current)), GlanceModifier.size(14.dp))
            Spacer(GlanceModifier.width(2.dp))
            Text(temp(h.temp, s), style = style(12.sp, Ink, bold = true), maxLines = 1)
        }
        Text("${pct(h.chance())} · ${mm(h.rain)}", style = style(9.sp, Rain, bold = true), maxLines = 1)
        Text(windShort(h.wind, h.windDir, s), style = style(9.sp, Muted), maxLines = 1)
    }
}

class HourlyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyTonalWidget() }
class DailyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTonalWidget() }
