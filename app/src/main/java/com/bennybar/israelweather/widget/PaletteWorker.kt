package com.bennybar.israelweather.widget

import android.content.Context
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bennybar.israelweather.app

/**
 * Redraws "Wallpaper"-colored widgets when the system's Material You palette changes. The home
 * screen is meant to re-resolve the dynamic colors itself, but Samsung's doesn't; Android starts
 * this job when the palette setting changes, even with the app closed. One-shot by nature, so it
 * re-arms itself.
 */
class PaletteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        redrawWallpaperWidgets(applicationContext)
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "palette"

        fun schedule(context: Context) {
            val uri = Settings.Secure.getUriFor("theme_customization_overlay_packages")
            val request = OneTimeWorkRequestBuilder<PaletteWorker>()
                .setConstraints(Constraints.Builder().addContentUriTrigger(uri, false).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Redraws widgets colored by the wallpaper, if there are any. */
        suspend fun redrawWallpaperWidgets(context: Context) {
            if (Widgets.placed(context).any { (_, id) -> app.settings.widgetColor(id) == WIDGET_COLOR_WALLPAPER }) Widgets.updateAll(context)
        }
    }
}
