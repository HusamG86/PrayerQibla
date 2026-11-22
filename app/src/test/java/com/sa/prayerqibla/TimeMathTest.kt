package com.sa.prayerqibla

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TimeMathTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun `offset follows daylight saving time`() {
        assertEquals(-5.0, TimeMath.offsetHoursAt(LocalDate.of(2026, 1, 15), newYork), 0.001)
        assertEquals(-4.0, TimeMath.offsetHoursAt(LocalDate.of(2026, 7, 15), newYork), 0.001)
        assertEquals(3.0, TimeMath.offsetHoursAt(LocalDate.of(2026, 11, 6), ZoneId.of("Asia/Riyadh")), 0.001)
    }

    @Test
    fun `spring forward gap is converted forward`() {
        val millis = TimeMath.localHoursToMillis(
            LocalDate.of(2026, 3, 8),
            2.5,
            newYork
        )
        val local = Instant.ofEpochMilli(millis).atZone(newYork)
        assertEquals(3, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun `fall back keeps the first occurrence of the repeated hour`() {
        val millis = TimeMath.localHoursToMillis(
            LocalDate.of(2026, 11, 1),
            1.5,
            newYork
        )
        val local = Instant.ofEpochMilli(millis).atZone(newYork)
        assertEquals(1, local.hour)
        assertEquals(30, local.minute)
        assertEquals(-4, local.offset.totalSeconds / 3600)
    }

    @Test
    fun `UTC hours convert to local wall clock independently of DST`() {
        val spring = LocalDate.of(2026, 3, 8)
        // 06:00 UTC is 01:00 EST; 07:00 UTC is 03:00 EDT after the spring-forward jump.
        assertEquals(1.0, TimeMath.localHoursAt(spring, 6.0, newYork), 0.001)
        assertEquals(3.0, TimeMath.localHoursAt(spring, 7.0, newYork), 0.001)

        val fall = LocalDate.of(2026, 11, 1)
        // 05:30 UTC is 01:30 EDT; 06:30 UTC is 01:30 EST after clocks fall back.
        assertEquals(1.5, TimeMath.localHoursAt(fall, 5.5, newYork), 0.001)
        assertEquals(1.5, TimeMath.localHoursAt(fall, 6.5, newYork), 0.001)
    }

    @Test
    fun `utcHoursToMillis does not depend on a fixed offset`() {
        assertEquals(
            Instant.parse("2026-11-06T01:43:30Z").toEpochMilli(),
            TimeMath.utcHoursToMillis(LocalDate.of(2026, 11, 6), 1.725)
        )
    }

    @Test
    fun `hours outside the day roll into neighbouring dates`() {
        val before = TimeMath.localHoursToMillis(LocalDate.of(2026, 11, 6), -1.0, ZoneId.of("Asia/Riyadh"))
        val after = TimeMath.localHoursToMillis(LocalDate.of(2026, 11, 6), 25.0, ZoneId.of("Asia/Riyadh"))
        val beforeLocal = Instant.ofEpochMilli(before).atZone(ZoneId.of("Asia/Riyadh"))
        val afterLocal = Instant.ofEpochMilli(after).atZone(ZoneId.of("Asia/Riyadh"))
        assertEquals(5, beforeLocal.dayOfMonth)
        assertEquals(23, beforeLocal.hour)
        assertEquals(7, afterLocal.dayOfMonth)
        assertEquals(1, afterLocal.hour)
    }

    @Test
    fun `todayIn uses the selected zone`() {
        // 2026-11-06T23:30Z is already 2026-11-07 in Riyadh.
        val millis = Instant.parse("2026-11-06T23:30:00Z").toEpochMilli()
        assertEquals(
            LocalDate.of(2026, 11, 7),
            TimeMath.todayIn(millis, ZoneId.of("Asia/Riyadh"))
        )
    }

    @Test
    fun `zoneFromPrefs validates IANA names and falls back to an offset`() {
        assertEquals(ZoneId.of("Asia/Riyadh"), TimeMath.zoneFromPrefs("Asia/Riyadh", null))
        assertEquals(ZoneOffset.ofHours(3), TimeMath.zoneFromPrefs("Not/AZone", 3.0))
        assertEquals(ZoneOffset.UTC, TimeMath.zoneFromPrefs(null, null))
    }
}
