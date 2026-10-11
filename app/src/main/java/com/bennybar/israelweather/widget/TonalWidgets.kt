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

class HourlyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (data.forecast?.currentHour() == null) return NoData(Card, Ink)
        Shell(Card, 28.dp, 12.dp) { SoftHourlyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

class DailyTonalWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (days(data).isEmpty()) return NoData(Card, Ink)
        Shell(Card, 28.dp, 10.dp) { SoftDailyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

/** 4x3: the hourly card on top, the 5-day list below; refresh and "updated" appear once (in the hourly header). */
class SoftComboWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (data.forecast?.currentHour() == null || days(data).isEmpty()) return NoData(Card, Ink)
        Shell(Card, 28.dp, 10.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                SoftHourlyBody(data, GlanceModifier.fillMaxWidth().defaultWeight(), compact = true)
                Spacer(GlanceModifier.height(6.dp))
                SoftDailyBody(data, GlanceModifier.fillMaxWidth().defaultWeight(), compact = true)
            }
        }
    }
}

/** Cookie with the current conditions + 3x2 hour chips. [compact] shrinks everything to share a 4x3 card. */
@Composable
fun SoftHourlyBody(data: WidgetData, modifier: GlanceModifier, compact: Boolean = false) {
    val f = data.forecast ?: return
    val now = f.currentHour() ?: return
    val context = LocalContext.current
    val en = english(context)
    val s = app.settings.value
    val today = days(data, 1).firstOrNull()
    val hours = f.upcoming(7).drop(1)
    val sub = listOfNotNull(WeatherCodes.describe(now.code, en).ifEmpty { null }, updatedText(context, data.updated)).joinToString(" · ")

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(if (compact) 104.dp else 112.dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(R.drawable.tonal_cookie), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), null, GlanceModifier.size(if (compact) 22.dp else 24.dp))
                Text(temp(now.temp, s), style = style(if (compact) 28.sp else 30.sp, Color.White, bold = true))
                if (today != null) Text("${temp(today.max, s)} / ${temp(today.min, s)}", style = style(if (compact) 10.sp else 11.sp, Color.White))
                Text(windShort(now.wind, now.windDir, s), style = style(if (compact) 9.sp else 10.sp, Color.White), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (compact) {
                    Text(data.place?.name(en) ?: "", style = style(13.sp, Ink, bold = true), maxLines = 1)
                    Spacer(GlanceModifier.width(6.dp))
                    Text(sub, GlanceModifier.defaultWeight(), style = style(10.sp, Muted), maxLines = 1)
                } else {
                    Column(GlanceModifier.defaultWeight()) {
                        Text(data.place?.name(en) ?: "", style = style(14.sp, Ink, bold = true), maxLines = 1)
                        if (sub.isNotEmpty()) Text(sub, style = style(10.sp, Muted), maxLines = 1)
                    }
                }
                if (data.showRefresh) RefreshButton(Chip, if (compact) 24.dp else 30.dp)
            }
            Spacer(GlanceModifier.height(if (compact) 4.dp else 6.dp))
            hours.chunked(3).forEachIndexed { i, row ->
                if (i > 0) Spacer(GlanceModifier.height(5.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    row.forEachIndexed { j, h ->
                        if (j > 0) Spacer(GlanceModifier.width(5.dp))
                        SoftHourChip(h, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.SoftHourChip(h: Hour, compact: Boolean) {
    val s = app.settings.value
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().background(Chip).cornerRadius(if (compact) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = ImageProvider(WeatherCodes.drawable(h.code, WeatherCodes.isNight(h.time)))
        if (compact) {
            // Hour ("21"), icon and temp share one line so the chip stays 3 lines tall.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(h.time.hour.toString(), style = style(9.sp, Muted), maxLines = 1)
                Spacer(GlanceModifier.width(3.dp))
                Image(icon, null, GlanceModifier.size(14.dp))
                Spacer(GlanceModifier.width(2.dp))
                Text(temp(h.temp, s), style = style(12.sp, Ink, bold = true), maxLines = 1)
            }
            Text("${pct(h.chance())} · ${mm(h.rain)}", style = style(9.sp, Rain, bold = true), maxLines = 1)
            Text(windShort(h.wind, h.windDir, s), style = style(9.sp, Muted), maxLines = 1)
        } else {
            Text(hh(h.time), style = style(10.sp, Muted))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(icon, null, GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(3.dp))
                Text(temp(h.temp, s), style = style(13.sp, Ink, bold = true))
            }
            Text("${pct(h.chance())} · ${mm(h.rain)}", style = style(10.sp, Rain, bold = true), maxLines = 1)
            Text(windShort(h.wind, h.windDir, s), style = style(9.sp, Muted), maxLines = 1)
        }
    }
}

/** Five day rows (rain badge, wind, max/min). [compact] = shorter rows and no updated/refresh in today's row. */
@Composable
fun SoftDailyBody(data: WidgetData, modifier: GlanceModifier, compact: Boolean = false) {
    val list = days(data)
    val context = LocalContext.current
    val s = app.settings.value
    val updated = if (compact) null else data.updated?.let { hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), IsraelZone)) }
    val todayDate = israelNow().toLocalDate()

    Column(modifier) {
        list.forEachIndexed { i, d ->
            if (i > 0) Spacer(GlanceModifier.height(if (compact) 3.dp else 4.dp))
            val today = d.localDate == todayDate
            val fg = if (today) Color.White else Ink
            val rain = if (today) Color.White else Rain
            Row(
                GlanceModifier.fillMaxWidth().defaultWeight().background(if (today) Deep else Chip)
                    .cornerRadius(if (compact) 12.dp else 17.dp).padding(horizontal = if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(GlanceModifier.width(if (compact) 40.dp else 46.dp)) {
                    Text(dayLabel(context, d), style = style(if (compact) 11.sp else 13.sp, fg, bold = true), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(if (compact) 15.dp else 20.dp))
                Spacer(GlanceModifier.width(6.dp))
                Box(GlanceModifier.defaultWeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            GlanceModifier.background(if (today) BadgeDeep else BadgeLight).cornerRadius(10.dp)
                                .padding(horizontal = 7.dp, vertical = if (compact) 0.dp else 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(ImageProvider(if (today) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null, GlanceModifier.size(if (compact) 9.dp else 10.dp))
                            Spacer(GlanceModifier.width(3.dp))
                            Text("${pct(dayRainChance(data, d))} · ${mm(dayRainMm(data, d))}", style = style(if (compact) 9.sp else 10.sp, rain, bold = true), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        val (w, dir) = dayWind(data, d)
                        Text(windShort(w, dir, s), style = style(if (compact) 9.sp else 10.sp, if (today) Lilac else Muted, bold = true), maxLines = 1)
                        if (today && updated != null) {
                            Spacer(GlanceModifier.width(6.dp))
                            Text(updated, style = style(9.sp, Lilac), maxLines = 1)
                        }
                    }
                }
                Spacer(GlanceModifier.width(6.dp))
                Text("${temp(d.max, s)} / ${temp(d.min, s)}", style = style(if (compact) 11.sp else 13.sp, fg, bold = true), maxLines = 1)
                if (!compact && today && data.showRefresh) {
                    Spacer(GlanceModifier.width(6.dp))
                    RefreshButton(Lilac, 22.dp)
                }
            }
        }
    }
}

class HourlyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyTonalWidget() }
class DailyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTonalWidget() }
class SoftComboReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SoftComboWidget() }
