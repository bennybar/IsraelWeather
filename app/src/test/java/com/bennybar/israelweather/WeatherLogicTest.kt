package com.bennybar.israelweather

import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Warning
import com.bennybar.israelweather.data.WeatherCodes
import com.bennybar.israelweather.data.apparentTemp
import com.bennybar.israelweather.data.sunTimes
import com.bennybar.israelweather.location.LocationResolver
import com.bennybar.israelweather.ui.compassIndex
import com.bennybar.israelweather.ui.places.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs

class WeatherLogicTest {
    private val places = listOf(
        Place(1, "ירושלים", "Jerusalem", 31.778, 35.2, 118),
        Place(2, "תל-אביב, חוף", "Tel Aviv Coast", 32.0821, 34.7802, 103),
        Place(520, "אילת", "Elat", 29.55, 34.9, 137),
    )

    @Test fun nearestPicksClosestPlace() {
        assertEquals(1, LocationResolver.nearest(places, 31.7683, 35.2137)?.id)
        assertEquals(2, LocationResolver.nearest(places, 32.0853, 34.7818)?.id)
    }

    @Test fun nearestRejectsOutsideIsrael() {
        assertNull(LocationResolver.nearest(places, 48.8566, 2.3522)) // Paris
        assertNull(LocationResolver.nearest(places, 30.0444, 31.2357)) // Cairo
    }

    @Test fun searchNormalizationIgnoresPunctuationAndCase() {
        assertEquals(normalize("תל אביב"), normalize("תל-אביב"))
        assertTrue(normalize("Tel Aviv Coast").contains(normalize("tel-aviv")))
        assertEquals(normalize("רמת השרון"), normalize("רמת-השרון"))
    }

    @Test fun currentHourIsTheSlotCoveringNow() {
        val f = Forecast(1, hourly = listOf(Hour("2026-10-11 14:00", 1250), Hour("2026-10-11 15:00", 1220), Hour("2026-10-11 16:00", 1230)))
        val now = LocalDateTime.of(2026, 10, 11, 15, 40)
        assertEquals("2026-10-11 15:00", f.currentHour(now)?.t)
        assertEquals(listOf("2026-10-11 15:00", "2026-10-11 16:00"), f.upcoming(24, now).map { it.t })
    }

    @Test fun warningsAreActiveUntilTheirEnd() {
        val w = Warning(1, 3, "yellow", to = "2026-10-11 20:00")
        assertTrue(w.isActive(LocalDateTime.of(2026, 10, 11, 19, 59)))
        assertFalse(w.isActive(LocalDateTime.of(2026, 10, 11, 20, 1)))
        assertTrue(Warning(2, 8, "gray").isFlash)
    }

    @Test fun compassDirections() {
        assertEquals(0, compassIndex(0.0))
        assertEquals(7, compassIndex(292.5 + 22.5)) // NW
        assertEquals(6, compassIndex(270.0)) // W
        assertEquals(0, compassIndex(355.0))
    }

    // Reference values from Open-Meteo for Gedera, 11 Oct 2026: sunrise 06:41, sunset 18:13.
    @Test fun sunTimesMatchReference() {
        val (rise, set) = sunTimes(LocalDate.of(2026, 10, 11), 31.8128, 34.7787)!!
        fun minutes(t: LocalTime) = t.hour * 60 + t.minute
        assertTrue("sunrise $rise", abs(minutes(rise) - (6 * 60 + 41)) <= 2)
        assertTrue("sunset $set", abs(minutes(set) - (18 * 60 + 13)) <= 2)
    }

    // Open-Meteo's apparent_temperature at night (by day it adds sunshine; ours is the shade value).
    @Test fun apparentTempMatchesOpenMeteo() {
        assertEquals(26.3, apparentTemp(22.6, 87.0, 2.6)!!, 0.4)
        assertNull(apparentTemp(20.0, null, 5.0))
    }

    // Night icons follow the real sunset: ~19:47 late June, ~16:37 early December.
    @Test fun nightFollowsSunset() {
        assertFalse(WeatherCodes.isNight(LocalDateTime.of(2026, 6, 21, 19, 0)))
        assertTrue(WeatherCodes.isNight(LocalDateTime.of(2026, 6, 21, 20, 0)))
        assertTrue(WeatherCodes.isNight(LocalDateTime.of(2026, 12, 5, 17, 0)))
        assertFalse(WeatherCodes.isNight(LocalDateTime.of(2026, 12, 5, 7, 0)))
        assertTrue(WeatherCodes.isNight(LocalDateTime.of(2026, 12, 5, 5, 0)))
    }

    // Current conditions (Open-Meteo) stand in for "now" while under an hour old.
    @Test fun currentHourPrefersFreshReading() {
        val f = Forecast(
            id = 1,
            hourly = listOf(Hour("2026-10-18 09:00", 1230, temp = 20.0), Hour("2026-10-18 10:00", 1230, temp = 22.0)),
            now = Hour("2026-10-18 09:15", 1250, temp = 24.0),
        )
        assertEquals(24.0, f.currentHour(LocalDateTime.of(2026, 10, 18, 9, 40))!!.temp!!, 0.0)
        assertEquals(22.0, f.currentHour(LocalDateTime.of(2026, 10, 18, 10, 20))!!.temp!!, 0.0)
    }
}
