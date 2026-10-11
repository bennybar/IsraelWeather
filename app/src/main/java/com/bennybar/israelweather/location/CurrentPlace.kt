package com.bennybar.israelweather.location

import android.location.Location
import com.bennybar.israelweather.app
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
        app.settings.setCurrentPlace(place.id)
        return Result.OK
    }
}
