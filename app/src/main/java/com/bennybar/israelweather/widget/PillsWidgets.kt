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

// "Classic" (mockup 1, Sunburst): apricot, cream and a burnt-orange highlight.
private val Apricot = Color(0xFFFFD8A8)
private val Cream = Color(0xFFFFF6EC)
private val Peach = Color(0xFFFFE9D2)
private val Burnt = Color(0xFFB24A00)
private val BurntDark = Color(0xFF8F3B00)
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
    override fun Content(data: WidgetData) = HourlyContent(data)
}

@Composable
private fun HourlyContent(data: WidgetData) {
    val context = LocalContext.current
    val f = data.forecast ?: return NoData(Apricot, Ink)
    val s = app.settings.value
    val now = israelNow()
    val h = f.currentHour(now) ?: return NoData(Apricot, Ink)
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val en = english(context)
    val unit = context.getString(R.string.unit_mm)
    Shell(Apricot, 28.dp, 10.dp) {
        Column(GlanceModifier.fillMaxSize()) {
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
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                f.upcoming(6, now).forEachIndexed { i, hour ->
                    val on = i == 0
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                        Pill(on, Cream, 18.dp, GlanceModifier.fillMaxSize().padding(vertical = 5.dp)) {
                            val fg = if (on) Color.White else Ink
                            val rainFg = if (on) Color.White else Blue
                            Text(if (on) context.getString(R.string.now) else hh(hour.time), style = style(11.sp, fg), maxLines = 1)
                            Image(ImageProvider(WeatherCodes.drawable(hour.code, WeatherCodes.isNight(hour.time))), null, GlanceModifier.size(22.dp))
                            Text(temp(hour.temp, s), style = style(14.sp, fg, bold = true), maxLines = 1)
                            Text("${pct(hour.chance())}", style = style(11.sp, rainFg, bold = true), maxLines = 1)
                            Text("${mm(hour.rain)} $unit", style = style(9.sp, rainFg), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---- Daily 4×2: five day pills with a rain chip ----

class DailyWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) = DailyContent(data)
}

@Composable
private fun DailyContent(data: WidgetData) {
    val context = LocalContext.current
    val list = days(data)
    if (list.isEmpty()) return NoData(Cream, Ink)
    val s = app.settings.value
    val unit = context.getString(R.string.unit_mm)
    Shell(Cream, 28.dp, 8.dp) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                list.forEachIndexed { i, d ->
                    val on = i == 0
                    Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.5.dp)) {
                        Pill(on, Peach, 22.dp, GlanceModifier.fillMaxSize().padding(vertical = 6.dp)) {
                            val fg = if (on) Color.White else Ink
                            val rainFg = if (on) Color.White else Blue
                            Text(dayLabel(context, d, short = true), style = style(12.sp, fg, bold = true), maxLines = 1)
                            Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(26.dp))
                            Text(temp(d.max, s), style = style(16.sp, fg, bold = true), maxLines = 1)
                            Text(temp(d.min, s), style = style(12.sp, if (on) Peach else Muted), maxLines = 1)
                            Spacer(GlanceModifier.height(2.dp))
                            Column(
                                GlanceModifier.background(if (on) BurntDark else Cream).cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("${pct(dayRainChance(data, d))}", style = style(11.sp, rainFg, bold = true), maxLines = 1)
                                Text("${mm(dayRainMm(data, d))} $unit", style = style(9.sp, rainFg), maxLines = 1)
                            }
                        }
                    }
                }
                if (data.showRefresh) {
                    Box(GlanceModifier.fillMaxHeight().padding(start = 2.dp), contentAlignment = Alignment.TopCenter) { RefreshButton(Peach, 28.dp) }
                }
            }
            updatedText(context, data.updated)?.let {
                Text(it, style = style(9.sp, Muted), maxLines = 1, modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp))
            }
        }
    }
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
class DailyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyWidget() }
