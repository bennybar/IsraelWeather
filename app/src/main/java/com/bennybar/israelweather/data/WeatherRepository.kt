package com.bennybar.israelweather.data

import android.content.Context
import com.bennybar.israelweather.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Loading state of one piece of data: the last good value plus any error from the latest attempt. */
data class Loadable<T>(val value: T? = null, val fetchedAt: Long = 0, val loading: Boolean = false, val error: Throwable? = null)

/**
 * Network + disk cache for everything the app and widgets show. Each value is
 * kept on disk (filesDir/cache-*.json) so the app paints instantly and widgets
 * work offline; network refreshes are skipped while the copy is fresh.
 */
class WeatherRepository(
    private val context: Context,
    private val api: Api,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "weather").apply { mkdirs() }
    private val mutex = Mutex()

    private val _places = MutableStateFlow(Loadable<List<Place>>())
    val places: StateFlow<Loadable<List<Place>>> = _places.asStateFlow()

    private val _forecasts = MutableStateFlow<Map<Int, Loadable<Forecast>>>(emptyMap())
    val forecasts: StateFlow<Map<Int, Loadable<Forecast>>> = _forecasts.asStateFlow()

    private val _warnings = MutableStateFlow(Loadable<List<Warning>>())
    val warnings: StateFlow<Loadable<List<Warning>>> = _warnings.asStateFlow()

    private val _now = MutableStateFlow<Map<Int, Observation>>(emptyMap())
    val now: StateFlow<Map<Int, Observation>> = _now.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            read("places", ListSerializer(Place.serializer()))?.let { s -> _places.update { it.copy(value = s.data, fetchedAt = s.fetchedAt) } }
            read("warnings", ListSerializer(Warning.serializer()))?.let { s -> _warnings.update { it.copy(value = s.data, fetchedAt = s.fetchedAt) } }
        }
    }

    fun place(id: Int): Place? = _places.value.value?.firstOrNull { it.id == id }

    /** The IMS location list changes rarely: refresh weekly. */
    suspend fun refreshPlaces(force: Boolean = false) {
        ensurePlacesLoaded()
        val cur = _places.value
        if (!force && cur.value != null && age(cur.fetchedAt) < WEEK) return
        _places.update { it.copy(loading = true, error = null) }
        runCatching { api.places() }
            .onSuccess { list ->
                val now = System.currentTimeMillis()
                _places.value = Loadable(list, now)
                write("places", ListSerializer(Place.serializer()), Stamped(now, list))
            }
            .onFailure { e -> _places.update { it.copy(loading = false, error = e) } }
    }

    /** Disk copy of the places list, for callers that run before the init read (workers, widgets). */
    suspend fun ensurePlacesLoaded(): List<Place> {
        _places.value.value?.let { return it }
        val s = withContext(Dispatchers.IO) { read("places", ListSerializer(Place.serializer())) } ?: return emptyList()
        _places.update { if (it.value == null) it.copy(value = s.data, fetchedAt = s.fetchedAt) else it }
        return s.data
    }

    /** Latest forecast for [id] from memory or disk, without touching the network. */
    suspend fun cachedForecast(id: Int): Loadable<Forecast>? {
        _forecasts.value[id]?.takeIf { it.value != null }?.let { return it }
        val s = withContext(Dispatchers.IO) { read("forecast-$id", Forecast.serializer()) } ?: return null
        val l = Loadable(s.data, s.fetchedAt)
        _forecasts.update { m -> if (m[id]?.value == null) m + (id to l) else m }
        return l
    }

    /** IMS issues forecasts a few times a day; 20 minutes keeps us current without hammering the proxy. */
    suspend fun refreshForecast(id: Int, force: Boolean = false): Boolean {
        val cached = cachedForecast(id)
        if (!force && cached != null && age(cached.fetchedAt) < FORECAST_TTL) return true
        _forecasts.update { m -> m + (id to (m[id] ?: Loadable()).copy(loading = true, error = null)) }
        return runCatching { api.forecast(id, fresh = force) }
            .onSuccess { f ->
                val now = System.currentTimeMillis()
                _forecasts.update { m -> m + (id to Loadable(f, now)) }
                withContext(Dispatchers.IO) { write("forecast-$id", Forecast.serializer(), Stamped(now, f)) }
                scope.launch { Widgets.updateAll(context) }
            }
            .onFailure { e -> _forecasts.update { m -> m + (id to (m[id] ?: Loadable()).copy(loading = false, error = e)) } }
            .isSuccess
    }

    suspend fun refreshWarnings(force: Boolean = false) {
        val cur = _warnings.value
        if (!force && cur.value != null && age(cur.fetchedAt) < WARNINGS_TTL) return
        _warnings.update { it.copy(loading = true, error = null) }
        runCatching { api.warnings(fresh = force) }
            .onSuccess { list ->
                val now = System.currentTimeMillis()
                _warnings.value = Loadable(list, now)
                withContext(Dispatchers.IO) { write("warnings", ListSerializer(Warning.serializer()), Stamped(now, list)) }
            }
            .onFailure { e -> _warnings.update { it.copy(loading = false, error = e) } }
    }

    /** Active warnings for an IMS region (a place's `rid`). */
    fun warningsFor(rid: Int, all: List<Warning>? = _warnings.value.value): List<Warning> =
        all.orEmpty().filter { rid in it.regions && it.isActive() }

    /** Live station reading; quietly absent until the proxy has an IMS token. */
    suspend fun refreshNow(id: Int) {
        runCatching { api.now(id) }.onSuccess { o -> _now.update { it + (id to o) } }
    }

    suspend fun radar(): Radar = api.radar()

    private fun age(t: Long) = System.currentTimeMillis() - t

    private fun <T> read(name: String, ser: KSerializer<T>): Stamped<T>? = runCatching {
        val f = File(dir, "cache-$name.json")
        if (!f.exists()) null else json.decodeFromString(Stamped.serializer(ser), f.readText())
    }.getOrNull()

    private suspend fun <T> write(name: String, ser: KSerializer<T>, value: Stamped<T>) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val tmp = File(dir, "cache-$name.json.tmp")
            tmp.writeText(json.encodeToString(Stamped.serializer(ser), value))
            tmp.renameTo(File(dir, "cache-$name.json"))
        }
    }

    companion object {
        const val FORECAST_TTL = 20 * 60_000L
        const val WARNINGS_TTL = 5 * 60_000L
        const val WEEK = 7 * 24 * 3_600_000L
    }
}
