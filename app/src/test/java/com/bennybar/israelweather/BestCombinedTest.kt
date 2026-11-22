package com.bennybar.israelweather

import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.OpenMeteo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
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
    }
}
