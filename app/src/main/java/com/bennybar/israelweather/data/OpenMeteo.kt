package com.bennybar.israelweather.data

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    companion object {
        const val ECMWF = "ecmwf_ifs"
        const val GFS = "gfs_global"
        /** "Best Combined": several models in one request, each measure from the ones best at it. */
        const val BEST = "combined"
        // best_match must stay first: Open-Meteo answers `current` for the first model listed.
        /** Best Combined: daytime (07–17) blended rain that makes a day rainy, whatever its hour codes. */
        private const val WET_DAY_MM = 0.5
        /** Best Combined: blended rain in an hour that makes it light rain (1 mm: rain). */
        private const val WET_HOUR_MM = 0.2
        /** Slack for thresholds: a mean of tenths can land a hair under them (0.6 / 3 = 0.19999…). */
        private const val EPS = 1e-6
        /** Freezing drizzle and rain (WMO 56, 57, 66, 67): keep their sleet icon when wet. */
        private val FREEZING = setOf(56, 57, 66, 67)
        private const val COMBINED_MODELS = "best_match,ecmwf_aifs025_single,gfs_global,meteofrance_arpege_world,icon_global"
    }

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
        val cloud_cover_low: List<Double?> = emptyList(),
        val cloud_cover_mid: List<Double?> = emptyList(),
        val cloud_cover_high: List<Double?> = emptyList(),
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
    private data class Current(
        val time: String,
        val temperature_2m: Double? = null,
        val relative_humidity_2m: Double? = null,
        val weather_code: Int? = null,
        val wind_speed_10m: Double? = null,
        val wind_direction_10m: Double? = null,
        val wind_gusts_10m: Double? = null,
        val cloud_cover_low: Double? = null,
        val cloud_cover_mid: Double? = null,
        val cloud_cover_high: Double? = null,
    )

    @Serializable
    private data class Response(val hourly: Hourly, val daily: Daily, val current: Current? = null)

    /** ECMWF's 9 km IFS by default; [GFS] for NOAA's GFS (the best-graded model here, see tools/accuracy). */
    suspend fun forecast(lat: Double, lon: Double, fresh: Boolean = false, id: Int = CURRENT_ID, model: String = ECMWF): Forecast = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, lat, lon) +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,relative_humidity_2m," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index,cloud_cover_low,cloud_cover_mid,cloud_cover_high" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,uv_index_max" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
            "cloud_cover_low,cloud_cover_mid,cloud_cover_high" +
            "&models=${if (model == BEST) COMBINED_MODELS else model}&timezone=Asia%2FJerusalem&forecast_days=7"
        val request = Request.Builder().url(url)
            .apply { if (fresh) cacheControl(okhttp3.CacheControl.Builder().noCache().build()) }
            .build()
        val r = http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            val body = res.body.string()
            if (model == BEST) combine(json.parseToJsonElement(body).jsonObject) else json.decodeFromString(Response.serializer(), body)
        }
        // ECMWF has no UV index: take it from Open-Meteo's default blend (same hours and days).
        // GFS and best match carry their own, so they need no second request.
        val uv = if (model == ECMWF) runCatching { uvIndex(lat, lon, fresh) }.getOrNull() else null
        val h = r.hourly.copy(uv_index = uv?.hourly?.uv_index ?: r.hourly.uv_index)
        val dailyUv = uv?.daily?.uv_index_max ?: r.daily.uv_index_max
        val sky = h.time.indices.map { i -> effectiveCloud(h.cloud_cover_low.getOrNull(i), h.cloud_cover_mid.getOrNull(i), h.cloud_cover_high.getOrNull(i)) }
        val hours = h.time.indices.map { i ->
            Hour(
                t = h.time[i].replace('T', ' '),
                code = skyCode(h.weather_code[i], sky[i]),
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
            // Open-Meteo's daily code is the day's worst hour: one overcast hour makes the whole day
            // "cloudy". For plain sky codes, use the daytime (07–17) average cloud instead.
            val daytime = h.time.indices.filter { j -> h.time[j].startsWith(d.time[i]) && h.time[j].substring(11, 13).toInt() in 7..17 }
            val dayCloud = daytime.mapNotNull { sky[it] }.takeIf { it.isNotEmpty() }?.average()
            // Fog, drizzle, rain, snow or storms only when they last (3+ daytime hours): a single foggy
            // dawn hour made the whole day a fog icon. Otherwise the day's sky from its average cloud.
            val weatherHours = daytime.count { (h.weather_code[it] ?: 0) > 3 }
            // Best Combined: real daytime rain (0.5 mm+, at least one wet hour) makes the day light rain
            // (rain from 2 mm) however few hours it took; best match's daily code, often set by a night
            // shower, doesn't decide, and without such rain its rain code doesn't stand.
            val daytimeRain = daytime.sumOf { h.precipitation[it] ?: 0.0 }
            val wetDay = model == BEST && daytimeRain >= WET_DAY_MM - EPS &&
                daytime.any { (h.precipitation[it] ?: 0.0) >= WET_HOUR_MM - EPS }
            val code = d.weather_code[i]
            // Storms, snow and freezing rain count only in the daytime itself (a night thunderstorm
            // doesn't make the day one).
            fun isStormOrSnow(c: Int?) = c != null && (c >= 95 || c in 71..77 || c == 85 || c == 86 || c in FREEZING)
            val stormOrSnow = isStormOrSnow(code) && daytime.any { isStormOrSnow(h.weather_code[it]) }
            val codedRain = code != null && (code in 51..67 || code in 80..82)
            val dayCode = if (wetDay) {
                // Storms and snow keep their code; otherwise the amount decides (light rain or rain).
                skyCode(if (stormOrSnow) code else if (daytimeRain >= 2.0 - EPS) 61 else 51, dayCloud)
            } else if (model == BEST && (codedRain || (isStormOrSnow(code) && !stormOrSnow))) {
                skyCode(3, dayCloud) // best match's rain or night storm without daytime weather in the blend
            } else if ((code ?: 0) > 3 && weatherHours < 3 && dayCloud != null) {
                skyCode(3, dayCloud) // a plain sky code, so the cloud decides
            } else {
                skyCode(code, dayCloud)
            }
            Day(d.time[i], dayCode, d.temperature_2m_min[i], d.temperature_2m_max[i], dailyUv.getOrNull(i))
        }
        // Current conditions (15-minutely); rain chance and UV come from the hour they fall in.
        val now = r.current?.let { c -> nowHour(c, hours) }
        Forecast(id = id, hourly = hours, daily = days, source = when (model) {
            GFS -> Forecast.SOURCE_GFS
            BEST -> Forecast.SOURCE_BEST
            else -> Forecast.SOURCE_OPEN_METEO
        }, now = now)
    }

    /**
     * "Best Combined", from one multi-model response (fields suffixed with each model's name): each
     * measure averaged over the models that forecast it best 1–3 days ahead against Israeli airports
     * (tools/accuracy). Temperature: best match and ECMWF's AI model. Rain: GFS, Météo-France and ICON
     * (chance: GFS and ICON, the others give none). Wind speed and gusts: GFS and Météo-France. The
     * sky, humidity, wind direction and UV: best match, kept consistent with the blend (a wet blend
     * gets a rain code, gusts never below the speed, the day's high and low from the hours, "now"
     * shifted to the blend of its hour). An hour no chosen model covers falls back to best match; a
     * missing column is all nulls.
     */
    private fun combine(o: JsonObject): Response {
        val h = o["hourly"]!!.jsonObject
        val d = o["daily"]!!.jsonObject
        val hTimes = h["time"]!!.jsonArray.map { it.jsonPrimitive.content }
        val dTimes = d["time"]!!.jsonArray.map { it.jsonPrimitive.content }
        fun nums(obj: JsonObject, key: String, n: Int): List<Double?> {
            val a = obj[key] as? JsonArray
            return List(n) { i -> a?.getOrNull(i)?.jsonPrimitive?.doubleOrNull }
        }
        fun best(obj: JsonObject, field: String, n: Int) = nums(obj, "${field}_best_match", n)
        fun mean(obj: JsonObject, field: String, n: Int, vararg models: String): List<Double?> {
            val cols = models.map { nums(obj, "${field}_$it", n) }
            val fallback = best(obj, field, n)
            return List(n) { i -> cols.mapNotNull { it[i] }.takeIf { it.isNotEmpty() }?.average() ?: fallback[i] }
        }
        // The hour's code from its blended rain, for dry, foggy, missing and rain codes alike (0–3,
        // 45, 48, 51–67, 80–82): light rain from 0.2 mm, rain from 1 mm, else a plain sky code where
        // best match said rain (the cloud decides). Freezing rain keeps its code when wet; storms and
        // snow stay.
        fun wetCode(code: Int?, mm: Double?): Int? {
            if (mm == null) return code
            val rainCode = code != null && (code in 51..67 || code in 80..82)
            if (code in FREEZING) return if (mm >= WET_HOUR_MM - EPS) code else 3
            if (!(code == null || code <= 3 || code == 45 || code == 48 || rainCode)) return code
            return when {
                mm >= 1.0 - EPS -> 61
                mm >= WET_HOUR_MM - EPS -> 51
                rainCode -> 3
                else -> code
            }
        }
        val n = hTimes.size
        val nd = dTimes.size
        val bestTemp = best(h, "temperature_2m", n)
        val temp = mean(h, "temperature_2m", n, "best_match", "ecmwf_aifs025_single")
        // To the 0.1 mm the app shows, so the icons' thresholds match the amounts on screen.
        val rain = mean(h, "precipitation", n, "gfs_global", "meteofrance_arpege_world", "icon_global")
            .map { mm -> mm?.let { Math.round(it * 10) / 10.0 } }
        val wind = mean(h, "wind_speed_10m", n, "gfs_global", "meteofrance_arpege_world")
        val gusts = mean(h, "wind_gusts_10m", n, "gfs_global", "meteofrance_arpege_world").mapIndexed { i, g ->
            if (g != null && wind[i] != null) maxOf(g, wind[i]!!) else g
        }
        val hourly = Hourly(
            time = hTimes,
            temperature_2m = temp,
            precipitation_probability = mean(h, "precipitation_probability", n, "gfs_global", "icon_global"),
            precipitation = rain,
            weather_code = best(h, "weather_code", n).mapIndexed { i, c -> wetCode(c?.toInt(), rain[i]) },
            relative_humidity_2m = best(h, "relative_humidity_2m", n),
            wind_speed_10m = wind,
            wind_direction_10m = best(h, "wind_direction_10m", n),
            wind_gusts_10m = gusts,
            uv_index = best(h, "uv_index", n),
            cloud_cover_low = best(h, "cloud_cover_low", n),
            cloud_cover_mid = best(h, "cloud_cover_mid", n),
            cloud_cover_high = best(h, "cloud_cover_high", n),
        )
        val dayHours = dTimes.map { day -> hTimes.indices.filter { hTimes[it].startsWith(day) } }
        val bestMax = best(d, "temperature_2m_max", nd)
        val bestMin = best(d, "temperature_2m_min", nd)
        val daily = Daily(
            time = dTimes,
            // best match's; forecast() sets rainy days from the blended daytime rain (WET_DAY_MM).
            weather_code = best(d, "weather_code", nd).map { it?.toInt() },
            // The day's high and low from the blended hours, so they agree with the hourly row.
            temperature_2m_max = dTimes.indices.map { i -> dayHours[i].mapNotNull { temp[it] }.maxOrNull() ?: bestMax[i] },
            temperature_2m_min = dTimes.indices.map { i -> dayHours[i].mapNotNull { temp[it] }.minOrNull() ?: bestMin[i] },
            uv_index_max = best(d, "uv_index_max", nd),
        )
        // "Now" is best match's (15-minutely), moved onto the blend of the hour it falls in: its
        // temperature shifted by the blend's difference, the blended wind and gusts, a rain code if wet.
        val current = o["current"]?.let { json.decodeFromJsonElement(Current.serializer(), it) }?.let { c ->
            val slot = hTimes.indexOfLast { it <= c.time }
            if (slot < 0) return@let c
            val shift = if (temp[slot] != null && bestTemp[slot] != null) temp[slot]!! - bestTemp[slot]!! else 0.0
            val w = wind[slot] ?: c.wind_speed_10m
            c.copy(
                temperature_2m = c.temperature_2m?.plus(shift),
                weather_code = wetCode(c.weather_code, rain[slot]),
                wind_speed_10m = w,
                wind_gusts_10m = gusts[slot] ?: c.wind_gusts_10m?.let { g -> w?.let { maxOf(g, it) } ?: g },
            )
        }
        return Response(hourly, daily, current)
    }

    @Serializable
    private data class UvHourly(val uv_index: List<Double?> = emptyList())

    @Serializable
    private data class UvDaily(val uv_index_max: List<Double?> = emptyList())

    @Serializable
    private data class UvResponse(val hourly: UvHourly = UvHourly(), val daily: UvDaily = UvDaily())

    private fun uvIndex(lat: Double, lon: Double, fresh: Boolean): UvResponse {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, lat, lon) +
            "&hourly=uv_index&daily=uv_index_max&timezone=Asia%2FJerusalem&forecast_days=7"
        val request = Request.Builder().url(url).apply { if (fresh) cacheControl(okhttp3.CacheControl.Builder().noCache().build()) }.build()
        return http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            json.decodeFromString(UvResponse.serializer(), res.body.string())
        }
    }

    @Serializable
    private data class CurrentOnly(val current: Current)

    /**
     * ECMWF's current conditions at a point, for "now" at IMS places (IMS's hourly forecast is
     * coarse; this follows the actual sky more closely). Rain chance and UV from [hours].
     */
    suspend fun current(lat: Double, lon: Double, hours: List<Hour>): Hour? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, lat, lon) +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
            "cloud_cover_low,cloud_cover_mid,cloud_cover_high&models=ecmwf_ifs&timezone=Asia%2FJerusalem"
        val c = http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            json.decodeFromString(CurrentOnly.serializer(), res.body.string()).current
        }
        nowHour(c, hours)
    }

    private fun nowHour(c: Current, hours: List<Hour>): Hour {
        val t = c.time.replace('T', ' ')
        val slot = hours.lastOrNull { it.t <= t }
        return Hour(
            t = t,
            code = skyCode(c.weather_code, effectiveCloud(c.cloud_cover_low, c.cloud_cover_mid, c.cloud_cover_high)),
            temp = c.temperature_2m,
            rainChance = slot?.rainChance,
            rain = slot?.rain,
            rh = c.relative_humidity_2m,
            wind = c.wind_speed_10m,
            gust = c.wind_gusts_10m,
            windDir = c.wind_direction_10m,
            uv = slot?.uv,
        )
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
        // A Hebrew query finds nothing in English: look those names up by id (in parallel).
        coroutineScope {
            he.map { r ->
                async {
                    val english = en[r.id]?.name ?: runCatching { nameById(r.id, "en") }.getOrNull() ?: r.name
                    GeoPlace(r.id, r.name, english, r.admin1, r.latitude, r.longitude)
                }
            }.awaitAll()
        }
    }

    /** A place's name in [lang] by its GeoNames id; null if unknown. */
    suspend fun nameById(id: Int, lang: String): String? = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/get?id=$id&language=$lang"
        http.newCall(Request.Builder().url(url).build()).execute().use { res ->
            if (!res.isSuccessful) return@use null
            json.decodeFromString(GeoResult.serializer(), res.body.string()).name
        }
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

    /**
     * Cloud as it looks from the ground (%): the thickest layer, with high cloud at half weight,
     * since thin cirrus lets the sun through (Open-Meteo counts it fully, so its codes run cloudy).
     */
    private fun effectiveCloud(low: Double?, mid: Double?, high: Double?): Double? =
        if (low == null && mid == null && high == null) null else maxOf(low ?: 0.0, mid ?: 0.0, (high ?: 0.0) / 2)

    /** Plain sky codes (0–3) from [cloud] (when known); rain, fog, snow and storms as Open-Meteo says. */
    private fun skyCode(wmo: Int?, cloud: Double?): Int = when {
        wmo == null || wmo > 3 || cloud == null -> imsCode(wmo)
        cloud < 25 -> 1250
        cloud < 60 -> 1220
        else -> 1230
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
