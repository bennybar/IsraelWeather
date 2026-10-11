package com.bennybar.israelweather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import com.bennybar.israelweather.MainActivity
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.apparentTemp
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windShort
import java.time.Instant
import java.time.LocalDateTime
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.ui.hhmm

// "Spectrum": now on top, five days with cool→warm gradient bars (a dot marks the current
// temperature on today's bar) and rain chips that turn solid on rainy days.

private data class Spectrum(
    val bg: Color,
    /** Drawn background (gradient) instead of [bg], if any. */
    val bgImage: Int?,
    val today: Color,
    val ink: Color,
    val muted: Color,
    val button: Color,
    val track: Color,
    val ring: Color,
    val gradient: List<Color>,
    val chip: Color,
    val chipInk: Color,
    val chipStrong: Color,
    val chipStrongInk: Color,
    /** White drop on the light chips too (dark cards). */
    val whiteDrop: Boolean = false,
)

private val LavenderSpectrum = Spectrum(
    bg = Color(0xFFE6DCF7), bgImage = null, today = Color(0xFFF7F3FD), ink = Color(0xFF1E1633), muted = Color(0xFF5B5072),
    button = Color(0xFFF4F0FB), track = Color(0xFFD7CCEE), ring = Color(0xFF7259CC),
    gradient = listOf(Color(0xFF8FA8FF), Color(0xFFB48CFF), Color(0xFFFF9F6B)),
    chip = Color(0xFFF4F0FB), chipInk = Color(0xFF2F5FC8), chipStrong = Color(0xFF2F5FC8), chipStrongInk = Color.White,
)

private val OrangeSpectrum = Spectrum(
    bg = Color(0xFFFFE3C4), bgImage = null, today = Color(0xFFFFF7EE), ink = Color(0xFF2A1B0E), muted = Color(0xFF6A4A2A),
    button = Color(0xFFFFF6EC), track = Color(0xFFF5CFA6), ring = Color(0xFFD5620B),
    gradient = listOf(Color(0xFF8FB4FF), Color(0xFFFFC07A), Color(0xFFF0703A)),
    chip = Color(0xFFFFF6EC), chipInk = Color(0xFF1554B8), chipStrong = Color(0xFF1554B8), chipStrongInk = Color.White,
)

private val TwilightSpectrum = Spectrum(
    bg = Color(0xFF3E2F7A), bgImage = R.drawable.twilight_bg, today = Color(0x1FFFFFFF), ink = Color(0xFFF3EFFF), muted = Color(0xFFC9BFEA),
    button = Color(0xFFD9CFF2), track = Color(0x24FFFFFF), ring = Color(0xFFB79CFF),
    gradient = listOf(Color(0xFF7FA2FF), Color(0xFFC9A8FF), Color(0xFFFFB38A)),
    chip = Color(0x1FFFFFFF), chipInk = Color(0xFFB9CCFF), chipStrong = Color(0xFF6F8BFF), chipStrongInk = Color.White,
    whiteDrop = true,
)

private fun spectrum(color: String) = when (color) {
    WIDGET_COLOR_ORANGE -> OrangeSpectrum
    WIDGET_COLOR_TWILIGHT -> TwilightSpectrum
    else -> LavenderSpectrum
}

/** Rainy enough for the solid chip. */
private const val RAINY_PCT = 30

class SpectrumWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val p = spectrum(data.color)
        val list = days(data)
        val now = data.forecast?.currentHour()
        if (list.isEmpty() || now == null) return NoData(p.bg, p.ink)
        val context = LocalContext.current
        val s = app.settings.value
        val en = english(context)
        val size = LocalSize.current
        val tall = size.height >= 210.dp
        val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
        val hi = (list.mapNotNull { it.max }.maxOrNull() ?: lo).coerceAtLeast(lo + 1)
        val rtl = context.resources.configuration.layoutDirection == LayoutDirection.RTL
        val text = if (tall) 15.sp else 14.sp
        // The bar takes the row's leftover width; its bitmap is drawn for that width so the round
        // ends and the dot aren't stretched. (The picker preview reports the minimum size, so
        // assume a typical 4-cell width there.) Fixed: card + row padding, day, icon, min, max, chip, wind.
        val fixed = 32.dp + 8.dp + 50.dp + 28.dp + 34.dp + 36.dp + 62.dp + (if (data.showWind) 42.dp else 0.dp)
        val layoutWidth = if (size.width < 300.dp) 360.dp else size.width
        val barWidth = (layoutWidth - fixed).coerceAtLeast(24.dp)

        val bgModifier = if (p.bgImage != null) GlanceModifier.background(ImageProvider(p.bgImage)) else GlanceModifier.background(p.bg)
        Box(
            GlanceModifier.fillMaxSize().then(bgModifier).cornerRadius(28.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(temp(now.temp, s), style = style(if (tall) 40.sp else 34.sp, p.ink, bold = true), maxLines = 1)
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(data.place?.name(en) ?: "", style = style(16.sp, p.ink, bold = true), maxLines = 1)
                        val line = listOfNotNull(
                            WeatherCodes.describe(now.code, en),
                            apparentTemp(now.temp, now.rh, now.wind)?.let { context.getString(R.string.feels_like, temp(it, s)).lowercaseFirstIfEnglish(en) },
                            data.updated?.let { hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), IsraelZone)) },
                        ).joinToString(" · ")
                        Text(line, style = style(11.sp, p.muted), maxLines = 1)
                    }
                    if (data.showRefresh) {
                        Spacer(GlanceModifier.width(6.dp))
                        RefreshButton(p.button, 30.dp)
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                list.forEachIndexed { i, d ->
                    val today = i == 0
                    Row(
                        GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(14.dp)
                            .background(if (today) p.today else Color.Transparent).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(GlanceModifier.width(50.dp)) {
                            Text(dayLabel(context, d), style = style(text, p.ink, bold = today), maxLines = 1)
                        }
                        Box(GlanceModifier.width(28.dp), contentAlignment = Alignment.Center) {
                            Image(
                                ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en),
                                GlanceModifier.size(if (tall) 24.dp else 22.dp),
                            )
                        }
                        Box(GlanceModifier.width(34.dp), contentAlignment = Alignment.Center) {
                            Text(temp(d.min, s), style = style(text, p.muted), maxLines = 1)
                        }
                        val bar = rangeBar(
                            context.resources.displayMetrics.density, barWidth, p,
                            from = (((d.min ?: lo) - lo) / (hi - lo)).toFloat(),
                            to = (((d.max ?: hi) - lo) / (hi - lo)).toFloat(),
                            dot = if (today) now.temp?.let { ((it - lo) / (hi - lo)).toFloat() } else null,
                            rtl = rtl,
                        )
                        Image(ImageProvider(bar), null, GlanceModifier.defaultWeight().height(14.dp), contentScale = ContentScale.FillBounds)
                        Box(GlanceModifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text(temp(d.max, s), style = style(text, p.ink, bold = true), maxLines = 1)
                        }
                        if (data.showWind) {
                            val (w, dir) = dayWind(data, d)
                            Box(GlanceModifier.width(42.dp), contentAlignment = Alignment.CenterEnd) {
                                Text(windShort(w, dir, s), style = style(12.sp, p.muted), maxLines = 1)
                            }
                        }
                        val chance = dayRainChance(data, d)
                        val strong = (chance ?: 0) >= RAINY_PCT
                        Box(GlanceModifier.width(62.dp), contentAlignment = Alignment.CenterEnd) {
                            Row(
                                GlanceModifier.background(if (strong) p.chipStrong else p.chip).cornerRadius(10.dp).padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    ImageProvider(if (strong || p.whiteDrop) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null,
                                    GlanceModifier.size(9.dp),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Text(pct(chance), style = style(12.sp, if (strong) p.chipStrongInk else p.chipInk, bold = true), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.lowercaseFirstIfEnglish(en: Boolean) = if (en) replaceFirstChar { it.lowercase() } else this

/**
 * One day's range as a bitmap: a rounded track, the min→max part filled with the
 * cool→warm gradient of the whole week's scale, and optionally a "now" dot.
 */
private fun rangeBar(density: Float, width: Dp, p: Spectrum, from: Float, to: Float, dot: Float?, rtl: Boolean): Bitmap {
    val w = (width.value * density).toInt().coerceAtLeast(1)
    val h = (14 * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val thick = 8 * density
    val top = (h - thick) / 2
    val dotR = 6 * density
    // Keep the dot inside the bitmap at the ends.
    val x0 = dotR
    val span = w - 2 * dotR
    fun x(f: Float) = x0 + span * (if (rtl) 1 - f.coerceIn(0f, 1f) else f.coerceIn(0f, 1f))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = p.track.toArgb()
    c.drawRoundRect(RectF(x0, top, x0 + span, top + thick), thick / 2, thick / 2, paint)
    val colors = p.gradient.map { it.toArgb() }.let { if (rtl) it.reversed() else it }.toIntArray()
    // Opaque again: the paint's alpha (Twilight's see-through track) would dim the gradient.
    paint.color = android.graphics.Color.BLACK
    paint.shader = LinearGradient(x0, 0f, x0 + span, 0f, colors, null, Shader.TileMode.CLAMP)
    val a = minOf(x(from), x(to))
    val b = maxOf(x(from), x(to))
    c.drawRoundRect(RectF(a, top, maxOf(b, a + thick), top + thick), thick / 2, thick / 2, paint)
    paint.shader = null
    if (dot != null) {
        val cx = x(dot)
        paint.color = p.ring.toArgb()
        c.drawCircle(cx, h / 2f, dotR, paint)
        paint.color = android.graphics.Color.WHITE
        c.drawCircle(cx, h / 2f, dotR - 3 * density, paint)
    }
    return bmp
}

class SpectrumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SpectrumWidget() }
