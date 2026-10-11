package com.bennybar.israelweather.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.bennybar.israelweather.data.Place
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Device location via the platform LocationManager (no Play Services, like
 * Ilay). A coarse fix is plenty: we only need the nearest IMS location.
 */
class LocationResolver(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A fresh fix (up to 15 s), falling back to the last known one. Null without permission. */
    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val fix = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    try {
                        lm.getCurrentLocation(p, null, executor) { loc -> if (cont.isActive) cont.resume(loc) }
                    } catch (_: SecurityException) {
                        cont.resume(null)
                    }
                }
            }
            if (fix != null) return fix
        }
        return lastKnown()
    }

    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return lm.allProviders.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.maxByOrNull { it.time }
    }

    companion object {
        /** Farther than this from every IMS location means we're outside Israel. */
        const val MAX_KM = 40.0

        fun nearest(places: List<Place>, lat: Double, lon: Double): Place? {
            val k = cos(Math.toRadians(lat))
            val best = places.minByOrNull { p -> sq(p.lat - lat) + sq((p.lon - lon) * k) } ?: return null
            val km = sqrt(sq(best.lat - lat) + sq((best.lon - lon) * k)) * 111.0
            return best.takeIf { km <= MAX_KM }
        }

        private fun sq(x: Double) = x * x
    }
}
