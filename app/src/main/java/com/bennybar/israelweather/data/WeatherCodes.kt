package com.bennybar.israelweather.data

import androidx.annotation.DrawableRes
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
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
     * Whether an hour slot (or reading) is mostly after sunset or before sunrise (central Israel's times:
     * they differ by only a few minutes across the country).
     */
    fun isNight(t: LocalDateTime): Boolean {
        val (rise, set) = sunTimes(t.toLocalDate(), 31.5, 34.9) ?: return t.hour < 6 || t.hour >= 18
        // Hour slots are judged by their middle; a current reading (e.g. 13:15) by itself.
        val mid = if (t.minute == 0) t.toLocalTime().plusMinutes(30) else t.toLocalTime()
        return mid.isBefore(rise) || !mid.isBefore(set)
    }

    /** The icon for [code] in the chosen set (Settings → Weather icons), used by the app and widgets alike. */
    @DrawableRes
    fun drawable(code: Int, night: Boolean = false, style: IconStyle = app.settings.value.iconStyle): Int {
        val set = when (style) {
            IconStyle.MATERIAL -> MATERIAL
            IconStyle.METEOCONS -> METEOCONS
            IconStyle.FLUENT -> FLUENT
            IconStyle.NOTO -> NOTO
            IconStyle.GLASS -> GLASS
            IconStyle.ONEUI -> ONEUI
            IconStyle.MATERIAL_WEATHER -> MATERIAL_WEATHER
        }
        return when (icon(code)) {
            Icon.CLEAR -> if (night) set.moon else set.sun
            Icon.PARTLY -> if (night) set.partlyNight else set.partly
            Icon.CLOUDY -> set.cloud
            Icon.PARTLY_RAIN -> set.partlyRain
            Icon.RAIN -> set.rain
            Icon.STORM -> set.storm
            Icon.SNOW -> set.snow
            Icon.FOG -> set.fog
            Icon.DUST -> set.dust
            Icon.WIND -> set.wind
        }
    }

    private class IconSet(
        val sun: Int, val moon: Int, val partly: Int, val partlyNight: Int, val cloud: Int, val partlyRain: Int,
        val rain: Int, val storm: Int, val snow: Int, val fog: Int, val dust: Int, val wind: Int,
    )

    private val MATERIAL = IconSet(
        R.drawable.w_sun, R.drawable.w_moon, R.drawable.w_partly, R.drawable.w_partly_night, R.drawable.w_cloud, R.drawable.w_partly_rain,
        R.drawable.w_rain, R.drawable.w_storm, R.drawable.w_snow, R.drawable.w_fog, R.drawable.w_dust, R.drawable.w_wind,
    )

    // Meteocons (Bas Milius, MIT), Fluent Emoji (Microsoft, MIT), Noto Emoji (Google, Apache 2.0):
    // 192 px WebPs; the emoji sets' partly-cloudy night, fog and dust are composed from their parts.
    private val METEOCONS = IconSet(
        R.drawable.wi_mfill_sun, R.drawable.wi_mfill_moon, R.drawable.wi_mfill_partly, R.drawable.wi_mfill_partly_night,
        R.drawable.wi_mfill_cloud, R.drawable.wi_mfill_partly_rain, R.drawable.wi_mfill_rain, R.drawable.wi_mfill_storm,
        R.drawable.wi_mfill_snow, R.drawable.wi_mfill_fog, R.drawable.wi_mfill_dust, R.drawable.wi_mfill_wind,
    )
    private val FLUENT = IconSet(
        R.drawable.wi_fluent_sun, R.drawable.wi_fluent_moon, R.drawable.wi_fluent_partly, R.drawable.wi_fluent_partly_night,
        R.drawable.wi_fluent_cloud, R.drawable.wi_fluent_partly_rain, R.drawable.wi_fluent_rain, R.drawable.wi_fluent_storm,
        R.drawable.wi_fluent_snow, R.drawable.wi_fluent_fog, R.drawable.wi_fluent_dust, R.drawable.wi_fluent_wind,
    )
    private val NOTO = IconSet(
        R.drawable.wi_noto_sun, R.drawable.wi_noto_moon, R.drawable.wi_noto_partly, R.drawable.wi_noto_partly_night,
        R.drawable.wi_noto_cloud, R.drawable.wi_noto_partly_rain, R.drawable.wi_noto_rain, R.drawable.wi_noto_storm,
        R.drawable.wi_noto_snow, R.drawable.wi_noto_fog, R.drawable.wi_noto_dust, R.drawable.wi_noto_wind,
    )

    // Our own sets (tools/icons/gen_icons.py): frosted "glass", and flat rounded One UI–style.
    private val GLASS = IconSet(
        R.drawable.wi_glass_sun, R.drawable.wi_glass_moon, R.drawable.wi_glass_partly, R.drawable.wi_glass_partly_night,
        R.drawable.wi_glass_cloud, R.drawable.wi_glass_partly_rain, R.drawable.wi_glass_rain, R.drawable.wi_glass_storm,
        R.drawable.wi_glass_snow, R.drawable.wi_glass_fog, R.drawable.wi_glass_dust, R.drawable.wi_glass_wind,
    )
    private val ONEUI = IconSet(
        R.drawable.wi_oneui_sun, R.drawable.wi_oneui_moon, R.drawable.wi_oneui_partly, R.drawable.wi_oneui_partly_night,
        R.drawable.wi_oneui_cloud, R.drawable.wi_oneui_partly_rain, R.drawable.wi_oneui_rain, R.drawable.wi_oneui_storm,
        R.drawable.wi_oneui_snow, R.drawable.wi_oneui_fog, R.drawable.wi_oneui_dust, R.drawable.wi_oneui_wind,
    )

    // Material Weather Icons by M00NJ (CC BY 4.0); "partly rain" uses its drizzle, dust its haze.
    private val MATERIAL_WEATHER = IconSet(
        R.drawable.wi_m3_sun, R.drawable.wi_m3_moon, R.drawable.wi_m3_partly, R.drawable.wi_m3_partly_night,
        R.drawable.wi_m3_cloud, R.drawable.wi_m3_partly_rain, R.drawable.wi_m3_rain, R.drawable.wi_m3_storm,
        R.drawable.wi_m3_snow, R.drawable.wi_m3_fog, R.drawable.wi_m3_dust, R.drawable.wi_m3_wind,
    )
}
