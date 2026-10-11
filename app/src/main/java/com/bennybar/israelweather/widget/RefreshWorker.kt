package com.bennybar.israelweather.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bennybar.israelweather.app
import com.bennybar.israelweather.location.CurrentPlace
import java.util.concurrent.TimeUnit

/** Periodic background refresh: forecasts for saved/current/widget places + warnings, then redraw widgets. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val s = app.settings.value
        // Background fixes need background-location permission, which we don't ask
        // for: the last known location (from any app) is good enough here.
        if (s.useLocation) runCatching { CurrentPlace.resolve(fresh = false) }
        app.repo.refreshPlaces()
        val ids = s.places + listOfNotNull(app.settings.value.currentPlace) + Widgets.placeIds(applicationContext)
        ids.toSet().forEach { app.repo.refreshForecast(it, force = true) }
        app.repo.refreshWarnings(force = true)
        Widgets.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "refresh"

        /** [minutes] = 0 turns background refresh off. WorkManager's minimum interval is 15 minutes. */
        fun schedule(context: Context, minutes: Int) {
            val wm = WorkManager.getInstance(context)
            if (minutes <= 0) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
