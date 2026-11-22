package com.sa.prayerqibla

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToLong

/** Small, testable helpers for converting local prayer hours to absolute instants. */
object TimeMath {
    fun todayIn(nowMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    /** UTC offset in hours for the selected location on [date]. */
    fun offsetHoursAt(date: LocalDate, zone: ZoneId): Double =
        date.atTime(12, 0).atZone(zone).offset.totalSeconds / 3600.0

    /**
     * Converts a local wall-clock hour (e.g. 5.25 = 05:15) on [date] to epoch milliseconds.
     * Handles times outside 0..24 and DST gaps/overlaps through [ZoneId] rules.
     */
    fun localHoursToMillis(date: LocalDate, hours: Double, zone: ZoneId): Long {
        var d = date
        var h = hours
        while (h >= 24.0) {
            d = d.plusDays(1)
            h -= 24.0
        }
        while (h < 0.0) {
            d = d.minusDays(1)
            h += 24.0
        }
        return d.atStartOfDay(zone)
            .plusSeconds((h * 3600.0).roundToLong())
            .toInstant()
            .toEpochMilli()
    }

    /** Converts a UTC hour on [date] (e.g. 1.5 = 01:30 UTC) to epoch milliseconds. */
    fun utcHoursToMillis(date: LocalDate, utcHours: Double): Long =
        date.toEpochDay() * 86_400_000L + (utcHours * 3_600_000.0).roundToLong()

    /**
     * Converts a UTC hour on [date] to wall-clock hours in [zone]. Unlike adding a single
     * offset, this applies the zone rules for that exact instant, so it is correct on the
     * two DST transition days each year.
     */
    fun localHoursAt(date: LocalDate, utcHours: Double, zone: ZoneId): Double {
        val local = Instant.ofEpochMilli(utcHoursToMillis(date, utcHours)).atZone(zone)
        return local.hour + local.minute / 60.0 + local.second / 3600.0
    }

    fun zoneFromPrefs(zone: String?, tzOffset: Double?): ZoneId {
        if (!zone.isNullOrBlank()) {
            try {
                return ZoneId.of(zone)
            } catch (_: Exception) {
                // Fall through to the stored offset.
            }
        }
        return try {
            ZoneOffset.ofTotalSeconds(((tzOffset ?: 0.0) * 3600.0).roundToLong().toInt())
        } catch (_: Exception) {
            ZoneOffset.UTC
        }
    }
}
