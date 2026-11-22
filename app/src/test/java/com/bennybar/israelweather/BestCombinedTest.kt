package com.bennybar.israelweather

import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.OpenMeteo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** "Best Combined" against the live API (skipped offline): temperature is best match and AIFS averaged. */
class BestCombinedTest {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun combinesModels() = runBlocking {
        val lat = 31.877; val lon = 34.739
        val f = try { OpenMeteo(http, json).forecast(lat, lon, model = OpenMeteo.BEST) } catch (e: Exception) { null }
        assumeTrue("network needed", f != null)
        f!!
        assertEquals(Forecast.SOURCE_BEST, f.source)
        assertEquals(168, f.hourly.size)
        assertEquals(7, f.daily.size)
        assertNotNull(f.now)
        assertTrue(f.hourly.all { it.temp != null && it.uv != null })
        // The same hours from the raw multi-model response: temperature must be the two models' mean.
        val raw = http.newCall(Request.Builder().url(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m" +
                "&models=best_match,ecmwf_aifs025_single&timezone=Asia%2FJerusalem&forecast_days=7").build()).execute().use { it.body.string() }
        val h = json.parseToJsonElement(raw).jsonObject["hourly"]!!.jsonObject
        val a = h["temperature_2m_best_match"]!!.jsonArray.map { it.jsonPrimitive.doubleOrNull }
        val b = h["temperature_2m_ecmwf_aifs025_single"]!!.jsonArray.map { it.jsonPrimitive.doubleOrNull }
        for (i in listOf(0, 50, 120)) {
            assertEquals((a[i]!! + b[i]!!) / 2, f.hourly[i].temp!!, 0.051)
        }
        // Consistent with the blend: gusts never below the speed, the day's high and low from its hours.
        assertTrue(f.hourly.all { it.gust == null || it.wind == null || it.gust!! >= it.wind!! })
        assertTrue(f.now!!.gust == null || f.now!!.wind == null || f.now!!.gust!! >= f.now!!.wind!!)
        for (day in f.daily) {
            val temps = f.hourly.filter { it.t.startsWith(day.date) }.mapNotNull { it.temp }
            assertEquals(temps.max(), day.max!!, 0.001)
            assertEquals(temps.min(), day.min!!, 0.001)
        }
    }

