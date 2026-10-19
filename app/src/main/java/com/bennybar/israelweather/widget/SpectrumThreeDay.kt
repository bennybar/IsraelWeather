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
import androidx.glance.unit.ColorProvider
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

// Spectrum's day layouts (per-widget "Layout" setting): Refined (three days) and Tiles (3–5).
// Wind (arrow + speed) on every day and same-width rain pills, on one shared temperature scale.
// Both fit 4×2 (tighter) and 4×3 (roomier).

const val SPECTRUM_LAYOUT_CLASSIC = "classic"
const val SPECTRUM_LAYOUT_REFINED = "refined"
const val SPECTRUM_LAYOUT_TILES = "tiles"

/** Days shown for a layout: Classic 2/3/5, Tiles 3/4/5 (from the Days setting), Refined always 3. */
fun spectrumDays(layout: String, dayCount: Int): Int = when (layout) {
    SPECTRUM_LAYOUT_CLASSIC -> dayCount
    SPECTRUM_LAYOUT_TILES -> dayCount.coerceIn(3, 5)
    else -> 3
}

/** The Days choices a layout offers in the widget's settings (none for Refined). */
fun spectrumDayChoices(layout: String): List<Int> = when (layout) {
    SPECTRUM_LAYOUT_CLASSIC -> listOf(2, 3, 5)
    SPECTRUM_LAYOUT_TILES -> listOf(3, 4, 5)
    else -> emptyList()
}

/** From this height (4×3) everything gets a little bigger and roomier. */
private val TALL_LAYOUT_MIN_HEIGHT = 250.dp

@Composable
internal fun SpectrumThreeDay(data: WidgetData, p: Spectrum, now: Hour, list: List<Day>) {
    val context = LocalContext.current
    val s = app.settings.value
    val en = english(context)
    val size = LocalSize.current
    val tall = size.height >= TALL_LAYOUT_MIN_HEIGHT
    // Anything but Three Tiles (including the retired Breathing Room) shows Refined.
    val tiles = data.layout == SPECTRUM_LAYOUT_TILES
    // 4–5 tiles are narrow; on a short widget they need the stats line's and footer's room too.
    val narrow = tiles && list.size >= 4
    val bare = narrow && !tall
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
            if (!bare) {
                Spacer(GlanceModifier.height(if (tall) 4.dp else 2.dp))
                Stats(p, now, s, density, k = k)
            }
            Spacer(GlanceModifier.height(if (tall) 10.dp else 8.dp))
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                when {
                    tiles -> Row(GlanceModifier.fillMaxSize()) {
                        val gap = if (narrow) 5.dp else 8.dp
                        val tile = (width - pad * 2 - gap * (list.size - 1)) / list.size
                        list.forEachIndexed { i, d ->
                            if (i > 0) Spacer(GlanceModifier.width(gap))
                            if (narrow) {
                                NarrowTile(data, p, d, today = i == 0, s, en, 1f, bar(d, i == 0, tile - 8.dp), density, tile - 8.dp)
                            } else if (tall) {
                                Tile(data, p, d, today = i == 0, s, en, 1f, bar(d, i == 0, tile - 16.dp), density)
                            } else {
                                CompactTile(data, p, d, today = i == 0, s, en, bar(d, i == 0, tile - 12.dp), density)
                            }
                        }
                    }
                    // Tall: the rows fill the height with small gaps. Short: their height comes from the
                    // widget's real height (what's left after the header, stats and footer), since rows
                    // sharing it by weight keep their content height and get clipped at the bottom.
                    else -> {
                        val header = if (compact) 46.dp else 54.dp
                        val left = size.height - pad * 2 - header - 21.dp - 15.dp - 8.dp
                        val rowHeight = ((left - 6.dp) / 3).coerceAtLeast(22.dp)
                        list.forEachIndexed { i, d ->
                            if (i > 0) Spacer(GlanceModifier.height(if (tall) 8.dp else 3.dp))
                            // Columns stay at their base width (only text grows), so the bar keeps its room.
                            val barWidth = width - pad * 2 - 16.dp - (44.dp + 26.dp + 32.dp + 34.dp + 44.dp + 56.dp)
                            val height = if (tall) GlanceModifier.defaultWeight() else GlanceModifier.height(rowHeight)
                            RefinedRow(data, p, d, today = i == 0, s, en, k, bar(d, i == 0, barWidth.coerceAtLeast(24.dp)), density, height)
                        }
                    }
                }
            }
            if (!bare) {
                Spacer(GlanceModifier.height(if (tall) 8.dp else 4.dp))
                Footer(data, p, s, k)
            }
        }
    }
}

