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
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windShort

// "Soft" style: lavender tonal card, a scalloped cookie for the current conditions.
private val Deep = Color(0xFF4F3D85)
private val Ink = Color(0xFF1E1633)
private val Muted = Color(0xFF4A4060)
private val Rain = Color(0xFF2F5FC8)
private val Lilac = Color(0xFFD9CFF2)
private val BadgeLight = Color(0xFFF4F0FB)
private val Lavender = Color(0xFFE6DCF7)

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

class DailyTonalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyTonalWidget() }
