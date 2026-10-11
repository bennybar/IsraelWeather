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

/** Rain chance and amount on one line (compact pills in the combo). */
@Composable
private fun RainLine(chance: Int?, amount: Double?, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(pct(chance), style = style(10.sp, color, bold = true), maxLines = 1)
        // No unit here: with readable text sizes it didn't fit the compact pills.
        Text(" · ${mm(amount)}", style = style(9.sp, color), maxLines = 1)
    }
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
private fun ClassicHeader(data: WidgetData, compact: Boolean = false) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val s = app.settings.value
    val now = israelNow()
    val h = f.currentHour(now) ?: return
    val today = f.daily.firstOrNull { it.localDate == now.toLocalDate() }
    val en = english(context)
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(temp(h.temp, s), style = style(if (compact) 30.sp else 36.sp, Ink, bold = true), maxLines = 1)
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
            if (compact) RefreshButton(Cream, 28.dp) else RefreshButton(Cream)
        }
    }
}

/** [ClassicHeader] (when [showHeader]) over six hour pills; [compact] folds rain % and mm onto one line. */
@Composable
fun ClassicHourlyBody(data: WidgetData, modifier: GlanceModifier, showHeader: Boolean = true, compact: Boolean = false) {
    val context = LocalContext.current
    val f = data.forecast ?: return
    val s = app.settings.value
    val now = israelNow()
    val unit = context.getString(R.string.unit_mm)
    Column(modifier) {
        if (showHeader) {
            ClassicHeader(data)
            Spacer(GlanceModifier.height(4.dp))
        }
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            f.upcoming(6, now).forEachIndexed { i, hour ->
                val on = i == 0
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Pill(on, Cream, 18.dp, GlanceModifier.fillMaxSize().padding(vertical = if (compact) 2.dp else 3.dp)) {
                        val fg = if (on) Color.White else Ink
                        val rainFg = if (on) Color.White else Blue
                        Text(if (on) context.getString(R.string.now) else hh(hour.time), style = style(if (compact) 10.sp else 11.sp, fg), maxLines = 1)
                        Image(ImageProvider(WeatherCodes.drawable(hour.code, WeatherCodes.isNight(hour.time))), null, GlanceModifier.size(if (compact) 18.dp else 20.dp))
                        Text(temp(hour.temp, s), style = style(if (compact) 13.sp else 14.sp, fg, bold = true), maxLines = 1)
                        if (compact) {
                            RainLine(hour.chance(), hour.rain, unit, rainFg)
                        } else {
                            Text("${pct(hour.chance())}", style = style(11.sp, rainFg, bold = true), maxLines = 1)
                            Text("${mm(hour.rain)} $unit", style = style(9.sp, rainFg), maxLines = 1)
                        }
                        Text(windShort(hour.wind, hour.windDir, s), style = style(9.sp, if (on) Color.White else Muted), maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- Daily 4×2: five day pills with a rain chip ----

class DailyWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (days(data).isEmpty()) return NoData(Cream, Ink)
        Shell(Cream, 28.dp, 8.dp) { ClassicDailyBody(data, GlanceModifier.fillMaxSize()) }
    }
}

/**
 * Five day pills; [showFooter] adds the refresh button and "Updated" line.
 * [compact] puts max/min and rain % / mm on one line each (for the combo).
 */
@Composable
fun ClassicDailyBody(data: WidgetData, modifier: GlanceModifier, showFooter: Boolean = true, compact: Boolean = false) {
    val context = LocalContext.current
    val list = days(data)
    val s = app.settings.value
    val unit = context.getString(R.string.unit_mm)
    Column(modifier) {
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            list.forEachIndexed { i, d ->
                val on = i == 0
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.5.dp)) {
                    Pill(on, Peach, if (compact) 18.dp else 22.dp, GlanceModifier.fillMaxSize().padding(vertical = if (compact) 3.dp else 6.dp)) {
                        val fg = if (on) Color.White else Ink
                        val rainFg = if (on) Color.White else Blue
                        val minFg = if (on) Peach else Muted
                        val (w, dir) = dayWind(data, d)
                        val chipBg = GlanceModifier.background(if (on) BurntDark else Cream).cornerRadius(10.dp)
                        if (compact) {
                            Text(dayLabel(context, d, short = true), style = style(11.sp, fg, bold = true), maxLines = 1)
                            Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(18.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(temp(d.max, s), style = style(13.sp, fg, bold = true), maxLines = 1)
                                Text(" ${temp(d.min, s)}", style = style(11.sp, minFg), maxLines = 1)
                            }
                            Text(windShort(w, dir, s), style = style(9.sp, if (on) Color.White else Muted), maxLines = 1)
                            Box(chipBg.padding(horizontal = 5.dp, vertical = 1.dp)) {
                                RainLine(dayRainChance(data, d), dayRainMm(data, d), unit, rainFg)
                            }
                        } else {
                            Text(dayLabel(context, d, short = true), style = style(12.sp, fg, bold = true), maxLines = 1)
                            Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(24.dp))
                            Text(temp(d.max, s), style = style(16.sp, fg, bold = true), maxLines = 1)
                            Text(temp(d.min, s), style = style(12.sp, minFg), maxLines = 1)
                            Text(windShort(w, dir, s), style = style(10.sp, if (on) Color.White else Muted), maxLines = 1)
                            Spacer(GlanceModifier.height(2.dp))
                            Column(
                                chipBg.padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("${pct(dayRainChance(data, d))}", style = style(11.sp, rainFg, bold = true), maxLines = 1)
                                Text("${mm(dayRainMm(data, d))} $unit", style = style(9.sp, rainFg), maxLines = 1)
                            }
                        }
                    }
                }
            }
            if (showFooter && data.showRefresh) {
                Box(GlanceModifier.fillMaxHeight().padding(start = 2.dp), contentAlignment = Alignment.TopCenter) { RefreshButton(Peach, 28.dp) }
            }
        }
        if (showFooter) {
            updatedText(context, data.updated)?.let {
                Text(it, style = style(9.sp, Muted), maxLines = 1, modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp))
            }
        }
    }
}

// ---- Classic combo 4×3: one header, the six hour pills, then the five day pills on a cream panel ----

class ClassicComboWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        if (data.forecast?.currentHour(israelNow()) == null || days(data).isEmpty()) return NoData(Apricot, Ink)
        Shell(Apricot, 28.dp, 8.dp) {
            // Header outside the weighted halves, so hourly gets header + half (~55%) and daily the other half.
            Column(GlanceModifier.fillMaxSize()) {
                ClassicHeader(data, compact = true)
                Spacer(GlanceModifier.height(4.dp))
                ClassicHourlyBody(data, GlanceModifier.fillMaxWidth().defaultWeight(), showHeader = false, compact = true)
                Spacer(GlanceModifier.height(6.dp))
                Box(GlanceModifier.fillMaxWidth().defaultWeight().background(Cream).cornerRadius(20.dp).padding(4.dp)) {
                    ClassicDailyBody(data, GlanceModifier.fillMaxSize(), showFooter = false, compact = true)
                }
            }
        }
    }
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = HourlyWidget() }
class DailyWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DailyWidget() }
class ClassicComboReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = ClassicComboWidget() }
