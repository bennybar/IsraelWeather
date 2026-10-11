package com.bennybar.israelweather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HttpException(val code: Int) : IOException("HTTP $code")

/** The Israel Weather proxy (see proxy/ in the repo). */
class Api(private val http: OkHttpClient, private val json: Json, private val baseUrl: String) {

    suspend fun places() = get("/v1/locations", PlacesResponse.serializer()).locations
    suspend fun forecast(id: Int, fresh: Boolean = false) = get("/v1/forecast/$id", Forecast.serializer(), fresh)
    suspend fun warnings(fresh: Boolean = false) = get("/v1/warnings", WarningsResponse.serializer(), fresh).warnings
    suspend fun radar() = get("/v1/radar", Radar.serializer())
    suspend fun now(id: Int) = get("/v1/now/$id", Observation.serializer())

    /** [fresh] skips OkHttp's local cache (pull-to-refresh, widget refresh button). */
    private suspend fun <T> get(path: String, serializer: KSerializer<T>, fresh: Boolean = false): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path)
            .apply { if (fresh) cacheControl(CacheControl.Builder().noCache().build()) }
            .build()
        http.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw HttpException(res.code)
            json.decodeFromString(serializer, res.body.string())
        }
    }
}
