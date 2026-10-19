package com.bennybar.israelweather.widget

import android.app.LocaleManager
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.action.clickable
import androidx.glance.text.Text
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Day
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.Settings
import com.bennybar.israelweather.data.TempUnit
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.WindUnit
import com.bennybar.israelweather.data.apparentTemp
import com.bennybar.israelweather.ui.temp
import com.bennybar.israelweather.ui.windValue
import kotlin.math.roundToInt

// Spectrum's three-day layouts (per-widget "Layout" setting): Refined, Breathing Room and Three
// Tiles. Always today + two days, wind (arrow + speed) on every day and same-width rain pills,
// on one shared temperature scale. Breathing Room and Three Tiles need a tall widget (4×3);
// shorter ones get Refined rather than squeezed text.

const val SPECTRUM_LAYOUT_CLASSIC = "classic"
const val SPECTRUM_LAYOUT_REFINED = "refined"
const val SPECTRUM_LAYOUT_BREATHING = "breathing"
const val SPECTRUM_LAYOUT_TILES = "tiles"

/** Below this height Breathing Room and Three Tiles don't fit; Refined is shown instead. */
private val TALL_LAYOUT_MIN_HEIGHT = 250.dp

@Composable
internal fun SpectrumThreeDay(data: WidgetData, p: Spectrum, now: Hour, list: List<Day>) {
    val context = LocalContext.current
    val s = app.settings.value
    val en = english(context)
    val size = LocalSize.current
    val tall = size.height >= TALL_LAYOUT_MIN_HEIGHT
    val layout = if (data.layout == SPECTRUM_LAYOUT_REFINED || !tall) SPECTRUM_LAYOUT_REFINED else data.layout
    // A tall widget gets a little more of everything; never more data. A short one (Samsung's 4×2
    // is ~20dp shorter than Pixel's) gets a slightly tighter header so the three rows still fit.
    val k = if (tall) 1.12f else 1f
    val compact = size.height < 205.dp
    val density = context.resources.displayMetrics.density
    val phoneLocale = context.getSystemService(LocaleManager::class.java).systemLocales.get(0)
    val rtl = TextUtils.getLayoutDirectionFromLocale(phoneLocale) == View.LAYOUT_DIRECTION_RTL
    val lo = list.mapNotNull { it.min }.minOrNull() ?: 0.0
    val hi = (list.mapNotNull { it.max }.maxOrNull() ?: lo).coerceAtLeast(lo + 1)
    fun bar(d: Day, today: Boolean, width: Dp) = rangeBar(
        density, width, p,
        from = (((d.min ?: lo) - lo) / (hi - lo)).toFloat(),
        to = (((d.max ?: hi) - lo) / (hi - lo)).toFloat(),
        dot = if (today) now.temp?.let { ((it - lo) / (hi - lo)).toFloat() } else null,
        rtl = rtl,
        scale = 0.85f * k,
    )
    // The preview reports the minimum size; assume a typical 4-cell width there.
    val width = if (size.width < 300.dp) 360.dp else size.width
    val pad = 12.dp

    val bgModifier = if (p.bgImage != null) GlanceModifier.background(ImageProvider(p.bgImage)) else GlanceModifier.background(p.bg)
    Box(GlanceModifier.fillMaxSize().then(bgModifier).cornerRadius(28.dp).padding(horizontal = pad, vertical = pad).clickable(openApp())) {
        Column(GlanceModifier.fillMaxSize()) {
            ThreeDayHeader(data, p, now, s, en, k, compact)
            Spacer(GlanceModifier.height(if (tall) 10.dp else 4.dp))
            Stats(p, now, s, density, boxed = layout == SPECTRUM_LAYOUT_BREATHING, k = k)
            Spacer(GlanceModifier.height(if (tall) 12.dp else 6.dp))
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                when (layout) {
                    SPECTRUM_LAYOUT_TILES -> Row(GlanceModifier.fillMaxSize()) {
                        val tile = (width - pad * 2 - 16.dp) / 3
                        list.forEachIndexed { i, d ->
                            if (i > 0) Spacer(GlanceModifier.width(8.dp))
                            Tile(data, p, d, today = i == 0, s, en, k, bar(d, i == 0, tile - 16.dp), density)
                        }
                    }
                    SPECTRUM_LAYOUT_BREATHING -> list.forEachIndexed { i, d ->
                        if (i > 0) Spacer(GlanceModifier.defaultWeight())
                        // Today's accent strip takes 3dp of the panel's width.
                        val barWidth = width - pad * 2 - 20.dp - (32.dp + 34.dp) * k - (if (i == 0) 3.dp else 0.dp)
                        Panel(data, p, d, today = i == 0, s, en, k, bar(d, i == 0, barWidth), density)
                    }
                    // Tall: the rows fill the height with small gaps. Short: their height comes from the
                    // widget's real height (what's left after the header, stats and footer), since rows
                    // sharing it by weight keep their content height and get clipped at the bottom.
                    else -> {
                        val header = if (compact) 44.dp else 52.dp
                        val left = size.height - pad * 2 - header - 20.dp - 15.dp - 14.dp
                        val rowHeight = ((left - 6.dp) / 3).coerceAtLeast(22.dp)
                        list.forEachIndexed { i, d ->
                            if (i > 0) Spacer(GlanceModifier.height(if (tall) 8.dp else 3.dp))
                            val barWidth = width - pad * 2 - 16.dp - (44.dp + 26.dp + 32.dp + 34.dp + 46.dp + 56.dp) * k
                            val height = if (tall) GlanceModifier.defaultWeight() else GlanceModifier.height(rowHeight)
                            RefinedRow(data, p, d, today = i == 0, s, en, k, bar(d, i == 0, barWidth.coerceAtLeast(24.dp)), density, height)
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
            Footer(data, p, s, k)
        }
    }
}

/** Place and condition; current icon and temperature; refresh. No high/low: the days show it. */
@Composable
private fun ThreeDayHeader(data: WidgetData, p: Spectrum, now: Hour, s: Settings, en: Boolean, k: Float, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(data.place?.name(en) ?: "", style = style((if (compact) 16.sp else 18.sp) * k, p.ink, bold = true), maxLines = 1)
            Text(WeatherCodes.describe(now.code, en), style = style(12.sp * k, p.muted), maxLines = 1)
        }
        Image(
            ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), WeatherCodes.describe(now.code, en),
            GlanceModifier.size((if (compact) 30.dp else 34.dp) * k),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(temp(now.temp, s), style = style((if (compact) 32.sp else 38.sp) * k, p.ink, bold = true), maxLines = 1)
        if (data.showRefresh) {
            Spacer(GlanceModifier.width(4.dp))
            RefreshButton(p.button, (if (compact) 28.dp else 32.dp) * k, touchMargin = if (compact) 4.dp else 6.dp)
        }
    }
}

/** Feels-like, humidity and current wind: a quiet line, or a soft strip (Breathing Room). */
@Composable
private fun Stats(p: Spectrum, now: Hour, s: Settings, density: Float, boxed: Boolean, k: Float) {
    val context = LocalContext.current
    val modifier = if (boxed) {
        GlanceModifier.fillMaxWidth().background(p.today).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 6.dp * k)
    } else {
        GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun stat(label: String, value: String, arrow: Boolean = false) {
            Text("$label ", style = style(12.sp * k, p.muted), maxLines = 1)
            if (arrow) now.windDir?.let {
                Image(ImageProvider(windArrow(density, p, it)), null, GlanceModifier.size(10.dp * k))
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(value, style = style(12.sp * k, p.ink, bold = true), maxLines = 1)
        }
        apparentTemp(now.temp, now.rh, now.wind)?.let { stat(context.getString(R.string.feels), temp(it, s)) }
        Spacer(GlanceModifier.defaultWeight())
        now.rh?.let { stat(context.getString(R.string.humidity), "${it.roundToInt()}%") }
        Spacer(GlanceModifier.defaultWeight())
        now.wind?.let { stat(context.getString(R.string.wind), windValue(it, s), arrow = true) }
    }
}

/** "°C · wind in km/h" and the update time: units once, not in every row. */
@Composable
private fun Footer(data: WidgetData, p: Spectrum, s: Settings, k: Float) {
    val context = LocalContext.current
    val units = context.getString(
        R.string.widget_units,
        if (s.tempUnit == TempUnit.F) "°F" else "°C",
        context.getString(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh),
    )
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(units, style = style(10.sp * k, p.muted), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        updatedText(context, data.updated)?.let { Text(it, style = style(10.sp * k, p.muted), maxLines = 1) }
    }
}

/** Arrow toward where the wind blows, then the speed (no unit: the footer says it). */
@Composable
private fun Wind(p: Spectrum, d: Day, data: WidgetData, s: Settings, density: Float, size: TextUnit, k: Float) {
    val (w, dir) = dayWind(data, d)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (w != null && dir != null) {
            Image(ImageProvider(windArrow(density, p, dir)), null, GlanceModifier.size(10.dp * k))
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(if (w != null) windValue(w, s) else "–", style = style(size, p.muted), maxLines = 1)
    }
}

/** Same-width rain pill, no border: pale under [RAINY_PCT], strong blue from it. */
@Composable
private fun RainPill(p: Spectrum, chance: Int?, width: Dp, k: Float) {
    val strong = (chance ?: 0) >= RAINY_PCT
    Row(
        GlanceModifier.width(width).background(if (strong) p.chipStrong else p.chip).cornerRadius(10.dp).padding(vertical = 3.dp * k),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(ImageProvider(if (strong || p.whiteDrop) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null, GlanceModifier.size(9.dp * k))
        Spacer(GlanceModifier.width(3.dp))
        Text(pct(chance), style = style(12.sp * k, if (strong) p.chipStrongInk else p.chipInk, bold = true), maxLines = 1)
    }
}

@Composable
private fun DayIcon(d: Day, en: Boolean, size: Dp) =
    Image(ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en), GlanceModifier.size(size))

/** Refined: one line per day, today on a soft background. */
@Composable
private fun RefinedRow(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, k: Float, bar: android.graphics.Bitmap, density: Float, height: GlanceModifier) {
    val context = LocalContext.current
    Row(
        GlanceModifier.fillMaxWidth().then(height).cornerRadius(14.dp).background(if (today) p.today else Color.Transparent)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(44.dp * k)) { Text(dayLabel(context, d), style = style(14.sp * k, p.ink, bold = today), maxLines = 1) }
        Box(GlanceModifier.width(26.dp * k), contentAlignment = Alignment.Center) { DayIcon(d, en, 22.dp * k) }
        Box(GlanceModifier.width(32.dp * k), contentAlignment = Alignment.Center) { Text(temp(d.min, s), style = style(13.sp * k, p.muted), maxLines = 1) }
        Image(ImageProvider(bar), null, GlanceModifier.defaultWeight().height(12.dp * k), contentScale = ContentScale.FillBounds)
        Box(GlanceModifier.width(34.dp * k), contentAlignment = Alignment.Center) { Text(temp(d.max, s), style = style(14.sp * k, p.ink, bold = true), maxLines = 1) }
        Box(GlanceModifier.width(46.dp * k), contentAlignment = Alignment.Center) { Wind(p, d, data, s, density, 12.sp * k, k) }
        Box(GlanceModifier.width(56.dp * k), contentAlignment = Alignment.CenterEnd) { RainPill(p, dayRainChance(data, d), 52.dp * k, k) }
    }
}

