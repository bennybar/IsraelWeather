package com.bennybar.israelweather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.bennybar.israelweather.R
import com.bennybar.israelweather.data.Warning
import com.bennybar.israelweather.data.israelNow
import com.bennybar.israelweather.data.parseImsTime
import com.bennybar.israelweather.ui.theme.WeatherColors
import java.util.Locale

fun WeatherColors.forLevel(level: String): Color = when (level) {
    "red" -> red
    "orange" -> orange
    "yellow" -> yellow
    else -> track
}

/** "Yellow warning", "Orange early warning", "News flash"… */
fun warningLabelRes(w: Warning): Int = when {
    w.isFlash -> R.string.news_flash
    w.level == "red" -> if (w.early) R.string.early_red else R.string.warning_red
    w.level == "orange" -> if (w.early) R.string.early_orange else R.string.warning_orange
    else -> if (w.early) R.string.early_yellow else R.string.warning_yellow
}

@Composable
@ReadOnlyComposable
fun warningLabel(w: Warning): String = stringResource(warningLabelRes(w))

/** "12:00–20:00", prefixed with the day when it isn't today. */
fun warningRange(w: Warning, locale: Locale): String {
    val from = w.from?.let(::parseImsTime) ?: return ""
    val to = w.to?.let(::parseImsTime)
    val today = israelNow().toLocalDate()
    val day = if (from.toLocalDate() == today) "" else dayName(from.toLocalDate(), locale, short = true) + " "
    val end = to?.let { if (it.toLocalDate() == from.toLocalDate()) hhmm(it) else dayName(it.toLocalDate(), locale, short = true) + " " + hhmm(it) }
    return day + hhmm(from) + (end?.let { "–$it" } ?: "")
}
