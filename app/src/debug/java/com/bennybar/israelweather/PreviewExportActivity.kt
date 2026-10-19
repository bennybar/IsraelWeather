package com.bennybar.israelweather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.bennybar.israelweather.widget.WidgetKind
import java.io.File

/**
 * Debug-only tool: renders each widget's generated preview (published by the
 * app at start) to a WebP, for the static `previewImage` that launchers
 * without generated-preview support (e.g. Samsung) show in the widget picker.
 *
 *   adb shell am start -n com.bennybar.israelweather/.PreviewExportActivity
 *   adb shell run-as com.bennybar.israelweather ls files/previews
 */
class PreviewExportActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = File(filesDir, "previews").apply { mkdirs() }
        val am = AppWidgetManager.getInstance(this)
        val d = resources.displayMetrics.density
        WidgetKind.entries.forEach { k ->
            val rv = am.getWidgetPreview(ComponentName(this, k.receiver.java), Process.myUserHandle(), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            if (rv == null) {
                Log.w("PreviewExport", "${k.name}: no generated preview yet")
                return@forEach
            }
            val hDp = 180
            val w = (360 * d).toInt()
            val h = (hDp * d).toInt()
            val host = FrameLayout(this)
            val view = rv.apply(this, host)
            host.addView(view, FrameLayout.LayoutParams(w, h))
            host.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            host.layout(0, 0, w, h)
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(full))
            // 2× is plenty for a picker thumbnail and keeps the APK small.
            val scaled = Bitmap.createScaledBitmap(full, (360 * 2), hDp * 2, true)
            File(out, "preview_${k.name.lowercase()}.webp").outputStream().use {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, it)
            }
            Log.i("PreviewExport", "${k.name}: saved")
        }
        finish()
    }
}
