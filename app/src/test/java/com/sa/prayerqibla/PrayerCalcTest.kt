package com.sa.prayerqibla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerCalcTest {
    private val riyadhLat = 24.7136
    private val riyadhLng = 46.6753
    private val riyadhOffset = 3.0
    private val date = LocalDate.of(2026, 11, 6)

    private fun times(
        settings: PrayerCalc.Settings = PrayerCalc.Settings(),
        ramadan: Boolean = false,
        lat: Double = riyadhLat,
        lng: Double = riyadhLng,
        tz: Double = riyadhOffset,
        day: LocalDate = date
    ) = PrayerCalc.timesRaw(day, lat, lng, tz, settings, ramadan)

    @Test
    fun `Umm al-Qura times match the reference calculation`() {
        val t = times()
        assertEquals(4.725, t[0], 0.02)   // Fajr
        assertEquals(6.059, t[1], 0.02)   // Sunrise
        assertEquals(11.615, t[2], 0.02)  // Dhuhr
        assertEquals(14.788, t[3], 0.02)  // Asr
        assertEquals(17.172, t[4], 0.02)  // Maghrib
        assertEquals(18.672, t[5], 0.02)  // Isha
    }

    @Test
    fun `prayer times are ordered through the day`() {
        val t = times()
        for (i in 0 until t.size - 1) {
            assertTrue("time $i (${t[i]}) should be before time ${i + 1} (${t[i + 1]})", t[i] < t[i + 1])
        }
    }

    @Test
    fun `Umm al-Qura Isha is ninety minutes after Maghrib and extra in Ramadan`() {
        val normal = times()
        val ramadan = times(ramadan = true)
        assertEquals(normal[4] + 1.5, normal[5], 0.001)
        assertEquals(normal[5] + 0.5, ramadan[5], 0.001)
    }

    @Test
    fun `Hanafi Asr is later than the standard Asr`() {
        val standard = times(PrayerCalc.Settings(asr = PrayerCalc.AsrMethod.STANDARD))
        val hanafi = times(PrayerCalc.Settings(asr = PrayerCalc.AsrMethod.HANAFI))
        assertTrue(hanafi[3] > standard[3])
    }

    @Test
    fun `ISNA Fajr is later than Muslim World League Fajr`() {
        val mwl = times(PrayerCalc.Settings(method = PrayerCalc.Method.MUSLIM_WORLD_LEAGUE))
        val isna = times(PrayerCalc.Settings(method = PrayerCalc.Method.ISNA))
        assertTrue(isna[0] > mwl[0])
    }

    @Test
    fun `timesUtc is the local time minus the zone offset`() {
        val local = times()
        val utc = PrayerCalc.timesUtc(date, riyadhLat, riyadhLng, ramadan = false)
        for (i in local.indices) {
            assertEquals(local[i] - riyadhOffset, utc[i], 0.0001)
        }
    }

    @Test
    fun `high latitude rules keep every value finite and ordered`() {
        // Tromsø in late June: the sun does not set, so the fallback must be used.
        for (rule in PrayerCalc.HighLatitudeRule.entries) {
            val t = times(
                settings = PrayerCalc.Settings(highLatitude = rule),
                lat = 69.6492,
                lng = 18.9553,
                tz = 2.0,
                day = LocalDate.of(2026, 6, 21)
            )
            t.forEach { assertTrue("expected a finite time for $rule", it.isFinite()) }
            assertTrue(t[0] < t[1])
            assertTrue(t[1] < t[2])
            assertTrue(t[2] < t[3])
            assertTrue(t[3] < t[4])
            assertTrue(t[4] < t[5])
        }
    }
}
