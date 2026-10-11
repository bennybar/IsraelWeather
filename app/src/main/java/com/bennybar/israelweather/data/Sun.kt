package com.bennybar.israelweather.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise and sunset (local time) from NOAA's solar equations; null in polar day/night. */
fun sunTimes(date: LocalDate, lat: Double, lon: Double, zone: ZoneId = IsraelZone): Pair<LocalTime, LocalTime>? {
    val rad = Math.PI / 180
    // Fractional year (radians), at local noon.
    val g = 2 * Math.PI / (if (date.isLeapYear) 366 else 365) * (date.dayOfYear - 1)
    val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
    val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
        0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
    // 90.833°: refraction plus the sun's radius.
    val cosHa = cos(90.833 * rad) / (cos(lat * rad) * cos(decl)) - tan(lat * rad) * tan(decl)
    if (cosHa < -1 || cosHa > 1) return null
    val ha = acos(cosHa) / rad
    fun at(minutesUtc: Double): LocalTime {
        val utc = date.atStartOfDay(ZoneId.of("UTC")).plusSeconds((minutesUtc * 60).toLong())
        return ZonedDateTime.ofInstant(utc.toInstant(), zone).toLocalTime()
    }
    return at(720 - 4 * (lon + ha) - eqTime) to at(720 - 4 * (lon - ha) - eqTime)
}

/**
 * "Feels like" (°C): the Australian apparent temperature (Steadman), from air temperature,
 * humidity and wind, the same formula Open-Meteo uses.
 */
fun apparentTemp(tempC: Double?, rh: Double?, windKmh: Double?): Double? {
    if (tempC == null || rh == null || windKmh == null) return null
    val vapour = rh / 100 * 6.105 * exp(17.27 * tempC / (237.7 + tempC))
    return tempC + 0.33 * vapour - 0.70 * (windKmh / 3.6) - 4.0
}