/** Breathing Room: a soft two-line panel per day; a wide range bar on the second line. */
@Composable
private fun Panel(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, k: Float, bar: android.graphics.Bitmap, density: Float) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(p.today)) {
        // Today's accent at the reading-start edge (Row mirrors it in RTL).
        if (today) Box(GlanceModifier.width(3.dp).height(64.dp * k).background(p.ring)) {}
        Column(GlanceModifier.defaultWeight().padding(horizontal = 10.dp, vertical = 7.dp * k)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(context, d), style = style(14.sp * k, p.ink, bold = today), maxLines = 1)
                Spacer(GlanceModifier.width(8.dp))
                DayIcon(d, en, 22.dp * k)
                Spacer(GlanceModifier.defaultWeight())
                Wind(p, d, data, s, density, 12.sp * k, k)
                Spacer(GlanceModifier.width(10.dp))
                RainPill(p, dayRainChance(data, d), 54.dp * k, k)
            }
            Spacer(GlanceModifier.height(4.dp * k))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(32.dp * k)) { Text(temp(d.min, s), style = style(13.sp * k, p.muted), maxLines = 1) }
                Image(ImageProvider(bar), null, GlanceModifier.defaultWeight().height(12.dp * k), contentScale = ContentScale.FillBounds)
                Box(GlanceModifier.width(34.dp * k), contentAlignment = Alignment.CenterEnd) { Text(temp(d.max, s), style = style(14.sp * k, p.ink, bold = true), maxLines = 1) }
            }
        }
    }
}

/** Three Tiles: one column per day; big high, small low, short bar, wind, rain. */
@Composable
private fun androidx.glance.layout.RowScope.Tile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, k: Float, bar: android.graphics.Bitmap, density: Float) {
    val context = LocalContext.current
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(16.dp).background(if (today) p.track else p.today).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Spacing is padding, not spacers: Glance allows at most 10 children per column.
        Text(dayLabel(context, d), style = style(14.sp * k, p.ink, bold = today), maxLines = 1)
        Image(
            ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en),
            GlanceModifier.padding(vertical = 4.dp).size(28.dp * k + 8.dp),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(temp(d.max, s), style = style(22.sp * k, p.ink, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(5.dp))
            Text(temp(d.min, s), style = style(13.sp * k, p.muted), maxLines = 1)
        }
        Image(ImageProvider(bar), null, GlanceModifier.fillMaxWidth().padding(vertical = 5.dp).height(10.dp * k + 10.dp), contentScale = ContentScale.FillBounds)
        Wind(p, d, data, s, density, 13.sp * k, k)
        Box(GlanceModifier.padding(top = 6.dp)) { RainPill(p, dayRainChance(data, d), 60.dp * k, k) }
    }
}
