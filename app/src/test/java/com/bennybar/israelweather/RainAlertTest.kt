package com.bennybar.israelweather

import com.bennybar.israelweather.data.Forecast
import com.bennybar.israelweather.data.Hour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** When the rain alert goes off: the same cases as the iOS check. */
class RainAlertTest {
    private fun at(hhmm: String) = LocalDateTime.parse("2026-12-01T$hhmm")
    /** 24 hours of 2026-12-01: [wet] hours at 80% chance, [codeRain] hours with a rain icon at 5%. */
    private fun fc(wet: Set<Int>, codeRain: Set<Int> = emptySet()) = Forecast(
        id = -1,
        hourly = (0 until 24).map { h ->
            Hour(t = "2026-12-01 %02d:00".format(h), code = if (h in codeRain) 1560 else 1220,
                rainChance = if (h in wet) 80.0 else if (h in codeRain) 5.0 else 0.0)
        },
        daily = emptyList(), source = "x",
    )
    private val now = at("09:10")

    @Test
    fun timing() {
        assertEquals(at("14:00") to at("13:00"), RainAlert.next(fc(setOf(14, 15)), now, null))
        assertNull("already raining", RainAlert.next(fc(setOf(9, 10, 14)), now, null))
        assertNull("alert time passed", RainAlert.next(fc(setOf(10)), now, null))
        assertEquals("rain icon at a low chance counts", at("12:00"), RainAlert.next(fc(emptySet(), setOf(12)), now, null)?.first)
        assertNull("alert at 22:00 is night", RainAlert.next(fc(setOf(23)), at("12:00"), null))
        assertEquals(at("20:00"), RainAlert.next(fc(setOf(21)), at("12:00"), null)?.second)
        assertNull("same spell as the last alert", RainAlert.next(fc(setOf(12)), now, at("08:00")))
        assertNotNull("a new spell", RainAlert.next(fc(setOf(15)), now, at("08:00")))
        assertNotNull("a pending alert is replaced", RainAlert.next(fc(setOf(14)), now, at("12:00")))
        assertNull("dry", RainAlert.next(fc(emptySet()), now, null))
    }
}
