package com.bennybar.israelweather.widget

import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextUtils
import android.view.View
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
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.apparentTemp
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windShort
import com.bennybar.israelweather.ui.windValue
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.roundToInt
import com.bennybar.israelweather.data.IsraelZone
import com.bennybar.israelweather.ui.hhmm

// "Spectrum": now on top, five days with cool→warm gradient bars (a dot marks the current
// temperature on today's bar) and rain chips that turn solid on rainy days.

internal data class Spectrum(
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
    /** Hairline around the light chips, so they don't melt into today's highlight. */
    val chipBorder: Color,
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
    chip = Color(0xFFE6EBFC), chipBorder = Color(0xFFC3CEF4), chipInk = Color(0xFF2F5FC8), chipStrong = Color(0xFF2F5FC8), chipStrongInk = Color.White,
)

private val OrangeSpectrum = Spectrum(
    bg = Color(0xFFFFE3C4), bgImage = null, today = Color(0xFFFFF7EE), ink = Color(0xFF2A1B0E), muted = Color(0xFF6A4A2A),
    button = Color(0xFFFFF6EC), track = Color(0xFFF5CFA6), ring = Color(0xFFD5620B),
    gradient = listOf(Color(0xFF8FB4FF), Color(0xFFFFC07A), Color(0xFFF0703A)),
    chip = Color(0xFFE8EEFB), chipBorder = Color(0xFFC6D3F2), chipInk = Color(0xFF1554B8), chipStrong = Color(0xFF1554B8), chipStrongInk = Color.White,
)

private val TwilightSpectrum = Spectrum(
    bg = Color(0xFF3E2F7A), bgImage = R.drawable.twilight_bg, today = Color(0x1FFFFFFF), ink = Color(0xFFF3EFFF), muted = Color(0xFFC9BFEA),
    button = Color(0xFFD9CFF2), track = Color(0x24FFFFFF), ring = Color(0xFFB79CFF),
    gradient = listOf(Color(0xFF7FA2FF), Color(0xFFC9A8FF), Color(0xFFFFB38A)),
    chip = Color(0x2E8FA8FF), chipBorder = Color(0x5C8FA8FF), chipInk = Color(0xFFB9CCFF), chipStrong = Color(0xFF6F8BFF), chipStrongInk = Color.White,
    whiteDrop = true,
)

private val SkySpectrum = Spectrum(
    bg = Color(0xFFDCEBFA), bgImage = null, today = Color(0xFFF4F9FF), ink = Color(0xFF10243A), muted = Color(0xFF4E6178),
    button = Color(0xFFEEF5FD), track = Color(0xFFC7DAF0), ring = Color(0xFF2F6FD0),
    gradient = listOf(Color(0xFF7FB0F0), Color(0xFFA79AEE), Color(0xFFF2A07C)),
    chip = Color(0xFFE2EBFB), chipBorder = Color(0xFFBCCFF0), chipInk = Color(0xFF1F56B8), chipStrong = Color(0xFF1F56B8), chipStrongInk = Color.White,
)

private val MintSpectrum = Spectrum(
    bg = Color(0xFFDDF1E6), bgImage = null, today = Color(0xFFF4FBF7), ink = Color(0xFF10291C), muted = Color(0xFF4C6656),
    button = Color(0xFFEEF8F2), track = Color(0xFFC3E2D0), ring = Color(0xFF2E8A5C),
    gradient = listOf(Color(0xFF86B4EE), Color(0xFF9BCFB4), Color(0xFFF0A57E)),
    chip = Color(0xFFE2EEF9), chipBorder = Color(0xFFBED2EA), chipInk = Color(0xFF2A5DAE), chipStrong = Color(0xFF2F62B8), chipStrongInk = Color.White,
)

private val RoseSpectrum = Spectrum(
    bg = Color(0xFFF8E1E7), bgImage = null, today = Color(0xFFFFF5F7), ink = Color(0xFF34151F), muted = Color(0xFF75505C),
    button = Color(0xFFFDF0F3), track = Color(0xFFEFC9D3), ring = Color(0xFFC2416E),
    gradient = listOf(Color(0xFF93A9EE), Color(0xFFC79BE0), Color(0xFFF0907E)),
    chip = Color(0xFFE5EAFA), chipBorder = Color(0xFFC6D0F0), chipInk = Color(0xFF2E57AE), chipStrong = Color(0xFF3D5FBF), chipStrongInk = Color.White,
)

/**
 * Material You: the system's wallpaper-based tones (accent 1 for surfaces and accents, accent 2 for
 * secondary text). Rain stays blue and the bars keep their cool-to-warm ends, so meaning holds;
 * only the middle of the gradient follows the wallpaper.
 */
