package com.bennybar.israelweather.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import com.bennybar.israelweather.data.Place
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Device location, kept cheap on battery. We only need the nearest of IMS's
 * forecast points (a few km apart), so city-level accuracy is plenty and GPS
 * is never used:
 *  1. a recent fix the phone already has (free),
 *  2. else Google Play Services' fused location at balanced / low power
 *     (Wi-Fi + cell, works indoors),
 *  3. else, on phones without Play Services, Android's network/fused providers.
 */
class LocationResolver(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val fused = runCatching {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
            LocationServices.getFusedLocationProviderClient(context)
        } else {
            null
        }
    }.getOrNull()

    private fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Any foreground location (approximate or precise). */
    fun hasPermission() = granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPrecise() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** "Allow all the time": lets widgets and background refresh follow the user. */
    fun hasBackground() = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** Location services switched on at all. */
    fun isEnabled() = runCatching { lm.isLocationEnabled }.getOrDefault(true)

    /**
     * A fix no older than [maxAgeMs]: the phone's last known one when recent
     * enough, otherwise a new one (20 s max). [lowPower] (background refresh)
     * settles for cell-level accuracy.
     */
    suspend fun current(maxAgeMs: Long = 10 * 60_000L, lowPower: Boolean = false): Location? {
        if (!hasPermission()) return null
        lastKnown()?.takeIf { System.currentTimeMillis() - it.time < maxAgeMs }?.let { return it }
        val fresh = withTimeoutOrNull(20_000) { if (fused != null) playServicesFix(maxAgeMs, lowPower) else platformFix() }
        return fresh ?: lastKnown()
    }

    private suspend fun playServicesFix(maxAgeMs: Long, lowPower: Boolean): Location? {
        val client = fused ?: return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (lowPower) Priority.PRIORITY_LOW_POWER else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(maxAgeMs)
            .setDurationMillis(20_000)
            .build()
        val cancel = CancellationTokenSource()
        return try {
            client.getCurrentLocation(request, cancel.token).await(cancel)
        } catch (_: SecurityException) {
            null
        }
    }

    /** Without Play Services: ask the network and fused providers at once (not GPS), first answer wins. */
    private suspend fun platformFix(): Location? {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter { p -> runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        return channelFlow { providers.forEach { p -> launch { platformFix(p)?.let { send(it) } } } }.firstOrNull()
    }

    private suspend fun platformFix(provider: String): Location? = callbackFlow {
        val cancel = CancellationSignal()
        try {
            lm.getCurrentLocation(provider, cancel, executor) { loc -> trySend(loc); close() }
        } catch (_: SecurityException) {
            close()
        } catch (_: IllegalArgumentException) {
            close() // provider doesn't exist on this device
        }
        awaitClose { cancel.cancel() }
    }.firstOrNull()

    /** The newest fix the phone already has, from Play Services or any platform provider. Costs nothing. */
    suspend fun lastKnown(): Location? {
        if (!hasPermission()) return null
        val gms = runCatching { fused?.lastLocation?.await() }.getOrNull()
        val platform = lm.allProviders.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        return (platform + listOfNotNull(gms)).maxByOrNull { it.time }
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
