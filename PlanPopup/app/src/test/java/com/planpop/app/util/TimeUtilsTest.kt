package com.planpop.app.util

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun laterToday_returnsToday() {
        val now = ZonedDateTime.of(2026, 1, 1, 7, 30, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(8, 0, now))
    }

    @Test
    fun exactlyNow_returnsTomorrow() {
        val now = ZonedDateTime.of(2026, 1, 1, 8, 0, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 8, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(8, 0, now))
    }

    @Test
    fun alreadyPassed_returnsTomorrow() {
        val now = ZonedDateTime.of(2026, 1, 1, 9, 15, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 8, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(8, 0, now))
    }

    @Test
    fun justBeforeMidnight_returnsNextDay() {
        val now = ZonedDateTime.of(2026, 1, 1, 23, 59, 0, 0, zone)
        val expected = ZonedDateTime.of(2026, 1, 2, 0, 5, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(0, 5, now))
    }
}
