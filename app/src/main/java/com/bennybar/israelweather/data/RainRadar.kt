package com.bennybar.israelweather.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.tan

/** Rain the radar sees near a point: [km] to the nearest rain (0 = overhead). */
data class RainNow(val km: Double, val heavy: Boolean, val approaching: Boolean)

/**
 * Reads IMS radar frames (transparent PNGs, Web Mercator over [Radar.bounds]) at a point:
 * is it raining there, or how far is the nearest rain, and is it getting closer.
 */
class RainRadar(private val http: OkHttpClient) {
    private val frames = LinkedHashMap<String, Bitmap>()

    /** Null when there's no rain within [RADIUS_KM] (or the radar is unavailable). */
    suspend fun near(radar: Radar, lat: Double, lon: Double): RainNow? = withContext(Dispatchers.IO) {
        val latest = radar.frames.lastOrNull() ?: return@withContext null
        val now = scan(frame(latest.url) ?: return@withContext null, radar.bounds, lat, lon) ?: return@withContext null
        // Frames are 5 minutes apart: compare with half an hour ago.
        val before = radar.frames.getOrNull(radar.frames.size - 7)?.let { frame(it.url) }?.let { scan(it, radar.bounds, lat, lon) }
        RainNow(now.first, now.second, approaching = before != null && before.first - now.first >= 3)
    }

    private fun frame(url: String): Bitmap? = synchronized(frames) { frames[url] } ?: runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return null
            BitmapFactory.decodeStream(res.body.byteStream())
        }
    }.getOrNull()?.also { bmp ->
        synchronized(frames) {
            frames[url] = bmp
            while (frames.size > 8) frames.remove(frames.keys.first())
        }
    }

    /** Nearest rain (km) within [RADIUS_KM], and whether rain near it is heavy; null if none. */
    private fun scan(bmp: Bitmap, b: Bounds, lat: Double, lon: Double): Pair<Double, Boolean>? {
        val w = bmp.width
        val h = bmp.height
        fun my(la: Double) = ln(tan(PI / 4 + Math.toRadians(la) / 2))
        val px = (lon - b.west) / (b.east - b.west) * w
        val py = (my(b.north) - my(lat)) / (my(b.north) - my(b.south)) * h
        val kmPerPx = (b.east - b.west) * 111.32 * cos(Math.toRadians(lat)) / w
        val r = (RADIUS_KM / kmPerPx).toInt()
        val x0 = (px - r).toInt().coerceIn(0, w - 1)
        val x1 = (px + r).toInt().coerceIn(0, w - 1)
        val y0 = (py - r).toInt().coerceIn(0, h - 1)
        val y1 = (py + r).toInt().coerceIn(0, h - 1)
        if (x1 <= x0 || y1 <= y0) return null
        val cols = x1 - x0 + 1
        val pixels = IntArray(cols * (y1 - y0 + 1))
        bmp.getPixels(pixels, 0, cols, x0, y0, cols, y1 - y0 + 1)
        var best = Double.MAX_VALUE
        var count = 0
        val hits = ArrayList<Pair<Double, Int>>()
        for (i in pixels.indices) {
            val c = pixels[i]
            if (Color.alpha(c) < 40) continue
            val d = hypot(x0 + i % cols - px, y0 + i / cols - py) * kmPerPx
            if (d > RADIUS_KM) continue
            count++
            hits += d to c
            if (d < best) best = d
        }
        // A few stray pixels are radar clutter, not rain.
        if (count < 4) return null
        val hsv = FloatArray(3)
        val heavy = hits.any { (d, c) ->
            d <= best + 3 && run {
                Color.colorToHSV(c, hsv)
                // IMS colors run teal/blue (light) → green → yellow, red, purple (heavy).
                hsv[0] < 75 || hsv[0] > 280
            }
        }
        return (if (best < OVERHEAD_KM) 0.0 else best) to heavy
    }

    companion object {
        const val RADIUS_KM = 25.0
        const val OVERHEAD_KM = 2.0
    }
}