    /**
     * A made-up six-day response (rain is rounded to the 0.1 mm the app shows).
     * Day 1: best match's day says showers (80) from rain at night (01–04); the daytime has only
     * 0.07 mm an hour (0.77 mm, no wet hour), so the day stays dry. Best match's drizzle at 20:00
     * has no blended rain, so it turns to sky.
     * Day 2: best match says fog all day; rain (1.2 mm) at 09–10, 2.4 mm in all: a rain day.
     * Day 3: best match's day says rain (63) but only two daytime hours are wet (09, and 11 at a
     * mean of 0.19999… that must count as 0.2); 10:00 has 0.14 mm (shown 0.1, stays dry), and
     * Météo-France is gone from 12:00. 1.0 mm in the daytime: light rain, not best match's rain.
     * Day 4: best match's day says thunder (95), with a 3 mm storm at 09: the day keeps its thunder.
     * Day 5: best match says showers (80) at 10–13 but the blend has 0.1 mm an hour: sky, hours and day.
     * Its night hours: rain (63) at 0.5 mm → light rain, drizzle (53) at 1.2 mm → rain, no code at
     * 0.4 mm → light rain, freezing rain (66) at 0.4 mm → sleet, and at 0.1 mm → sky.
     * Day 6: best match's day says thunder (95) from a storm at 02:00; the daytime has two 0.3 mm
     * hours: a light-rain day, not a thunderstorm one.
     * Wind and gusts vary by hour; humidity is missing and mid cloud too short.
     */
    @Test
    fun keepsTheBlendConsistent() = runBlocking {
        val n = 144
        val hours = (0 until n).map { "2026-12-%02dT%02d:00".format(1 + it / 24, it % 24) }
        fun col(v: (Int) -> Any?) = (0 until n).joinToString(",", "[", "]") { v(it)?.toString() ?: "null" }
        val rainAll = mapOf(1 to 1.5, 2 to 1.5, 3 to 1.5, 4 to 1.5, 33 to 1.2, 34 to 1.2, 57 to 0.6, 58 to 0.14, 81 to 3.0, 106 to 0.1, 107 to 0.1, 108 to 0.1, 109 to 0.1,
            96 to 0.5, 97 to 1.2, 98 to 0.4, 99 to 0.4, 100 to 0.1,
            122 to 2.0, 129 to 0.3, 130 to 0.3) +
            (7..17).associateWith { 0.07 }
        // 11:00 on day 3: 0.3, 0.3 and 0.0, a mean of 0.19999…; 14:00: GFS 0.2 and ICON 0.0 only.
        fun gfs(i: Int) = when (i) { 59 -> 0.3; 62 -> 0.2; else -> rainAll[i] ?: 0 }
        fun mf(i: Int) = when { i in 60..71 -> null; i == 59 -> 0.3; else -> rainAll[i] ?: 0 }
        fun icon(i: Int) = when (i) { 59 -> 0.0; 62 -> 0.0; else -> rainAll[i] ?: 0 }
        val body = """{"hourly":{"time":${hours.joinToString(",", "[", "]") { "\"$it\"" }},
            "temperature_2m_best_match":${col { 10 + it % 24 * 0.5 }},"temperature_2m_ecmwf_aifs025_single":${col { 12 + it % 24 * 0.5 }},
            "precipitation_best_match":${col { 0 }},"precipitation_gfs_global":${col { gfs(it) }},
            "precipitation_meteofrance_arpege_world":${col { mf(it) }},"precipitation_icon_global":${col { icon(it) }},
            "precipitation_probability_gfs_global":${col { 80 }},"precipitation_probability_icon_global":${col { 60 }},
            "weather_code_best_match":${col { if (it == 96) 63 else if (it == 97) 53 else if (it == 98) null else if (it in 99..100) 66 else if (it == 20) 53 else if (it == 81 || it == 122) 95 else if (it in 106..109) 80 else if (it / 24 == 1) 45 else if (it / 24 == 0) 0 else 3 }},
            "wind_speed_10m_best_match":${col { 1 }},"wind_speed_10m_gfs_global":${col { 20 + it }},"wind_speed_10m_meteofrance_arpege_world":${col { 10 + it }},
            "wind_gusts_10m_best_match":${col { 1 }},"wind_gusts_10m_gfs_global":${col { 30 + it }},"wind_gusts_10m_meteofrance_arpege_world":${col { 5 }},
            "wind_direction_10m_best_match":${col { 270 }},"uv_index_best_match":${col { 2 }},
            "cloud_cover_low_best_match":${col { 90 }},"cloud_cover_mid_best_match":[0,0,0,0,0,0,0,0,0,0],"cloud_cover_high_best_match":${col { 0 }}},
          "daily":{"time":["2026-12-01","2026-12-02","2026-12-03","2026-12-04","2026-12-05","2026-12-06"],"weather_code_best_match":[80,45,63,95,80,95],
            "temperature_2m_max_best_match":[30,30,30,30,30,30],"temperature_2m_min_best_match":[0,0,0,0,0,0],"uv_index_max_best_match":[2,2,2,2,2,2]},
          "current":{"time":"2026-12-01T03:15","interval":900,"temperature_2m":20,"weather_code":0,"wind_speed_10m":1,"wind_gusts_10m":1,"wind_direction_10m":270,"cloud_cover_low":90}}"""
        val fake = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val f = OpenMeteo(fake, json).forecast(31.8, 34.8, model = OpenMeteo.BEST)
        val rain = 1140; val light = 1560; val fog = 1160; val cloudy = 1230; val storm = 1020
        // Hours: 1 mm+ is rain, 0.2 mm+ light rain (also over fog), under 0.2 mm keeps its sky, and
        // best match's drizzle without blended rain becomes sky.
        assertEquals(cloudy, f.hourly[0].code)
        assertTrue((1..4).all { f.hourly[it].code == rain })
        assertEquals(cloudy, f.hourly[12].code)
        assertEquals(cloudy, f.hourly[20].code)
        assertEquals(fog, f.hourly[30].code)
        assertEquals(rain, f.hourly[33].code)
        assertEquals(light, f.hourly[57].code)
        assertEquals(cloudy, f.hourly[58].code)
        assertEquals(light, f.hourly[59].code)
        assertEquals(cloudy, f.hourly[62].code)
        assertEquals(storm, f.hourly[81].code)
        // Days: a night shower with a damp daytime stays dry; daytime rain sets light rain or rain by
        // amount, over fog and over best match's own rain code; a storm day keeps its thunder.
        assertEquals(cloudy, f.daily[0].code)
        assertEquals(rain, f.daily[1].code)
        assertEquals(light, f.daily[2].code)
        assertEquals(storm, f.daily[3].code)
        assertTrue((106..109).all { f.hourly[it].code == cloudy })
        assertEquals(listOf(light, rain, light, 1080, cloudy), (96..100).map { f.hourly[it].code })
        assertEquals(cloudy, f.daily[4].code)
        assertEquals(storm, f.hourly[122].code)
        assertEquals(light, f.hourly[129].code)
        assertEquals(light, f.daily[5].code)
        // Rain is the models' mean, of the ones left once Météo-France ends.
        assertEquals(1.5, f.hourly[2].rain!!, 0.001)
        assertEquals(0.1, f.hourly[62].rain!!, 0.001)
        assertEquals(0.1, f.hourly[58].rain!!, 0.001)
        // Wind and gusts are GFS and Météo-France's; gusts lifted to the wind only where below it.
        assertEquals(18.0, f.hourly[3].wind!!, 0.001)
        assertEquals(19.0, f.hourly[3].gust!!, 0.001)
        assertEquals(55.0, f.hourly[40].gust!!, 0.001)
        // "Now" (03:15) takes 03:00's blend: wind, gust, rain code, and best match's temperature + 1°.
        assertEquals(18.0, f.now!!.wind!!, 0.001)
        assertEquals(19.0, f.now!!.gust!!, 0.001)
        assertEquals(rain, f.now!!.code)
        assertEquals(21.0, f.now!!.temp!!, 0.001)
        // The day's high and low from the blended hours, not best match's 30 and 0.
        assertEquals(22.5, f.daily[0].max!!, 0.001)
        assertEquals(11.0, f.daily[0].min!!, 0.001)
        // Missing or short columns are empty, not a crash.
        assertEquals(n, f.hourly.size)
        assertTrue(f.hourly.all { it.rh == null })
    }
}
