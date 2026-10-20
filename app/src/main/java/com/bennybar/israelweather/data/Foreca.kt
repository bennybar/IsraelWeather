package com.bennybar.israelweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Foreca (https://developer.foreca.com), with the user's own API key: an alternative forecast
 * source for every place. Three calls per refresh (current, hourly, daily), at most every
 * [MIN_INTERVAL_MS] per spot, so the free plan's 2,000 requests a day go a long way.
 */
class Foreca(private val http: OkHttpClient, private val json: Json) {
    private val recent = HashMap<String, Pair<Long, Forecast>>()

    @Serializable
    private data class Point(
        val time: String? = null,
        val date: String? = null,
        val symbol: String? = null,
        val temperature: Double? = null,
        val maxTemp: Double? = null,
        val minTemp: Double? = null,
        val relHumidity: Double? = null,
        val windSpeed: Double? = null,
        val windGust: Double? = null,
        val windDir: Double? = null,
        val precipProb: Double? = null,
        val precipAccum: Double? = null,
        val uvIndex: Double? = null,
    )

    @Serializable
    private data class Current(val current: Point)

    @Serializable
    private data class Series(val forecast: List<Point> = emptyList())

    private fun get(path: String, key: String, query: String): String {
        val url = "https://weatherapi.foreca.net/api/v1/$path?tempunit=C&windunit=KMH&tz=Asia/Jerusalem$query"
        val request = Request.Builder().url(url).header("Authorization", "Bearer $key").build()
        return http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            res.body.string()
        }
    }

    /** Whether [key] is accepted (one current-conditions call, Tel Aviv). */
    suspend fun check(key: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { get("current/34.78,32.08", key, "") }.isSuccess
    }

    suspend fun forecast(lat: Double, lon: Double, key: String, id: Int): Forecast = withContext(Dispatchers.IO) {
        val spot = "%.3f,%.3f".format(Locale.ROOT, lon, lat)
        synchronized(recent) { recent[spot] }?.takeIf { System.currentTimeMillis() - it.first < MIN_INTERVAL_MS }
            ?.let { return@withContext it.second.copy(id = id) }
        val now = json.decodeFromString(Current.serializer(), get("current/$spot", key, "")).current
        val hours = json.decodeFromString(Series.serializer(), get("forecast/hourly/$spot", key, "&periods=168&dataset=full")).forecast
        val days = json.decodeFromString(Series.serializer(), get("forecast/daily/$spot", key, "&periods=7&dataset=full")).forecast
        fun hour(p: Point) = Hour(
            t = (p.time ?: "").take(16).replace('T', ' '),
            code = imsCode(p.symbol),
            temp = p.temperature,
            rainChance = p.precipProb,
            rain = p.precipAccum,
            rh = p.relHumidity,
            wind = p.windSpeed,
            gust = p.windGust,
            windDir = p.windDir,
            uv = p.uvIndex,
        )
        val f = Forecast(
            id = id,
            hourly = hours.filter { it.time != null }.map(::hour),
            daily = days.filter { it.date != null }.map { d -> Day(d.date!!, imsCode(d.symbol), d.minTemp, d.maxTemp, d.uvIndex) },
            source = Forecast.SOURCE_FORECA,
            now = now.takeIf { it.time != null }?.let(::hour),
        )
        synchronized(recent) { recent[spot] = System.currentTimeMillis() to f }
        f
    }

    companion object {
        const val MIN_INTERVAL_MS = 30 * 60_000L

        /**
         * Foreca symbols ("d210": day/night, then cloudiness 0–6, precipitation rate 0–4, type
         * 0 rain / 1 sleet / 2 snow; cloudiness 6 = fog) → the IMS codes the app's icons use.
         */
        fun imsCode(symbol: String?): Int {
            val s = symbol?.takeIf { it.length == 4 } ?: return 1230
            val cloud = s[1].digitToIntOrNull() ?: return 1230
            val rate = s[2].digitToIntOrNull() ?: 0
            val type = s[3].digitToIntOrNull() ?: 0
            return when {
                cloud == 6 -> 1160
                rate == 4 -> 1020
                rate > 0 && type == 2 -> if (rate == 1) 1070 else 1060
                rate > 0 && type == 1 -> 1080
                rate == 1 -> 1560
                rate == 2 -> if (cloud <= 2) 1530 else 1540
                rate == 3 -> 1140
                // "Mostly clear" reads as sunny.
                cloud <= 1 -> 1250
                cloud == 2 || cloud == 5 -> 1220
                else -> 1230
            }
        }
    }
}
