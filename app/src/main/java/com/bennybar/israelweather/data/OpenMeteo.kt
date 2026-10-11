package com.bennybar.israelweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Open-Meteo (https://open-meteo.com, CC BY 4.0): model forecasts (ECMWF, ICON…, best match)
 * for exact coordinates. Used where no IMS forecast point is near (see [IMS_RADIUS_KM]).
 * Called directly from the phone: free without a key, and each user has their own quota.
 */
class OpenMeteo(private val http: OkHttpClient, private val json: Json) {

    @Serializable
    private data class Hourly(
        val time: List<String>,
        val temperature_2m: List<Double?>,
        val precipitation_probability: List<Double?>,
        val precipitation: List<Double?>,
        val weather_code: List<Int?>,
        val relative_humidity_2m: List<Double?>,
        val wind_speed_10m: List<Double?>,
        val wind_direction_10m: List<Double?>,
        val wind_gusts_10m: List<Double?>,
        val uv_index: List<Double?>,
    )

    @Serializable
    private data class Daily(
        val time: List<String>,
        val weather_code: List<Int?>,
        val temperature_2m_max: List<Double?>,
        val temperature_2m_min: List<Double?>,
        val uv_index_max: List<Double?>,
    )

    @Serializable
    private data class Response(val hourly: Hourly, val daily: Daily)

    suspend fun forecast(lat: Double, lon: Double, fresh: Boolean = false): Forecast = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, lat, lon) +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,relative_humidity_2m," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
            "&timezone=Asia%2FJerusalem&forecast_days=7"
        val request = Request.Builder().url(url)
            .apply { if (fresh) cacheControl(okhttp3.CacheControl.Builder().noCache().build()) }
            .build()
        val r = http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            json.decodeFromString(Response.serializer(), res.body.string())
        }
        val h = r.hourly
        val hours = h.time.indices.map { i ->
            Hour(
                t = h.time[i].replace('T', ' '),
                code = imsCode(h.weather_code[i]),
                temp = h.temperature_2m[i],
                rainChance = h.precipitation_probability[i],
                rain = h.precipitation[i],
                rh = h.relative_humidity_2m[i],
                wind = h.wind_speed_10m[i],
                gust = h.wind_gusts_10m[i],
                windDir = h.wind_direction_10m[i],
                uv = h.uv_index[i],
            )
        }
        val d = r.daily
        val days = d.time.indices.map { i ->
            Day(d.time[i], imsCode(d.weather_code[i]), d.temperature_2m_min[i], d.temperature_2m_max[i], d.uv_index_max[i])
        }
        Forecast(id = CURRENT_ID, hourly = hours, daily = days, source = Forecast.SOURCE_OPEN_METEO)
    }

    /** WMO weather codes → the IMS codes the app's icons and texts use. */
    private fun imsCode(wmo: Int?): Int = when (wmo) {
        0 -> 1250
        1, 2 -> 1220
        3 -> 1230
        45, 48 -> 1160
        51, 53, 55, 56, 57 -> 1560
        61, 63, 65, 80, 81, 82 -> 1140
        66, 67 -> 1080
        71, 73, 77 -> 1070
        75, 85, 86 -> 1060
        95, 96, 99 -> 1020
        else -> 1230
    }
}
