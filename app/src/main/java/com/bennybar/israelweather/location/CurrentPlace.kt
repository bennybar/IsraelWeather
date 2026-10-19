package com.bennybar.israelweather.location

import android.location.Location
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.CURRENT_ID
import com.bennybar.israelweather.data.IMS_RADIUS_KM
import com.bennybar.israelweather.data.Localized
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.widget.Widgets
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/** Resolves the device location to the nearest IMS location and stores it as the current place. */
object CurrentPlace {
    enum class Result { LOCATING, OK, NO_PERMISSION, LOCATION_OFF, NO_FIX, OUTSIDE_ISRAEL, NO_PLACES }

    private val _status = MutableStateFlow<Result?>(null)

    /** Latest outcome, shown live by the forecast page (LOCATING only while there's nothing to show yet). */
    val status: StateFlow<Result?> = _status.asStateFlow()

    private var running: CompletableDeferred<Result>? = null

    /** Anything newer than this is shown straight away (and refined in the background). */
    private const val USABLE_MS = 24 * 3_600_000L
    private const val FRESH_MS = 10 * 60_000L

    /**
     * [fresh] = false: the last known fix only (no new fix), for when one isn't allowed.
     * [background]: low power, and reuse any fix from the last 30 minutes.
     * Calls made while a lookup is running share its result instead of queueing.
     */
    suspend fun resolve(fresh: Boolean = true, background: Boolean = false): Result {
        val mine = CompletableDeferred<Result>()
        val current = synchronized(this) { running ?: mine.also { running = it } }
        if (current !== mine) {
            return try {
                current.await()
            } catch (e: CancellationException) {
                // The lookup we joined was cancelled with its caller: run our own.
                currentCoroutineContext().ensureActive()
                resolve(fresh, background)
            }
        }
        try {
            val r = try {
                lookup(fresh, background)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.NO_FIX // e.g. Play Services unavailable or failing: never crash or hang callers
            }
            _status.value = r
            mine.complete(r)
            return r
        } catch (e: CancellationException) {
            mine.cancel(e)
            throw e
        } finally {
            synchronized(this) { if (running === mine) running = null }
        }
    }

    private suspend fun lookup(fresh: Boolean, background: Boolean): Result {
        if (!app.location.hasPermission()) return Result.NO_PERMISSION
        if (background) {
            val loc = (if (fresh) app.location.current(maxAgeMs = 30 * 60_000L, lowPower = true) else app.location.lastKnown())
            return loc?.let { apply(it) } ?: Result.NO_FIX
        }
        // Foreground: show whatever recent fix the phone already has right away,
        // then refine with a new one if it's more than a few minutes old.
        val last = app.location.lastKnown()?.takeIf { System.currentTimeMillis() - it.time < USABLE_MS }
        if (last != null) {
            val first = apply(last)
            _status.value = first
            if (!fresh || System.currentTimeMillis() - last.time < FRESH_MS) return first
            return app.location.current()?.let { apply(it) } ?: first
        }
        if (!fresh) return Result.NO_FIX
        if (app.settings.value.currentPlace == null) _status.value = Result.LOCATING
        val loc = app.location.current() ?: return if (app.location.isEnabled()) Result.NO_FIX else Result.LOCATION_OFF
        return apply(loc)
    }

    private suspend fun apply(loc: Location): Result {
        var places = app.repo.ensurePlacesLoaded()
        if (places.isEmpty()) {
            app.repo.refreshPlaces()
            places = app.repo.places.value.value.orEmpty()
        }
        if (places.isEmpty()) return Result.NO_PLACES
        val place = LocationResolver.nearest(places, loc.latitude, loc.longitude) ?: return Result.OUTSIDE_ISRAEL
        // ~100 m precision is plenty, and keeps the exact spot out of storage.
        val lat = Math.round(loc.latitude * 1000) / 1000.0
        val lon = Math.round(loc.longitude * 1000) / 1000.0
        // Rounded up, so the IMS_RADIUS_KM cutoff matches searched places' (3.4 km is beyond 3).
        val km = ceil(LocationResolver.distanceKm(place, lat, lon)).toInt()
        val before = app.settings.value
        // Beyond IMS_RADIUS_KM the forecast is Open-Meteo's for this exact spot, named after its town.
        val town = if (km > IMS_RADIUS_KM) {
            val he = app.location.town(lat, lon, Locale.forLanguageTag("he"))
            val en = app.location.town(lat, lon, Locale.ENGLISH)
            if (he == null && en == null) null else Localized(he ?: en, en ?: he)
        } else {
            null
        }
        app.settings.setCurrentLocation(place.id, lat, lon, km, town)
        // Moved noticeably while on the exact-location forecast: fetch the new spot.
        val moved = before.currentLat == null || before.currentLon == null ||
            LocationResolver.distanceKm(Place(0, "", "", before.currentLat, before.currentLon, 0), lat, lon) > 1.0
        if (moved && app.settings.value.currentForecastId == CURRENT_ID) app.repo.refreshForecast(CURRENT_ID, force = true)
        // "My location" widgets now point at another forecast (or had none yet, e.g. added before the
        // first fix): redraw them, even when that forecast is already cached and nothing is fetched.
        if (before.currentForecastId != app.settings.value.currentForecastId) Widgets.updateAll(app.context)
        return Result.OK
    }
}
