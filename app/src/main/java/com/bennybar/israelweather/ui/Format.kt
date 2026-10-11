package com.bennybar.israelweather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.bennybar.israelweather.data.Settings
import com.bennybar.israelweather.data.TempUnit
import com.bennybar.israelweather.data.WindUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** True when the app runs in English (anything else falls back to Hebrew, the default). */
@Composable
@ReadOnlyComposable
fun isEnglish(): Boolean = LocalConfiguration.current.locales[0].language == "en"

@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

fun temp(c: Double?, s: Settings): String {
    if (c == null) return "–"
    val v = if (s.tempUnit == TempUnit.F) c * 9 / 5 + 32 else c
    return "${v.roundToInt()}°"
}

fun windValue(kmh: Double?, s: Settings): String {
    if (kmh == null) return "–"
    return if (s.windUnit == WindUnit.MS) "${(kmh / 3.6).roundToInt()}" else "${kmh.roundToInt()}"
}

/** Eight-point compass index (0 = N … 7 = NW) for a direction in degrees. */
fun compassIndex(deg: Double): Int = (((deg % 360) + 360) % 360 / 45.0).roundToInt() % 8

fun hh(t: LocalDateTime) = "%02d:00".format(t.hour)
fun hhmm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

fun dayName(date: LocalDate, locale: Locale, short: Boolean = false): String =
    date.dayOfWeek.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale)
        .removePrefix("יום ")