private fun wallpaperSpectrum(context: Context): Spectrum {
    fun c(id: Int) = Color(context.getColor(id))
    return Spectrum(
        bg = c(android.R.color.system_accent1_100), bgImage = null, today = c(android.R.color.system_accent1_10),
        ink = c(android.R.color.system_accent1_900), muted = c(android.R.color.system_accent2_700),
        button = c(android.R.color.system_accent1_50), track = c(android.R.color.system_accent1_200), ring = c(android.R.color.system_accent1_600),
        gradient = listOf(Color(0xFF8FA8FF), c(android.R.color.system_accent1_300), Color(0xFFFF9F6B)),
        chip = Color(0xFFE6EBFC), chipBorder = Color(0xFFC3CEF4), chipInk = Color(0xFF2F5FC8), chipStrong = Color(0xFF2F5FC8), chipStrongInk = Color.White,
    )
}

internal fun spectrum(color: String, context: Context) = when (color) {
    WIDGET_COLOR_ORANGE -> OrangeSpectrum
    WIDGET_COLOR_TWILIGHT -> TwilightSpectrum
    WIDGET_COLOR_SKY -> SkySpectrum
    WIDGET_COLOR_MINT -> MintSpectrum
    WIDGET_COLOR_ROSE -> RoseSpectrum
    WIDGET_COLOR_WALLPAPER -> wallpaperSpectrum(context)
    else -> LavenderSpectrum
}

/** Rainy enough for the solid chip. */
internal const val RAINY_PCT = 30