/** Place and condition; current icon and temperature; refresh. No high/low: the days show it. */
@Composable
private fun ThreeDayHeader(data: WidgetData, p: Spectrum, now: Hour, s: Settings, en: Boolean, k: Float, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(data.place?.name(en) ?: "", style = style(if (compact) 18.sp else 20.sp, p.ink, bold = true), maxLines = 1)
            Text(WeatherCodes.describe(now.code, en), style = style(if (compact) 13.sp else 14.sp, p.muted), maxLines = 1)
        }
        Image(
            ImageProvider(WeatherCodes.drawable(now.code, WeatherCodes.isNight(now.time))), WeatherCodes.describe(now.code, en),
            GlanceModifier.size((if (compact) 30.dp else 34.dp) * k),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(temp(now.temp, s), style = style((if (compact) 32.sp else 38.sp) * k, p.ink, bold = true), maxLines = 1)
        if (data.showRefresh) {
            Spacer(GlanceModifier.width(4.dp))
            // Slim: it's secondary. The touch area stays comfortable around it.
            RefreshButton(p.button, if (compact) 24.dp else 28.dp, touchMargin = 6.dp)
        }
    }
}

/** Feels-like, humidity and current wind: one quiet line. */
@Composable
private fun Stats(p: Spectrum, now: Hour, s: Settings, density: Float, k: Float) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun stat(label: String, value: String, arrow: Boolean = false) {
            Text("$label ", style = style(13.5.sp * k, p.muted), maxLines = 1)
            if (arrow) now.windDir?.let {
                Image(ImageProvider(windArrow(density, p, it)), null, GlanceModifier.size(11.dp * k))
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(value, style = style(13.5.sp * k, p.ink, bold = true), maxLines = 1)
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
private fun Wind(p: Spectrum, d: Day, data: WidgetData, s: Settings, density: Float, size: TextUnit, k: Float, unit: Boolean = true) {
    val (w, dir) = dayWind(data, d)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (w != null && dir != null) {
            Image(ImageProvider(windArrow(density, p, dir)), null, GlanceModifier.size(10.dp * k))
            Spacer(GlanceModifier.width(3.dp))
        }
        Text(if (w != null) windValue(w, s) else "–", style = style(size, p.muted), maxLines = 1)
        // The unit, small: "17" alone next to "17°" reads as a temperature.
        if (unit && w != null) Text(" " + LocalContext.current.getString(if (s.windUnit == WindUnit.MS) R.string.unit_ms else R.string.unit_kmh), style = style(9.sp * k, p.muted), maxLines = 1)
    }
}

/** Same-width rain pill, no border: pale under [RAINY_PCT], strong blue from it. */
@Composable
private fun RainPill(p: Spectrum, chance: Int?, width: Dp, k: Float) {
    val strong = (chance ?: 0) >= RAINY_PCT
    Row(
        GlanceModifier.width(width).background(if (strong) p.chipStrong else p.chipQuiet).cornerRadius(10.dp).padding(vertical = 3.dp * k),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Low chances stay quiet (lighter, not bold); only real rain gets the strong blue.
        Image(ImageProvider(if (strong || p.whiteDrop) R.drawable.tonal_drop_white else R.drawable.tonal_drop), null, GlanceModifier.size((if (strong) 9.dp else 8.dp) * k))
        Spacer(GlanceModifier.width(3.dp))
        Text(
            pct(chance),
            style = style((if (strong) 12.sp else 11.sp) * k, if (strong) p.chipStrongInk else p.chipQuietInk, bold = strong),
            maxLines = 1,
        )
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
        GlanceModifier.fillMaxWidth().then(height).cornerRadius(14.dp).background(if (today) p.today else ColorProvider(Color.Transparent))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.width(44.dp)) { Text(dayLabel(context, d), style = style(14.sp * k, p.ink, bold = today), maxLines = 1) }
        Box(GlanceModifier.width(26.dp), contentAlignment = Alignment.Center) { DayIcon(d, en, 22.dp * k) }
        Box(GlanceModifier.width(32.dp), contentAlignment = Alignment.Center) { Text(temp(d.min, s), style = style(13.sp * k, p.muted), maxLines = 1) }
        Image(ImageProvider(bar), null, GlanceModifier.defaultWeight().height(12.dp * k), contentScale = ContentScale.FillBounds)
        Box(GlanceModifier.width(34.dp), contentAlignment = Alignment.Center) { Text(temp(d.max, s), style = style(14.sp * k, p.ink, bold = true), maxLines = 1) }
        // No unit here: the column is narrow, and Refined's footer always names it.
        Box(GlanceModifier.width(44.dp), contentAlignment = Alignment.Center) { Wind(p, d, data, s, density, 12.sp * k, k, unit = false) }
        Box(GlanceModifier.width(56.dp), contentAlignment = Alignment.CenterEnd) { RainPill(p, dayRainChance(data, d), 52.dp, k) }
    }
}

