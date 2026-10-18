package com.bennybar.israelweather.data

import androidx.annotation.DrawableRes
import com.bennybar.israelweather.R
import java.time.LocalDateTime

/** IMS weather codes (https://ims.gov.il/he/weather_codes) → text and icon. */
object WeatherCodes {
    private class Info(val he: String, val en: String, val icon: Icon)

    enum class Icon { CLEAR, PARTLY, CLOUDY, PARTLY_RAIN, RAIN, STORM, SNOW, FOG, DUST, WIND }

    private val codes = mapOf(
        1250 to Info("בהיר", "Clear", Icon.CLEAR),
        1220 to Info("מעונן חלקית", "Partly cloudy", Icon.PARTLY),
        1230 to Info("מעונן", "Cloudy", Icon.CLOUDY),
        1570 to Info("אביך", "Dust", Icon.DUST),
        1010 to Info("סופות חול", "Sandstorms", Icon.DUST),
        1160 to Info("ערפל", "Fog", Icon.FOG),
        1310 to Info("חם", "Hot", Icon.CLEAR),
        1580 to Info("חם מאוד", "Extremely hot", Icon.CLEAR),
        1270 to Info("הביל", "Muggy", Icon.PARTLY),
        1320 to Info("קר", "Cold", Icon.CLOUDY),
        1590 to Info("קר מאוד", "Extremely cold", Icon.CLOUDY),
        1300 to Info("קרה", "Frost", Icon.SNOW),
        1530 to Info("מעונן חלקית, ייתכן גשם", "Partly cloudy, possible rain", Icon.PARTLY_RAIN),
        1540 to Info("מעונן, ייתכן גשם", "Cloudy, possible rain", Icon.PARTLY_RAIN),
        1560 to Info("מעונן, גשם קל", "Cloudy, light rain", Icon.RAIN),
        1140 to Info("גשום", "Rainy", Icon.RAIN),
        1020 to Info("סופות רעמים", "Thunderstorms", Icon.STORM),
        1510 to Info("סוער", "Stormy", Icon.STORM),
        1260 to Info("רוחות ערות", "Windy", Icon.WIND),
        1080 to Info("שלג מעורב בגשם", "Sleet", Icon.SNOW),
        1070 to Info("שלג קל", "Light snow", Icon.SNOW),
        1060 to Info("שלג", "Snow", Icon.SNOW),
        1520 to Info("שלג כבד", "Heavy snow", Icon.SNOW),
    )

    fun describe(code: Int, english: Boolean): String =
        codes[code]?.let { if (english) it.en else it.he } ?: ""

    fun icon(code: Int): Icon = codes[code]?.icon ?: Icon.CLOUDY

    /**
     * Whether an hour slot is mostly after sunset or before sunrise (central Israel's times:
     * they differ by only a few minutes across the country).
     */
    fun isNight(t: LocalDateTime): Boolean {
        val (rise, set) = sunTimes(t.toLocalDate(), 31.5, 34.9) ?: return t.hour < 6 || t.hour >= 18
        val mid = t.toLocalTime().plusMinutes(30)
        return mid.isBefore(rise) || !mid.isBefore(set)
    }

    @DrawableRes
    fun drawable(code: Int, night: Boolean = false): Int = when (icon(code)) {
        Icon.CLEAR -> if (night) R.drawable.w_moon else R.drawable.w_sun
        Icon.PARTLY -> if (night) R.drawable.w_partly_night else R.drawable.w_partly
        Icon.CLOUDY -> R.drawable.w_cloud
        Icon.PARTLY_RAIN -> R.drawable.w_partly_rain
        Icon.RAIN -> R.drawable.w_rain
        Icon.STORM -> R.drawable.w_storm
        Icon.SNOW -> R.drawable.w_snow
        Icon.FOG -> R.drawable.w_fog
        Icon.DUST -> R.drawable.w_dust
        Icon.WIND -> R.drawable.w_wind
    }
}
