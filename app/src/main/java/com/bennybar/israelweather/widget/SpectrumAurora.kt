package com.bennybar.israelweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Spectrum's "Aurora" layout: a big current temperature, place and condition, the weather icon on
// a soft blob; then one tile per day (3 or 4) with the icon beside the high/low and a wind | rain
// line. Best with the Aurora color (navy, periwinkle tiles, mint today), but works with any.

@Composable
internal fun SpectrumAurora(data: WidgetData, p: Spectrum, now: Hour, list: List<Day>) {
    val context = LocalContext.current
    val s = app.settings.value
    val en = english(context)
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    // The picker preview reports the minimum size; assume a typical 4-cell width there.
    val width = if (size.width < 300.dp) 360.dp else size.width
    val pad = 12.dp
    val gap = 6.dp
    val tile = (width - pad * 2 - gap * (list.size - 1)) / list.size
    val tall = size.height >= 250.dp
    // Narrow tiles (4 on a narrow phone) keep wind and rain but drop the "km/h".
    val roomy = tile >= 80.dp

    Box(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(p.bg).clickable(openApp())) {
        Box(
            GlanceModifier.fillMaxSize().then(p.bgImage?.let { GlanceModifier.background(ImageProvider(it)) } ?: GlanceModifier)
                .cornerRadius(28.dp).padding(pad),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                // Header; refresh floats in the top corner.
                Box(GlanceModifier.fillMaxWidth()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(temp(now.temp, s), style = style(if (tall) 56.sp else 48.sp, p.ring, bold = true), maxLines = 1)
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(ImageProvider(R.drawable.w_pin), null, GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(p.ink))
                                Spacer(GlanceModifier.width(3.dp))
                                Text(data.place?.name(en) ?: "", style = style(17.sp, p.ink, bold = true), maxLines = 1)
                            }
                            Text(WeatherCodes.describe(now.code, en), style = style(15.sp, p.muted, bold = true), maxLines = 1)
                        }
                        // The weather icon on a soft blob.
                        Box(GlanceModifier.size(78.dp, 64.dp), contentAlignment = Alignment.Center) {
                            Image(ImageProvider(blob(density, p.blob, 78, 64)), null, GlanceModifier.fillMaxSize())
                            Image(
                                ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), WeatherCodes.describe(now.code, en),
                                GlanceModifier.size(44.dp),
                            )
                        }
                        Spacer(GlanceModifier.width(30.dp))
                    }
                    if (data.showRefresh) {
                        Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                            RefreshButton(ColorProvider(Color.Transparent), 24.dp, touchMargin = 4.dp, glyph = p.ink)
                        }
                    }
                }
                Spacer(GlanceModifier.height(if (tall) 12.dp else 6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    list.forEachIndexed { i, d ->
                        if (i > 0) Spacer(GlanceModifier.width(gap))
                        AuroraTile(data, p, d, today = i == 0, s, en, roomy, tall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AuroraTile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, roomy: Boolean, tall: Boolean) {
    val context = LocalContext.current
    val ink = if (today) p.todayInk ?: p.ink else p.ink
    val muted = if (today) p.todayInk ?: p.muted else p.muted
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(20.dp).background(if (today) p.today else p.soft)
            .padding(horizontal = if (roomy) 8.dp else 6.dp, vertical = if (tall) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(context, d), style = style(13.sp, ink, bold = true), maxLines = 1)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en), GlanceModifier.size(if (roomy) 32.dp else 26.dp))
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.End) {
                Text(temp(d.max, s), style = style(if (roomy) 19.sp else 16.sp, ink, bold = true), maxLines = 1)
                Text(temp(d.min, s), style = style(if (roomy) 15.sp else 13.sp, muted, bold = true), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        // Wind | rain.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (w, _) = dayWind(data, d)
            Image(ImageProvider(R.drawable.w_wind), null, GlanceModifier.size(if (roomy) 12.dp else 10.dp), colorFilter = ColorFilter.tint(ink))
            Spacer(GlanceModifier.width(2.dp))
            val unit = if (roomy && w != null) " " + context.getString(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh) else ""
            Text((if (w != null) windValue(w, s) else "–") + unit, style = style(if (roomy) 10.5.sp else 9.5.sp, ink), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            // The divider only where there's room for it.
            if (roomy) {
                Box(GlanceModifier.width(1.dp).height(12.dp).background(muted)) {}
                Spacer(GlanceModifier.defaultWeight())
            }
            Image(ImageProvider(R.drawable.tonal_drop_white), null, GlanceModifier.size(if (roomy) 10.dp else 8.dp), colorFilter = ColorFilter.tint(ink))
            Spacer(GlanceModifier.width(2.dp))
            Text(pct(dayRainChance(data, d)), style = style(if (roomy) 10.5.sp else 9.5.sp, ink), maxLines = 1)
        }
    }
}

/** A soft, slightly wobbly blob ([w]×[h] dp) behind the big icon. */
private fun blob(density: Float, color: Color, w: Int, h: Int): Bitmap {
    val bw = (w * density).toInt()
    val bh = (h * density).toInt()
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val path = Path()
    val cx = bw / 2f
    val cy = bh / 2f
    for (i in 0..120) {
        val a = 2 * PI * i / 120
        val r = 1 + 0.035 * sin(3 * a + 0.6) + 0.02 * cos(5 * a)
        val x = (cx + cx * 0.92 * r * cos(a)).toFloat()
        val y = (cy + cy * 0.92 * r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() })
    return bmp
}
