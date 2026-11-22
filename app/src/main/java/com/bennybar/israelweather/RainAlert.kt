package com.bennybar.israelweather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.israelNow
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * "Rain from 15:00 in Yavne", about an hour before rain at my location. No background work of
 * its own: whenever my location's forecast is downloaded (app or background refresh) the one
 * pending alert is set again from it, or removed when the rain is gone from the forecast.
 */
object RainAlert {
    private const val WORK = "rain-alert"
    private const val CHANNEL = "rain"
    private const val LEAD_MIN = 60L
    /** Codes whose icon shows rain falling: possible rain, light rain, rain, sleet, thunderstorms. */
    private val RAIN_ICONS = setOf(1530, 1540, 1560, 1140, 1080, 1020)

    /** Rain likely (30%+) or a rain icon, as the iOS app counts a wet hour. */
    fun wet(h: Hour) = (h.rainChance ?: 0.0) >= 30 || h.code in RAIN_ICONS

    /**
     * When the next rain starts and when to say so, or null for no alert: rain must follow a dry
     * hour within 12 hours, the alert an hour ahead must fall 07–22 and not be due within a minute,
     * and one alert covers a spell (none for rain starting within 4 hours of the last one sent).
     */
    fun next(f: Forecast, now: LocalDateTime, lastFire: LocalDateTime?): Pair<LocalDateTime, LocalDateTime>? {
        val hours = f.upcoming(12, now)
        val first = hours.indexOfFirst(::wet)
        if (first <= 0) return null
        val start = hours[first].time
        val fire = start.minusMinutes(LEAD_MIN)
        if (!fire.isAfter(now.plusMinutes(1)) || fire.hour !in 7 until 22) return null
        if (lastFire != null && lastFire.isBefore(now) && start.isBefore(lastFire.plusMinutes(LEAD_MIN + 4 * 60))) return null
        return start to fire
    }

    fun update(context: Context, f: Forecast, place: String?) {
        val prefs = context.getSharedPreferences("rain_alert", Context.MODE_PRIVATE)
        val now = israelNow()
        val last = prefs.getString("fire", null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val (start, fire) = next(f, now, last) ?: return cancel(context)
        val time = start.format(DateTimeFormatter.ofPattern("HH:mm"))
        val body = place?.let { context.getString(R.string.rain_alert_body, time, it) } ?: context.getString(R.string.rain_alert_body_here, time)
        val work = OneTimeWorkRequestBuilder<RainAlertWorker>()
            .setInitialDelay(Duration.between(now, fire).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("body" to body))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, work)
        prefs.edit().putString("fire", fire.toString()).apply()
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    internal fun post(context: Context, body: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.rain_alerts), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.w_rain)
            .setContentTitle(context.getString(R.string.rain_alert_title))
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(1, n) }
    }
}

/** Posts the rain alert scheduled by [RainAlert.update] (no network, no location). */
class RainAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        RainAlert.post(applicationContext, inputData.getString("body") ?: return Result.success())
        return Result.success()
    }
}
