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

// "Quiet week" and "Ranges": two calm daily widgets, each in lavender or orange (per-widget setting).

private data class Palette(
    val bg: Color,
    val today: Color,
    val ink: Color,
    val muted: Color,
    val rain: Color,
    val button: Color,
    val track: Color,
    val fill: Color,
)

private val Lavender = Palette(
    bg = Color(0xFFE6DCF7), today = Color(0xFFF7F3FD), ink = Color(0xFF1E1633), muted = Color(0xFF5B5072),
    rain = Color(0xFF2F5FC8), button = Color(0xFFF4F0FB), track = Color(0xFFD5C9EE), fill = Color(0xFF7259CC),
)

private val Orange = Palette(
    bg = Color(0xFFFFE3C4), today = Color(0xFFFFF7EE), ink = Color(0xFF2A1B0E), muted = Color(0xFF6A4A2A),
    rain = Color(0xFF1554B8), button = Color(0xFFFFF6EC), track = Color(0xFFF7CDA0), fill = Color(0xFFD5620B),
)

private fun palette(data: WidgetData) = if (data.color == WIDGET_COLOR_ORANGE) Orange else Lavender

/** Place, "Updated 16:05" and the optional refresh button. */
@Composable
private fun WeekHeader(data: WidgetData, p: Palette) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(data.place?.name(english(context)) ?: "", style = style(15.sp, p.ink, bold = true), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        updatedText(context, data.updated)?.let {
            Spacer(GlanceModifier.width(8.dp))
            Text(it, style = style(10.sp, p.muted), maxLines = 1)
        }
        if (data.showRefresh) {
            Spacer(GlanceModifier.width(8.dp))
            RefreshButton(p.button, 28.dp)
        }
    }
}

// ---- Quiet week 4×2: five open columns, a soft highlight on today, big highs ----

class QuietWeekWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val p = palette(data)
        val list = days(data)
        if (list.isEmpty()) return NoData(p.bg, p.ink)
        val context = LocalContext.current
        val s = app.settings.value
        val tall = LocalSize.current.height >= 210.dp
        Shell(p.bg, 28.dp, 10.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                WeekHeader(data, p)
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    list.forEachIndexed { i, d ->
                        val on = i == 0
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp)
                                .background(if (on) p.today else p.bg).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dayLabel(context, d), style = style(if (tall) 14.sp else 12.sp, if (on) p.ink else p.muted, bold = on), maxLines = 1)
                            Spacer(GlanceModifier.height(if (tall) 6.dp else 3.dp))
                            Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(if (tall) 30.dp else 24.dp))
                            Spacer(GlanceModifier.height(if (tall) 6.dp else 3.dp))
                            Text(temp(d.max, s), style = style(if (tall) 24.sp else 20.sp, p.ink, bold = true), maxLines = 1)
                            Text(temp(d.min, s), style = style(if (tall) 15.sp else 13.sp, p.muted), maxLines = 1)
                            Spacer(GlanceModifier.height(if (tall) 4.dp else 2.dp))
                            Text(pct(dayRainChance(data, d)), style = style(if (tall) 13.sp else 12.sp, p.rain, bold = true), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---- Ranges 4×2: one row per day, min → max on a shared scale ----

class RangesWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val p = palette(data)
        val list = days(data)
        if (list.isEmpty()) return NoData(p.bg, p.ink)
        val context = LocalContext.current
        val s = app.settings.value
        val tall = LocalSize.current.height >= 210.dp
        val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
        val hi = list.mapNotNull { it.max }.maxOrNull() ?: lo
        val span = (hi - lo).coerceAtLeast(1.0)
        val text = if (tall) 15.sp else 13.sp
        Shell(p.bg, 28.dp, 12.dp) {
            Column(GlanceModifier.fillMaxSize()) {
                WeekHeader(data, p)
                Spacer(GlanceModifier.height(4.dp))
                list.forEachIndexed { i, d ->
                    Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(GlanceModifier.width(48.dp)) {
                            Text(dayLabel(context, d), style = style(text, p.ink, bold = i == 0), maxLines = 1)
                        }
                        Box(GlanceModifier.width(28.dp), contentAlignment = Alignment.Center) {
                            Image(ImageProvider(WeatherCodes.drawable(d.code)), null, GlanceModifier.size(if (tall) 24.dp else 20.dp))
                        }
                        Box(GlanceModifier.width(34.dp), contentAlignment = Alignment.Center) {
                            Text(temp(d.min, s), style = style(text, p.muted), maxLines = 1)
                        }
                        // Min→max as 10 equal segments: Glance has no percent widths, and a Row takes at most 10 children.
                        val from = ((((d.min ?: lo) - lo) / span) * 10).toInt().coerceIn(0, 9)
                        val to = ((((d.max ?: hi) - lo) / span) * 10).toInt().coerceIn(from, 9)
                        Row(GlanceModifier.defaultWeight().height(if (tall) 8.dp else 6.dp).cornerRadius(4.dp)) {
                            for (j in 0 until 10) {
                                Box(GlanceModifier.defaultWeight().fillMaxHeight().background(if (j in from..to) p.fill else p.track)) {}
                            }
                        }
                        Box(GlanceModifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(temp(d.max, s), style = style(text, p.ink, bold = true), maxLines = 1)
                        }
                        if (data.showWind) {
                            val (w, dir) = dayWind(data, d)
                            Box(GlanceModifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(windShort(w, dir, s), style = style(if (tall) 13.sp else 12.sp, p.muted), maxLines = 1)
                            }
                        }
                        Box(GlanceModifier.width(40.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(pct(dayRainChance(data, d)), style = style(if (tall) 13.sp else 12.sp, p.rain, bold = true), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

class QuietWeekReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuietWeekWidget() }
class RangesReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RangesWidget() }
