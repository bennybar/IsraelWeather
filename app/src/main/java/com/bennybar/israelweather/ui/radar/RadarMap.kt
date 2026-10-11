package com.bennybar.israelweather.ui.radar

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.toBitmap
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Bounds
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Radar
import com.bennybar.israelweather.ui.theme.LocalWeatherColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/** Web Mercator "world pixels" at zoom [Z0]; tiles of finer zooms are scaled into this space. */
private const val Z0 = 8
private val world = 256.0 * 2.0.pow(Z0)
private fun mx(lon: Double) = (lon + 180) / 360 * world
private fun my(lat: Double): Double {
    val r = Math.toRadians(lat)
    return (1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * world
}

/** What the map shows by default: Israel, Eilat to the Golan. */
private val IsraelView = Bounds(south = 29.4, west = 34.1, north = 33.45, east = 35.95)

/** Dark theme: OSM's light tiles as an inverted, colourless (grey) map, so the radar colours stand out. */
private val darkTiles = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.18f, -0.35f, -0.07f, 0f, 200f,
            -0.18f, -0.35f, -0.07f, 0f, 204f,
            -0.18f, -0.35f, -0.07f, 0f, 214f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/**
 * OpenStreetMap tiles with an IMS radar frame stretched over [Radar.bounds],
 * the way IMS's own Leaflet map places it. [cover] fills the box (cards);
 * otherwise Israel fits inside it. [scale]/[pan] come from the user's gestures.
 */
@Composable
fun RadarMap(
    radar: Radar?,
    frame: ImageBitmap?,
    place: Place?,
    modifier: Modifier = Modifier,
    cover: Boolean = false,
    scale: Float = 1f,
    pan: Offset = Offset.Zero,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surface
    val dark = LocalWeatherColors.current.dark
    val density = LocalDensity.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier.clipToBounds()) {
            val w = constraints.maxWidth.toDouble()
            val h = constraints.maxHeight.toDouble()
            val vx0 = mx(IsraelView.west)
            val vx1 = mx(IsraelView.east)
            val vy0 = my(IsraelView.north)
            val vy1 = my(IsraelView.south)
            val fit = if (cover) max(w / (vx1 - vx0), h / (vy1 - vy0)) else min(w / (vx1 - vx0), h / (vy1 - vy0))
            val s = fit * scale
            // Cards centre on the place; the full map on Israel.
            val cx = if (cover && place != null) mx(place.lon) else (vx0 + vx1) / 2
            val cy = if (cover && place != null) my(place.lat) else (vy0 + vy1) / 2
            fun sx(wx: Double) = (wx - cx) * s + w / 2 + pan.x
            fun sy(wy: Double) = (wy - cy) * s + h / 2 + pan.y

            // Finer tiles as the user zooms in, so the map stays sharp.
            val zt = (Z0 + floor(log2(s)).toInt().coerceAtLeast(0)).coerceAtMost(12)
            val tileWorld = 256.0 / 2.0.pow(zt - Z0)
            val n = 2.0.pow(zt).toInt()
            val tx0 = floor(((0 - w / 2 - pan.x) / s + cx) / tileWorld).toInt().coerceIn(0, n - 1)
            val tx1 = floor(((w - w / 2 - pan.x) / s + cx) / tileWorld).toInt().coerceIn(0, n - 1)
            val ty0 = floor(((0 - h / 2 - pan.y) / s + cy) / tileWorld).toInt().coerceIn(0, n - 1)
            val ty1 = floor(((h - h / 2 - pan.y) / s + cy) / tileWorld).toInt().coerceIn(0, n - 1)
            val tilePx = tileWorld * s
            val tileDp = with(density) { (tilePx + 1).toFloat().toDp() }
            for (ty in ty0..ty1) for (tx in tx0..tx1) {
                AsyncImage(
                    model = "https://tile.openstreetmap.org/$zt/$tx/$ty.png",
                    contentDescription = null,
                    colorFilter = if (dark) darkTiles else null,
                    modifier = Modifier
                        .absoluteOffset { IntOffset(sx(tx * tileWorld).roundToInt(), sy(ty * tileWorld).roundToInt()) }
                        .size(tileDp),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                if (radar != null && frame != null) {
                    val b = radar.bounds
                    val x0 = sx(mx(b.west)).roundToInt()
                    val y0 = sy(my(b.north)).roundToInt()
                    val x1 = sx(mx(b.east)).roundToInt()
                    val y1 = sy(my(b.south)).roundToInt()
                    drawImage(frame, dstOffset = IntOffset(x0, y0), dstSize = IntSize(x1 - x0, y1 - y0), alpha = 0.9f)
                }
                if (place != null) {
                    val p = Offset(sx(mx(place.lon)).toFloat(), sy(my(place.lat)).toFloat())
                    drawCircle(ring, 8.dp.toPx(), p)
                    drawCircle(accent, 5.5.dp.toPx(), p)
                }
            }
            Text(
                "© OpenStreetMap",
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                fontSize = 10.sp,
                color = if (dark) Color(0xCCECEEF4) else Color(0xCC14161C),
            )
        }
    }
}

/** Decodes one radar frame at a modest size (22 full-size frames would be ~80 MB). */
suspend fun loadFrame(context: Context, url: String, size: Int = 640): ImageBitmap? {
    val request = ImageRequest.Builder(context).data(url).size(size).build()
    return SingletonImageLoader.get(context).execute(request).image?.toBitmap()?.asImageBitmap()
}

/** The latest radar frame over the map, for the forecast page's card. */
@Composable
fun RadarPreview(place: Place?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val radar by produceState<Radar?>(null) { value = runCatching { app.repo.radar() }.getOrNull() }
    val frame by produceState<ImageBitmap?>(null, radar) {
        value = radar?.frames?.lastOrNull()?.let { loadFrame(context, it.url) }
    }
    Box(modifier) {
        RadarMap(radar, frame, place, Modifier.fillMaxSize(), cover = true, scale = 1.6f)
    }
}
