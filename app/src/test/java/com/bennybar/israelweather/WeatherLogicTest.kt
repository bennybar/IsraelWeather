package com.bennybar.israelweather

import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Hour
import com.bennybar.israelweather.data.Place
import com.bennybar.israelweather.data.Warning
import com.bennybar.israelweather.location.LocationResolver
import com.bennybar.israelweather.ui.compassIndex
import com.bennybar.israelweather.ui.places.normalize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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
}
