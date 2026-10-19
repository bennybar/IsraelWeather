package com.bennybar.israelweather.widget

import android.app.LocaleManager
import android.graphics.Bitmap
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Day
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.Settings
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.WindUnit
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windValue

// Spectrum's "Bold" layout: the Tiles header, then one tile per day (3 or 4) led by a big high
// temperature, "▼ low" under it, the range bar, and wind | rain at the bottom. Today's tile is the
// light one. On a short (4×2) widget the type is smaller and wind's unit moves to its own line only
// when tall.

@Composable
internal fun SpectrumBold(data: WidgetData, p: Spectrum, now: Hour, list: List<Day>, lines: Boolean = false) {
    val context = LocalContext.current
    val s = app.settings.value
    val en = english(context)
    val size = LocalSize.current
    val tall = size.height >= 250.dp
    val compact = size.height < 205.dp
    val density = context.resources.displayMetrics.density
    val phoneLocale = context.getSystemService(LocaleManager::class.java).systemLocales.get(0)
    val rtl = TextUtils.getLayoutDirectionFromLocale(phoneLocale) == View.LAYOUT_DIRECTION_RTL
    val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
    val hi = (list.mapNotNull { it.max }.maxOrNull() ?: lo).coerceAtLeast(lo + 1)
    // The picker preview reports the minimum size; assume a typical 4-cell width there.
    val width = if (size.width < 300.dp) 360.dp else size.width
    val pad = 12.dp
    val gap = 6.dp
    val tile = (width - pad * 2 - gap * (list.size - 1)) / list.size
    val inner = tile - 16.dp
    // Size the columns' contents to the height they actually get (header and padding off the top),
    // instead of two fixed steps: on a ~195dp Samsung 4×2 the small set left big empty bands.
    // Text grows fully; icons and the bottom line a bit less, since they're also width-bound.
    val header = if (compact) 48.dp else 56.dp
    val columns = size.height - 20.dp - header - (if (tall) 8.dp else 3.dp)
    val f = (columns / 104.dp).coerceIn(1f, 1.5f)
    val fw = minOf(f, if (inner >= 72.dp) 1.3f else 1.15f)

    Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(p.bg).clickable(openApp())) {
        Box(
            GlanceModifier.fillMaxSize().then(p.bgImage?.let { GlanceModifier.background(ImageProvider(it)) } ?: GlanceModifier)
                .cornerRadius(28.dp).padding(horizontal = pad, vertical = 10.dp),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                ThreeDayHeader(data, p, now, s, en, 1f, compact)
                Spacer(GlanceModifier.height(if (tall) 8.dp else 3.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    list.forEachIndexed { i, d ->
                        if (i > 0) {
                            // "Bold lines": a hairline between days instead of separate tiles.
                            if (lines) {
                                Box(GlanceModifier.width(gap).fillMaxHeight().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Box(GlanceModifier.width(1.dp).fillMaxHeight().background(p.chipBorder)) {}
                                }
                            } else {
                                Spacer(GlanceModifier.width(gap))
                            }
                        }
                        val today = i == 0
                        val bar = rangeBar(
                            density, inner, p,
                            from = (((d.min ?: lo) - lo) / (hi - lo)).toFloat(),
                            to = (((d.max ?: hi) - lo) / (hi - lo)).toFloat(),
                            dot = if (today) now.temp?.let { ((it - lo) / (hi - lo)).toFloat() } else null,
                            rtl = rtl,
                            scale = 0.75f * fw,
                        )
                        BoldTile(data, p, d, today, s, en, tall, bar, density, roomy = inner >= 72.dp, framed = !lines, f = f, fw = fw)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.BoldTile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, tall: Boolean, bar: Bitmap, density: Float, roomy: Boolean, framed: Boolean, f: Float, fw: Float) {
    val context = LocalContext.current
    // Today's own text color only when it sits on today's tile.
    val ink = if (today && framed) p.todayInk ?: p.ink else p.ink
    val muted = if (today && framed) p.todayInk ?: p.muted else p.muted
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp).background(
            when {
                !framed -> ColorProvider(Color.Transparent) // "Bold lines": no tiles at all; today is marked by its accent name
                today -> p.today
                else -> p.soft
            },
        )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Day, with the icon at the far end.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (today && !framed) {
                // "Bold lines": today is a small filled pill in the accent color (no tile).
                val chipInk = ColorProvider(if (p.swatchRing.luminance() > 0.5f) Color(0xFF1D2548) else Color.White)
                Box(GlanceModifier.background(p.ring).cornerRadius(10.dp).padding(horizontal = if (roomy) 7.dp else 4.dp, vertical = 1.dp)) {
                    Text(dayLabel(context, d), style = style((if (roomy) 11.sp else 10.sp) * fw, chipInk, bold = true), maxLines = 1)
                }
            } else {
                Text(dayLabel(context, d), style = style(12.sp * fw, if (today) p.ring else ink, bold = true), maxLines = 1)
            }
            // Always some air between the day (or Today pill) and its icon.
            Spacer(GlanceModifier.width(6.dp))
            Spacer(GlanceModifier.defaultWeight())
            val iconSize = when {
                today && !framed -> (if (roomy) 18.dp else 16.dp) * fw
                else -> 20.dp * fw
            }
            Image(ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en), GlanceModifier.size(iconSize))
        }
        Text(temp(d.max, s), style = style(22.sp * f, ink, bold = true), maxLines = 1)
        Text("▾ " + temp(d.min, s), style = style(12.sp * f, muted, bold = true), maxLines = 1)
        Image(ImageProvider(bar), null, GlanceModifier.fillMaxWidth().padding(vertical = 2.dp * f).height(10.dp * fw), contentScale = ContentScale.FillBounds)
        // Wind | rain.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (w, dir) = dayWind(data, d)
            if (w != null && dir != null) {
                Image(ImageProvider(windArrow(density, p, dir)), null, GlanceModifier.size(10.dp * fw))
                Spacer(GlanceModifier.width(3.dp))
            }
            Column {
                Text(if (w != null) windValue(w, s) else "–", style = style(11.sp * fw, ink, bold = true), maxLines = 1)
                if (tall && w != null) {
                    Text(context.getString(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh), style = style(9.sp, muted), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            // The divider only where there's room for it (narrow tiles: wind and rain just sit apart).
            if (roomy) {
                Box(GlanceModifier.width(1.dp).height(if (tall) 18.dp else 10.dp * fw).background(p.chipBorder)) {}
                Spacer(GlanceModifier.defaultWeight())
            }
            if (roomy) {
                Image(ImageProvider(R.drawable.tonal_drop_white), null, GlanceModifier.size(9.dp * fw), colorFilter = ColorFilter.tint(muted))
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(pct(dayRainChance(data, d)), style = style(10.5.sp * fw, ink), maxLines = 1)
        }
    }
}