class SpectrumWidget : WeatherWidget() {
    @Composable
    override fun Content(data: WidgetData) {
        val p = spectrum(data.color, LocalContext.current)
        val threeDay = data.layout != SPECTRUM_LAYOUT_CLASSIC
        val list = days(data, spectrumDays(data.layout, data.dayCount))
        val now = data.forecast?.currentHour()
        if (list.isEmpty() || now == null) return NoData(p.bg, p.ink)
        if (threeDay) return SpectrumThreeDay(data, p, now, list)
        val context = LocalContext.current
        val s = app.settings.value
        val en = english(context)
        val size = LocalSize.current
        // Size from the room each day row gets: fewer days or a taller widget (4×3) mean bigger
        // text, icons, bars and pills, never more data. Columns widen less than the text (capped),
        // so the bars keep their room. A tall widget also gets a roomier header.
        val roomy = size.height >= 250.dp
        val baseHeader = if (roomy) 92.dp else 72.dp
        // One factor for everything (header, pills and rows grow together), measured against this
        // widget's own 5-day rows (capped at 36dp, so a tall 4×3 grows too): launchers' cell
        // heights differ (Samsung's 4×2 is ~25dp shorter than Pixel's). Kept moderate.
        val fiveDayRow = minOf((size.height - baseHeader - 12.dp) / 5, 36.dp)
        val roomPerRow = (size.height - baseHeader - 12.dp) / list.size
        val k = minOf(roomPerRow / fiveDayRow, roomPerRow / 26.dp, 1.18f).coerceAtLeast(1f)
        val kw = k
        val rowHeight = (size.height - baseHeader * k - 12.dp) / list.size
        val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
        val hi = (list.mapNotNull { it.max }.maxOrNull() ?: lo).coerceAtLeast(lo + 1)
        // The home screen lays the widget out in the phone's language direction, not the app's
        // (they differ when the app's language is set separately), so the bars follow the phone.
        // Resources.getSystem() won't do: it picks up the app's language too.
        val phoneLocale = context.getSystemService(LocaleManager::class.java).systemLocales.get(0)
        val rtl = TextUtils.getLayoutDirectionFromLocale(phoneLocale) == View.LAYOUT_DIRECTION_RTL
        val text = 14.sp * k
        // The bar takes the row's leftover width; its bitmap is drawn for that width so the round
        // ends and the dot aren't stretched. (The picker preview reports the minimum size, so
        // assume a typical 4-cell width there.) Fixed: card + row padding, day, icon, min, max, chip, wind.
        val windWidth = (if (data.windDial) 48.dp else 42.dp) * kw
        val fixed = 20.dp + 20.dp + (50.dp + 28.dp + 34.dp + 36.dp + 62.dp) * kw + (if (data.showWind) windWidth else 0.dp)
        val layoutWidth = if (size.width < 300.dp) 360.dp else size.width
        val barWidth = (layoutWidth - fixed).coerceAtLeast(24.dp)

        val bgModifier = if (p.bgImage != null) GlanceModifier.background(ImageProvider(p.bgImage)) else GlanceModifier.background(p.bg)
        Box(
            GlanceModifier.fillMaxSize().then(bgModifier).cornerRadius(28.dp).padding(horizontal = 10.dp, vertical = if (roomy) 18.dp else 12.dp)
                .clickable(openApp()),
        ) {
            Column(GlanceModifier.fillMaxSize()) {
                // Now: place, then condition and update time, on the left (aligned with the day names);
                // icon, temperature and today's range under it on the right; then small pills.
                // Today's high/low goes under the temperature when there's room (fewer days, or 4×3);
                // with 5 days on a short widget (Samsung's 4×2) it stays in the condition line, and the
                // update time moves next to the place, so the header is only two lines tall.
                val rangeUnder = k > 1f || roomy
                val today = list.first()
                val range = "↑${temp(today.max, s)} ↓${temp(today.min, s)}"
                val time = data.updated?.let { hhmm(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), IsraelZone)) }
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(data.place?.name(en) ?: "", style = style((if (roomy) 19.sp else 17.sp) * k, p.ink, bold = true), maxLines = 1)
                            if (!rangeUnder && time != null) Text(" · $time", style = style(11.sp, p.muted), maxLines = 1)
                        }
                        val line = listOfNotNull(WeatherCodes.describe(now.code, en), if (rangeUnder) time else range).joinToString(" · ")
                        Text(line, style = style((if (roomy) 14.sp else 13.sp) * k, p.muted), maxLines = 1)
                    }
                    Image(
                        ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), WeatherCodes.describe(now.code, en),
                        GlanceModifier.size((if (roomy) 44.dp else 38.dp) * k),
                    )
                    Spacer(GlanceModifier.width(4.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(temp(now.temp, s), style = style((if (roomy) 40.sp else 34.sp) * k, p.ink, bold = true), maxLines = 1)
                        if (rangeUnder) Text(range, style = style(11.sp * k, p.muted), maxLines = 1)
                    }
                    if (data.showRefresh) {
                        Spacer(GlanceModifier.width(8.dp))
                        RefreshButton(p.button, 30.dp * k)
                    }
                }
                // The pills sit with the same visible space above and below them; a high/low line under
                // the temperature already brings ~5dp of text padding, so less is added above then.
                val pillSpace = (if (roomy) 10.dp else 7.dp) * k
                Spacer(GlanceModifier.height(if (rangeUnder) pillSpace - 5.dp * k else pillSpace))
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    apparentTemp(now.temp, now.rh, now.wind)?.let { StatPill(p, context.getString(R.string.feels), temp(it, s), scale = k) }
                    now.rh?.let { StatPill(p, context.getString(R.string.humidity), "${it.roundToInt()}%", scale = k) }
                    now.wind?.let { w -> StatPill(p, context.getString(R.string.wind), windValue(w, s), now.windDir?.let { windArrow(context.resources.displayMetrics.density, p, it) }, k) }
                }
                Spacer(GlanceModifier.height(pillSpace))
                // Rows keep a natural height (so today's highlight doesn't balloon); extra room becomes gaps
                // between them (none above the first, so the space under the pills matches the space over
                // them). With lots of room (few days on a tall widget) the gaps stay moderate and the rest
                // goes to the bottom.
                val roomyGap = 16.dp
                val gap = ((rowHeight - 34.dp * k) / 2).coerceAtLeast(0.dp)
                val grouped = gap > roomyGap
                // Their own column: Glance allows at most 10 children per column.
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                list.forEachIndexed { i, d ->
                    val today = i == 0
                    if (!today) Spacer(GlanceModifier.height((if (grouped) roomyGap else gap) * 2))
                    Row(
                        GlanceModifier.fillMaxWidth().then(if (grouped) GlanceModifier.height(34.dp * k) else GlanceModifier.defaultWeight())
                            .cornerRadius(14.dp * kw)
                            .background(if (today) p.today else Color.Transparent).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(GlanceModifier.width(50.dp * kw)) {
                            Text(dayLabel(context, d), style = style(text, p.ink, bold = today), maxLines = 1)
                        }
                        Box(GlanceModifier.width(28.dp * kw), contentAlignment = Alignment.Center) {
                            Image(
                                ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en),
                                GlanceModifier.size(22.dp * k),
                            )
                        }
                        Box(GlanceModifier.width(34.dp * kw), contentAlignment = Alignment.Center) {
                            Text(temp(d.min, s), style = style(text, p.muted), maxLines = 1)
                        }
                        val bar = rangeBar(
                            context.resources.displayMetrics.density, barWidth, p,
                            from = (((d.min ?: lo) - lo) / (hi - lo)).toFloat(),
                            to = (((d.max ?: hi) - lo) / (hi - lo)).toFloat(),
                            dot = if (today) now.temp?.let { ((it - lo) / (hi - lo)).toFloat() } else null,
                            rtl = rtl,
                            scale = kw,
                        )
                        Image(ImageProvider(bar), null, GlanceModifier.defaultWeight().height(14.dp * kw), contentScale = ContentScale.FillBounds)
                        Box(GlanceModifier.width(36.dp * kw), contentAlignment = Alignment.Center) {
                            Text(temp(d.max, s), style = style(text, p.ink, bold = true), maxLines = 1)
                        }
                        if (data.showWind) {
                            val (w, dir) = dayWind(data, d)
                            val dial = data.windDial && w != null && dir != null
                            Box(GlanceModifier.width(windWidth), contentAlignment = if (dial) Alignment.Center else Alignment.CenterEnd) {
                                if (dial) {
                                    // A capsule like the rain pills: an arrow toward where the wind blows, then the speed.
                                    Box(GlanceModifier.background(p.muted.copy(alpha = 0.3f)).cornerRadius(11.dp).padding(1.dp)) {
                                        Row(
                                            GlanceModifier.background(p.track).cornerRadius(10.dp).padding(start = 5.dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Image(ImageProvider(windArrow(context.resources.displayMetrics.density, p, dir)), null, GlanceModifier.size(11.dp))
                                            Spacer(GlanceModifier.width(3.dp))
                                            Text(windValue(w, s), style = style(12.sp * kw, p.ink, bold = true), maxLines = 1)
                                        }
                                    }
                                } else {
                                    Text(windShort(w, dir, s), style = style(12.sp * kw, p.muted), maxLines = 1)
                                }
                            }
                        }
                        val chance = dayRainChance(data, d)
                        val strong = (chance ?: 0) >= RAINY_PCT
                        Box(GlanceModifier.width(62.dp * kw), contentAlignment = Alignment.CenterEnd) {
                            val pillWidth = if (data.equalPills) GlanceModifier.width(58.dp * kw) else GlanceModifier
                            Box(pillWidth.background(if (strong) p.chipStrong else p.chipBorder).cornerRadius(10.dp).padding(1.dp)) {
                            Row(
                                (if (data.equalPills) GlanceModifier.fillMaxWidth() else GlanceModifier)
                                    .background(if (strong) p.chipStrong else p.chip).cornerRadius(9.dp)
                                    // Same-size pills: a little more room after the percentage.
                                    .padding(start = 6.dp, end = if (data.equalPills) 9.dp else 6.dp, top = 1.dp, bottom = 1.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(
                                    ImageProvider(if (strong || p.whiteDrop) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null,
                                    GlanceModifier.size(9.dp * kw),
                                )
                                Spacer(GlanceModifier.width(3.dp))
                                Text(pct(chance), style = style(12.sp * kw, if (strong) p.chipStrongInk else p.chipInk, bold = true), maxLines = 1)
                            }
                            }
                        }
                    }
                }
                if (grouped) Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/** "Humidity 73%": a soft pill under the header, label muted and value bold (optional arrow before the value). */
@Composable
private fun StatPill(p: Spectrum, label: String, value: String, arrow: Bitmap? = null, scale: Float = 1f) {
    Row(
        // Today's row tone: light on lavender/orange, see-through on Twilight (where the button tone is too light).
        GlanceModifier.background(p.today).cornerRadius(11.dp * scale).padding(horizontal = 8.dp * scale, vertical = 2.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label ", style = style(11.sp * scale, p.muted), maxLines = 1)
        if (arrow != null) {
            Image(ImageProvider(arrow), null, GlanceModifier.size(10.dp * scale))
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(value, style = style(11.sp * scale, p.ink, bold = true), maxLines = 1)
    }
    Spacer(GlanceModifier.width(6.dp))
}

/**
 * One day's range as a bitmap: a rounded track, the min→max part filled with the
 * cool→warm gradient of the whole week's scale, and optionally a "now" dot.
 */
internal fun rangeBar(density: Float, width: Dp, p: Spectrum, from: Float, to: Float, dot: Float?, rtl: Boolean, scale: Float = 1f): Bitmap {
    val w = (width.value * density).toInt().coerceAtLeast(1)
    val h = (14 * scale * density).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val thick = 8 * scale * density
    val top = (h - thick) / 2
    val dotR = 6 * scale * density
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
        c.drawCircle(cx, h / 2f, dotR - 3 * scale * density, paint)
    }
    return bmp
}

/** A small navigation-style arrow pointing where the wind blows (north stays up in Hebrew too). */
internal fun windArrow(density: Float, p: Spectrum, fromDeg: Double): Bitmap {
    val size = (11 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val u = size / 11f
    c.rotate((fromDeg + 180).toFloat(), size / 2f, size / 2f)
    val arrow = Path().apply {
        moveTo(5.5f * u, 0.5f * u)
        lineTo(10f * u, 10.5f * u)
        lineTo(5.5f * u, 8f * u)
        lineTo(1f * u, 10.5f * u)
        close()
    }
    c.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.ring.toArgb() })
    return bmp
}

class SpectrumReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SpectrumWidget() }
