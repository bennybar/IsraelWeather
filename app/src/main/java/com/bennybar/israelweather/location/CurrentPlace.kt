package com.bennybar.israelweather.location

import com.bennybar.israelweather.app

/** Resolves the device location to the nearest IMS location and stores it as the current place. */
object CurrentPlace {
    enum class Result { OK, NO_PERMISSION, NO_FIX, OUTSIDE_ISRAEL, NO_PLACES }

    suspend fun resolve(fresh: Boolean = true): Result {
        if (!app.location.hasPermission()) return Result.NO_PERMISSION
        val loc = (if (fresh) app.location.current() else app.location.lastKnown()) ?: return Result.NO_FIX
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
