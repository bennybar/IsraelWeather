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
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.ui.dayName
import com.bennybar.israelweather.ui.hh
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.warningLabelRes
import kotlin.math.roundToInt

// The 4×1 widgets keep the "Sunburst" palette.
private val Cream = Color(0xFFFFF6EC)
private val Peach = Color(0xFFFFE9D2)
private val Burnt = Color(0xFFB24A00)
private val Ink = Color(0xFF2A1B0E)
private val Blue = Color(0xFF1554B8)
private val Lemon = Color(0xFFFFE16B)
private val LemonInk = Color(0xFF3D2E00)

@Composable
private fun Pill(on: Boolean, base: Color, radius: Dp, modifier: GlanceModifier, content: @Composable () -> Unit) {
    Column(
        modifier.background(if (on) Burnt else base).cornerRadius(radius),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// ---- Daily 4×1: compact pill strip ----

class StripWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) = StripContent(data)
}

@Composable
private fun StripContent(data: WidgetData) {
    val context = LocalContext.current
    val list = days(data)
    if (list.isEmpty()) return NoData(Peach, Ink)
    val s = app.settings.value
    Shell(Peach, 40.dp, 8.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            list.forEachIndexed { i, d ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)) {
                    Pill(i == 0, Cream, 32.dp, GlanceModifier.fillMaxSize()) {
                        val fg = if (i == 0) Color.White else Ink
                        Text(dayLabel(context, d, short = true), style = style(11.sp, fg, bold = true), maxLines = 1)
                        Image(ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, english(LocalContext.current)), GlanceModifier.size(18.dp))
                        Text("${temp(d.max, s)} ${temp(d.min, s)}", style = style(12.sp, fg, bold = true), maxLines = 1)
                        // Rain: chance · mm (the day's highest hourly chance and its total).
                        Text(
                            "${pct(dayRainChance(data, d))} · ${mm(dayRainMm(data, d))}",
                            style = style(9.sp, if (i == 0) Color.White else Blue, bold = true), maxLines = 1,
                        )
                    }
                }
            }
            if (data.showRefresh) {
                Spacer(GlanceModifier.width(4.dp))
                RefreshButton(Cream)
            }
        }
    }
}

class StripWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StripWidget() }