/** Three Tiles: one column per day; big high, small low, short bar, wind, rain. */
@Composable
private fun androidx.glance.layout.RowScope.Tile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, k: Float, bar: android.graphics.Bitmap, density: Float) {
    val context = LocalContext.current
    TileFrame(p, today, 16.dp) {
    Column(
        GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Spacing is padding, not spacers: Glance allows at most 10 children per column.
        Text(dayLabel(context, d), style = style(14.sp * k, if (today) p.ring else p.ink, bold = today), maxLines = 1)
        Image(
            ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en),
            GlanceModifier.padding(vertical = 2.dp).size(28.dp * k + 4.dp),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(temp(d.max, s), style = style(22.sp * k, p.ink, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(5.dp))
            Text(temp(d.min, s), style = style(13.sp * k, p.muted), maxLines = 1)
        }
        Image(ImageProvider(bar), null, GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).height(10.dp * k + 8.dp), contentScale = ContentScale.FillBounds)
        Wind(p, d, data, s, density, 11.5.sp * k, k)
        Box(GlanceModifier.padding(top = 4.dp)) { RainPill(p, dayRainChance(data, d), 60.dp * k, k) }
    }
    }
}

/**
 * Three Tiles on a short (4×2) widget: the same content in fewer lines. Day and icon share a line,
 * and wind sits beside the rain pill.
 */
@Composable
private fun androidx.glance.layout.RowScope.CompactTile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, bar: android.graphics.Bitmap, density: Float) {
    val context = LocalContext.current
    TileFrame(p, today, 14.dp) {
    Column(
        GlanceModifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayLabel(context, d), style = style(13.sp, if (today) p.ring else p.ink, bold = today), maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
            DayIcon(d, en, 20.dp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(temp(d.max, s), style = style(16.sp, p.ink, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
            Text(temp(d.min, s), style = style(12.sp, p.muted), maxLines = 1)
        }
        Image(ImageProvider(bar), null, GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).height(11.dp), contentScale = ContentScale.FillBounds)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // No unit: it shares the line with the rain pill, and this layout keeps the units footer.
            Wind(p, d, data, s, density, 11.sp, 1f, unit = false)
            Spacer(GlanceModifier.width(6.dp))
            RainPill(p, dayRainChance(data, d), 46.dp, 1f)
        }
    }
    }
}

/**
 * A narrow tile (4–5 across): everything stacked, nothing side by side, slightly smaller type.
 * [inner] is the tile's content width, which the rain pill fills (up to 48dp).
 */
@Composable
private fun androidx.glance.layout.RowScope.NarrowTile(data: WidgetData, p: Spectrum, d: Day, today: Boolean, s: Settings, en: Boolean, k: Float, bar: android.graphics.Bitmap, density: Float, inner: Dp) {
    val context = LocalContext.current
    TileFrame(p, today, 12.dp) {
    Column(
        GlanceModifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(context, d), style = style(12.sp * k, if (today) p.ring else p.ink, bold = today), maxLines = 1)
        Image(
            ImageProvider(WeatherCodes.drawable(d.code)), WeatherCodes.describe(d.code, en),
            GlanceModifier.padding(vertical = 2.dp).size(22.dp * k + 4.dp),
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(temp(d.max, s), style = style(15.sp * k, p.ink, bold = true), maxLines = 1)
            Spacer(GlanceModifier.width(3.dp))
            Text(temp(d.min, s), style = style(11.sp * k, p.muted), maxLines = 1)
        }
        Image(ImageProvider(bar), null, GlanceModifier.fillMaxWidth().padding(vertical = 2.dp).height(10.dp), contentScale = ContentScale.FillBounds)
        // The unit only where it fits (4 tiles yes, 5 on a narrow phone no); the footer names it otherwise.
        Wind(p, d, data, s, density, 10.5.sp * k, 1f, unit = inner >= 60.dp)
        Box(GlanceModifier.padding(top = 2.dp)) { RainPill(p, dayRainChance(data, d), minOf(inner, 48.dp), 1f) }
    }
    }
}

/**
 * A tile's background: today is the lightest (its day name is in the accent color, see the tiles),
 * the other days sit on a softer version of the same tone. No outline: it read heavy.
 */
@Composable
private fun androidx.glance.layout.RowScope.TileFrame(p: Spectrum, today: Boolean, radius: Dp, content: @Composable () -> Unit) {
    Box(GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(radius).background(if (today) p.today else p.soft)) {
        content()
    }
}
