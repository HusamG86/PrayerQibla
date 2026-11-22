package com.sa.prayerqibla.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WidgetDataTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 11, 23)
    private val today = doubleArrayOf(5.0, 6.5, 12.0, 15.0, 18.0, 20.0)
    private val tomorrow = doubleArrayOf(5.1, 6.6, 12.1, 15.1, 18.1, 20.1)

    @Test fun findNext_afterIshaUsesTomorrow() {
        val now = date.atTime(21, 0).toInstant(zone).toEpochMilli()
        val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)
        assertEquals(0, next?.index)
        assertEquals(
            date.plusDays(1).atTime(5, 6).toInstant(zone).toEpochMilli(),
            next?.atMillis
        )
    }

    @Test fun findNext_skipsSunrise() {
        val now = date.atTime(5, 30).toInstant(zone).toEpochMilli()
        val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)
        assertEquals(2, next?.index)
    }

    @Test fun findNext_setsPreviousPrayerAndProgress() {
        val now = date.atTime(13, 0).toInstant(zone).toEpochMilli()
        val next = NextPrayerFinder.find(now, date, zone, today, tomorrow)!!
        assertEquals(date.atTime(12, 0).toInstant(zone).toEpochMilli(), next.fromMillis)
        assertEquals(1f / 3f, NextPrayerFinder.progress(now, next), 0.001f)
    }

    @Test fun cityOrFallback_usesFallbackForBlankCustomNames() {
        assertEquals("Prayer times", NextPrayerFinder.cityOrFallback(null, "Prayer times"))
        assertEquals("Prayer times", NextPrayerFinder.cityOrFallback("  ", "Prayer times"))
        assertEquals("Riyadh", NextPrayerFinder.cityOrFallback("Riyadh", "Prayer times"))
    }
}
