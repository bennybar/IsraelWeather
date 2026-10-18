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
/** A place from Open-Meteo's search; [region] is the district, in Hebrew. */
data class GeoPlace(val id: Int, val he: String, val en: String, val region: String?, val lat: Double, val lon: Double)

/** US AQI (0–500) and particles in µg/m³. */
data class AirQuality(val aqi: Int?, val pm10: Double?, val pm25: Double?, val dust: Double?)

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

    suspend fun forecast(lat: Double, lon: Double, fresh: Boolean = false, id: Int = CURRENT_ID): Forecast = withContext(Dispatchers.IO) {
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
        Forecast(id = id, hourly = hours, daily = days, source = Forecast.SOURCE_OPEN_METEO)
    }

    @Serializable
    private data class GeoResult(
        val id: Int,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val admin1: String? = null,
        val feature_code: String? = null,
    )

    @Serializable
    private data class GeoResponse(val results: List<GeoResult> = emptyList())

    /**
     * Places in Israel matching [query] (Open-Meteo / GeoNames), with Hebrew and English names:
     * one lookup per language, merged by id.
     */
    suspend fun searchIsrael(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        fun lookup(lang: String): List<GeoResult> {
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("geocoding-api.open-meteo.com").addPathSegments("v1/search")
                .addQueryParameter("name", query).addQueryParameter("count", "20")
                .addQueryParameter("language", lang).addQueryParameter("countryCode", "IL").build()
            return http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw HttpException(res.code)
                json.decodeFromString(GeoResponse.serializer(), res.body.string()).results
            }
        }
        val he = lookup("he")
        val en = lookup("en").associateBy { it.id }
        he.map { r -> GeoPlace(r.id, r.name, en[r.id]?.name ?: r.name, r.admin1, r.latitude, r.longitude) }
    }

    @Serializable
    private data class AirCurrent(val us_aqi: Double? = null, val pm10: Double? = null, val pm2_5: Double? = null, val dust: Double? = null)

    @Serializable
    private data class AirResponse(val current: AirCurrent)

    private var air: Triple<String, Long, AirQuality>? = null

    /** Current air quality (CAMS model) at a point; cached for 30 minutes per ~1 km. */
    suspend fun airQuality(lat: Double, lon: Double): AirQuality = withContext(Dispatchers.IO) {
        val key = "%.2f,%.2f".format(Locale.ROOT, lat, lon)
        air?.takeIf { it.first == key && System.currentTimeMillis() - it.second < 30 * 60_000L }?.let { return@withContext it.third }
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, lat, lon) +
            "&current=us_aqi,pm10,pm2_5,dust&timezone=Asia%2FJerusalem"
        val c = http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            json.decodeFromString(AirResponse.serializer(), res.body.string()).current
        }
        AirQuality(c.us_aqi?.toInt(), c.pm10, c.pm2_5, c.dust).also { air = Triple(key, System.currentTimeMillis(), it) }
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
        95, 96, 97, 99 -> 1020
        else -> 1230
    }
}
