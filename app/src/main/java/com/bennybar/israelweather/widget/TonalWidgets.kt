package com.bennybar.israelweather.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

class HourlyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val f = data.forecast ?: return NoData(Card, Ink)
        val context = LocalContext.current
        val en = english(context)
        val s = app.settings.value
        val now = f.currentHour() ?: return NoData(Card, Ink)
        val today = days(data, 1).firstOrNull()
        val hours = f.upcoming(7).drop(1)
        val sub = listOfNotNull(WeatherCodes.describe(now.code, en).ifEmpty { null }, updatedText(context, data.updated)).joinToString(" · ")

        Shell(Card, 28.dp, 12.dp) {
            Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(130.dp), contentAlignment = Alignment.Center) {
                    Image(ImageProvider(R.drawable.tonal_cookie), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), null, GlanceModifier.size(28.dp))
                        Text(temp(now.temp, s), style = style(34.sp, Color.White, bold = true))
                        if (today != null) Text("${temp(today.max, s)} / ${temp(today.min, s)}", style = style(11.sp, Color.White))
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
                                HourChip(h)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun androidx.glance.layout.RowScope.HourChip(h: Hour) {
        val s = app.settings.value
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight().background(Chip).cornerRadius(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(hh(h.time), style = style(10.sp, Muted))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time))), null, GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(3.dp))
                Text(temp(h.temp, s), style = style(13.sp, Ink, bold = true))
            }
            Text("${pct(h.chance())} · ${mm(h.rain)}", style = style(10.sp, Rain, bold = true), maxLines = 1)
        }
    }
}

class DailyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val list = days(data)
        if (list.isEmpty()) return NoData(Card, Ink)
        val context = LocalContext.current
        val s = app.settings.value
        val unit = context.getString(R.string.unit_mm)
        val updated = data.updated?.let { hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), IsraelZone)) }
        val todayDate = israelNow().toLocalDate()

        Shell(Card, 28.dp, 10.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                list.forEachIndexed { i, d ->
                    if (i > 0) Spacer(GlanceModifier.height(4.dp))
                    val today = d.localDate == todayDate
                    val fg = if (today) Color.White else Ink
                    val rain = if (today) Color.White else Rain
                    Row(
                        GlanceModifier.fillMaxWidth().defaultWeight().background(if (today) Deep else Chip).cornerRadius(17.dp).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(GlanceModifier.width(46.dp)) {
                            Text(dayLabel(context, d), style = style(13.sp, fg, bold = true), maxLines = 1)
                            if (today && updated != null) Text(updated, style = style(9.sp, Lilac), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(20.dp))
                        Spacer(GlanceModifier.width(6.dp))
                        Box(GlanceModifier.defaultWeight()) {
                            Row(
                                GlanceModifier.background(if (today) BadgeDeep else BadgeLight).cornerRadius(10.dp).padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(ImageProvider(if (today) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null, GlanceModifier.size(10.dp))
                                Spacer(GlanceModifier.width(3.dp))
                                Text("${pct(dayRainChance(data, d))} · ${mm(dayRainMm(data, d))} $unit", style = style(10.sp, rain, bold = true), maxLines = 1)
                            }
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Text("${temp(d.max, s)} / ${temp(d.min, s)}", style = style(13.sp, fg, bold = true), maxLines = 1)
                        if (today && data.showRefresh) {
                            Spacer(GlanceModifier.width(6.dp))
                            RefreshButton(Lilac, 22.dp)
                        }
                    }
                }
            }
        }
    }
}

class HourlyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyTonalWidget() }
class DailyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTonalWidget() }
