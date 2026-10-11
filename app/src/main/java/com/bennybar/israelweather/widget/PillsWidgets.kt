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
import com.bennybar.israelweather.ui.windShort

// "Classic" (mockup 1, Sunburst): apricot, cream and a burnt-orange highlight.
private val Apricot = Color(0xFFFFD8A8)
private val Cream = Color(0xFFFFF6EC)
private val Burnt = Color(0xFFB24A00)
private val Ink = Color(0xFF2A1B0E)
private val Muted = Color(0xFF5A3A1A)
private val Blue = Color(0xFF1554B8)

/** A pill: rounded tile, highlighted for "now"/"today"; content spread top to bottom. */
@Composable
private fun Pill(on: Boolean, base: Color, radius: Dp, modifier: GlanceModifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(if (on) Burnt else base).cornerRadius(radius),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// ---- Hourly 4×2: header + six hour pills with rain chance and amount ----

class HourlyWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (data.forecast?.currentHour(israelNow()) == null) return NoData(Apricot, Ink)
        Shell(Apricot, 28.dp, 10.dp) { ClassicHourlyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

/** Temperature, place, description · max/min · updated, and the optional refresh button. */
@Composable
private fun ClassicHeader(data: WidgetData) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val s = app.settings.value
    val now = israelNow()
    val h = f.currentHour(now) ?: return
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val en = english(context)
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(temp(h.temp, s), style = style(36.sp, Ink, bold = true), maxLines = 1)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(data.place?.name(en) ?: "", style = style(14.sp, Ink, bold = true), maxLines = 1)
            val line = listOfNotNull(
                WeatherCodes.describe(h.code, en),
                today?.let { "${temp(it.max, s)}/${temp(it.min, s)}" },
                updatedText(context, data.updated),
            ).joinToString(" · ")
            Text(line, style = style(11.sp, Muted), maxLines = 1)
        }
        if (data.showRefresh) {
            Spacer(GlanceModifier.width(6.dp))
            RefreshButton(Cream)
        }
    }
}

/** [ClassicHeader] over six hour pills. */
@Composable
fun ClassicHourlyBody(data: WidgetData, modifier: GlanceModifier) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val s = app.settings.value
    val now = israelNow()
    val unit = context.getString(R.string.unit_mm)
    Column(modifier) {
        ClassicHeader(data)
        Spacer(GlanceModifier.height(4.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            f.upcoming(6, now).forEachIndexed { i, hour ->
                val on = i == 0
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Pill(on, Cream, 18.dp, GlanceModifier.fillMaxSize().padding(vertical = 3.dp)) {
                        val fg = if (on) Color.White else Ink
                        val rainFg = if (on) Color.White else Blue
                        Text(if (on) context.getString(R.string.now) else hh(hour.time), style = style(11.sp, fg), maxLines = 1)
                        Image(ImageProvider(WeatherCodes.drawable(hour.code, WeatherCodes.isNight(hour.time))), WeatherCodes.describe(hour.code, english(LocalContext.current)), GlanceModifier.size(20.dp))
                        Text(temp(hour.temp, s), style = style(14.sp, fg, bold = true), maxLines = 1)
                        Text("${pct(hour.chance())}", style = style(11.sp, rainFg, bold = true), maxLines = 1)
                        Text("${mm(hour.rain)} $unit", style = style(9.sp, rainFg), maxLines = 1)
                        Text(windShort(hour.wind, hour.windDir, s), style = style(9.sp, if (on) Color.White else Muted), maxLines = 1)
                    }
                }
            }
        }
    }
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
