package com.bennybar.israelweather.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** All IMS times are Israel local time. */
val IsraelZone: ZoneId = ZoneId.of("Asia/Jerusalem")

fun israelNow(): LocalDateTime = LocalDateTime.now(IsraelZone)

/** "YYYY-MM-DD HH:mm" (the proxy's format) → LocalDateTime. */
fun parseImsTime(s: String): LocalDateTime = LocalDateTime.parse(s.trim().replace(' ', 'T'))

/** One of the 175 IMS forecast points. */
@Serializable
data class Place(val id: Int, val he: String, val en: String, val lat: Double, val lon: Double, val rid: Int) {
    fun name(english: Boolean) = if (english) en else he
}

@Serializable
data class PlacesResponse(val locations: List<Place>)

@Serializable
data class Localized(val he: String? = null, val en: String? = null) {
    fun get(english: Boolean) = (if (english) en else he) ?: he ?: en
}

@Serializable
data class Hour(
    val t: String,
    val code: Int,
    val temp: Double? = null,
    val rainChance: Double? = null,
    val rain: Double? = null,
    val rh: Double? = null,
    val wind: Double? = null,
    val gust: Double? = null,
    val windDir: Double? = null,
    val uv: Double? = null,
    val heat: Int? = null,
    val pm10: Double? = null,
) {
    val time: LocalDateTime get() = parseImsTime(t)
}

@Serializable
data class Day(val date: String, val code: Int, val min: Double? = null, val max: Double? = null, val uv: Double? = null) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}

@Serializable
data class Forecast(
    val id: Int,
    val issued: String? = null,
    val country: Localized = Localized(),
    val hourly: List<Hour> = emptyList(),
    val daily: List<Day> = emptyList(),
) {
    /** The hour slot covering now (or the first upcoming one). */
    fun currentHour(now: LocalDateTime = israelNow()): Hour? {
        val slot = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.firstOrNull { !it.time.isBefore(slot) } ?: hourly.lastOrNull()
    }

    /** The next [count] hours starting at the current one. */
    fun upcoming(count: Int, now: LocalDateTime = israelNow()): List<Hour> {
        val slot = now.withMinute(0).withSecond(0).withNano(0)
        return hourly.filter { !it.time.isBefore(slot) }.take(count)
    }

    fun hoursOn(date: LocalDate) = hourly.filter { it.time.toLocalDate() == date }
}

@Serializable
data class Warning(
    val id: Long,
    val severity: Int,
    val level: String,
    val early: Boolean = false,
    val type: Int = 0,
    val typeName: Localized = Localized(),
    val sent: String? = null,
    val from: String? = null,
    val to: String? = null,
    val he: String = "",
    val en: String = "",
    val regions: List<Int> = emptyList(),
) {
    fun text(english: Boolean) = (if (english) en else he).ifBlank { he.ifBlank { en } }

    /** Still relevant: not yet over. */
    fun isActive(now: LocalDateTime = israelNow()) = to?.let { !parseImsTime(it).isBefore(now) } ?: true

    /** Gray IMS items are informational news flashes, not weather warnings. */
    val isFlash get() = level == "gray" || level == "green"
}

@Serializable
data class WarningsResponse(val warnings: List<Warning>)

@Serializable
data class Bounds(val south: Double, val west: Double, val north: Double, val east: Double)

@Serializable
data class RadarFrame(val t: String, val url: String)

@Serializable
data class Radar(val bounds: Bounds, val frames: List<RadarFrame>)

@Serializable
data class Station(val id: Int, val name: String)

/** Latest reading from the nearest IMS station (needs the Envista token on the proxy). */
@Serializable
data class Observation(
    val station: Station,
    val t: String? = null,
    val temp: Double? = null,
    val rh: Double? = null,
    val wind: Double? = null,
    val gust: Double? = null,
    val windDir: Double? = null,
    val rain: Double? = null,
)

/** A value plus when we fetched it (epoch millis), as stored on disk. */
@Serializable
data class Stamped<T>(val fetchedAt: Long, val data: T)
