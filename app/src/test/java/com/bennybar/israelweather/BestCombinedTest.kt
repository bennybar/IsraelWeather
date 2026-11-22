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

    /** A made-up wet response: best match says cloudy and dry, the rain models say wet; humidity missing. */
    @Test
    fun keepsTheBlendConsistent() = runBlocking {
        val hours = (0 until 48).map { "2026-12-%02dT%02d:00".format(1 + it / 24, it % 24) }
        fun col(v: (Int) -> Any?) = hours.indices.joinToString(",", "[", "]") { v(it)?.toString() ?: "null" }
        val wet = 30 until 36 // day 2, 06:00-11:00
        val body = """{"hourly":{"time":${hours.joinToString(",", "[", "]") { "\"$it\"" }},
            "temperature_2m_best_match":${col { 10 + it % 24 * 0.5 }},"temperature_2m_ecmwf_aifs025_single":${col { 12 + it % 24 * 0.5 }},
            "precipitation_best_match":${col { 0 }},"precipitation_gfs_global":${col { if (it in wet) 1.2 else 0 }},
            "precipitation_meteofrance_arpege_world":${col { if (it < 40) (if (it in wet) 0.9 else 0) else null }},"precipitation_icon_global":${col { if (it in wet) 0.6 else 0 }},
            "precipitation_probability_best_match":${col { 10 }},"precipitation_probability_gfs_global":${col { 80 }},"precipitation_probability_icon_global":${col { 60 }},
            "weather_code_best_match":${col { 3 }},
            "wind_speed_10m_best_match":${col { 5 }},"wind_speed_10m_gfs_global":${col { 30 }},"wind_speed_10m_meteofrance_arpege_world":${col { 20 }},
            "wind_direction_10m_best_match":${col { 270 }},"wind_gusts_10m_best_match":${col { 15 }},"uv_index_best_match":${col { 2 }},
            "cloud_cover_low_best_match":${col { 90 }},"cloud_cover_mid_best_match":${col { 0 }},"cloud_cover_high_best_match":${col { 0 }}},
          "daily":{"time":["2026-12-01","2026-12-02"],"weather_code_best_match":[3,3],
            "temperature_2m_max_best_match":[30,30],"temperature_2m_min_best_match":[0,0],"uv_index_max_best_match":[2,2]},
          "current":{"time":"2026-12-02T07:15","interval":900,"temperature_2m":14,"weather_code":3,"wind_speed_10m":5,"wind_gusts_10m":15,"wind_direction_10m":270}}"""
        val fake = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val f = OpenMeteo(fake, json).forecast(31.8, 34.8, model = OpenMeteo.BEST)
        // Wet blended hours get rain icons even though best match says cloudy; dry ones stay cloudy.
        val rainCodes = f.hourly.filter { it.t.substring(0, 13) in hours.slice(wet).map { h -> h.replace('T', ' ').substring(0, 13) } }.map { it.code }.toSet()
        val dryCode = f.hourly[0].code
        assertTrue("wet hours $rainCodes vs dry $dryCode", rainCodes.none { it == dryCode })
        assertEquals(dryCode, f.daily[0].code)
        assertTrue(f.daily[1].code != dryCode)
        assertEquals(0.9, f.hourly[32].rain!!, 0.001) // mean of 1.2, 0.9, 0.6
        // Wind blended (25), gusts lifted to it; "now" uses the blended wind of its hour.
        assertEquals(25.0, f.hourly[0].wind!!, 0.001)
        assertTrue(f.hourly.all { it.gust!! >= it.wind!! })
        assertEquals(25.0, f.now!!.wind!!, 0.001)
        assertEquals(25.0, f.now!!.gust!!, 0.001)
        // The day's high and low from the blended hours (11 + 11.5 = 22.5 max, 11 min), not best match's 30 and 0.
        assertEquals(22.5, f.daily[0].max!!, 0.001)
        assertEquals(11.0, f.daily[0].min!!, 0.001)
        // A missing column (humidity) is empty, not a crash.
        assertTrue(f.hourly.all { it.rh == null })
    }
}
