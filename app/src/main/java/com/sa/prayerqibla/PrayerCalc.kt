package com.sa.prayerqibla

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.PI

/**
 * Prayer time calculation.
 *
 * The astronomy is kept in [timesRaw] and has no Android dependencies so it can be unit tested
 * on the JVM. The Umm al-Qura Hijri calendar (used for the Ramadan Isha rule) is applied by the
 * small [isRamadan] wrapper.
 */
object PrayerCalc {
    private const val R = PI / 180.0

    enum class Method(
        val fajrAngle: Double,
        val ishaAngle: Double?,
        val ishaIntervalMinutes: Int?
    ) {
        UMM_AL_QURA(18.5, null, 90),
        MUSLIM_WORLD_LEAGUE(18.0, 17.0, null),
        ISNA(15.0, 15.0, null),
        EGYPTIAN(19.5, 17.5, null),
        KARACHI(18.0, 18.0, null);

        companion object {
            fun fromKey(key: String?): Method =
                entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: UMM_AL_QURA
        }
    }

    enum class AsrMethod(val shadowFactor: Double) {
        STANDARD(1.0),
        HANAFI(2.0);

        companion object {
            fun fromKey(key: String?): AsrMethod =
                entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: STANDARD
        }
    }

    enum class HighLatitudeRule {
        ANGLE_BASED,
        ONE_SEVENTH,
        MIDDLE_OF_NIGHT,
        NONE;

        companion object {
            fun fromKey(key: String?): HighLatitudeRule =
                entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: ANGLE_BASED
        }
    }

    data class Settings(
        val method: Method = Method.UMM_AL_QURA,
        val asr: AsrMethod = AsrMethod.STANDARD,
        val highLatitude: HighLatitudeRule = HighLatitudeRule.ANGLE_BASED
    )

    private fun fix(a: Double, b: Double) = a - b * floor(a / b)

    /** Umm al-Qura month; uses UTC so the result does not depend on the device time zone. */
    fun isRamadan(date: LocalDate): Boolean {
        val c = IslamicCalendar()
        c.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        c.timeZone = TimeZone.getTimeZone("UTC")
        c.timeInMillis = date.toEpochDay() * 86400000L + 43200000L
        return c.get(Calendar.MONTH) == IslamicCalendar.RAMADAN
    }

    /**
     * Returns local-time hours: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha.
     *
     * [tz] is the UTC offset in hours for the selected location and date. Prefer
     * [timesUtc] when converting to absolute alarm instants: a single offset cannot
     * represent both sides of a daylight-saving transition.
     */
    fun times(
        date: LocalDate,
        lat: Double,
        lng: Double,
        tz: Double,
        settings: Settings = Settings(),
        ramadan: Boolean = isRamadan(date)
    ): DoubleArray = timesRaw(date, lat, lng, tz, settings, ramadan)

    /**
     * Returns the same six values as UTC hours. This is safe across DST changes because
     * each value can be converted independently with [TimeMath.utcHoursToMillis].
     */
    fun timesUtc(
        date: LocalDate,
        lat: Double,
        lng: Double,
        settings: Settings = Settings(),
        ramadan: Boolean = isRamadan(date)
    ): DoubleArray = timesRaw(date, lat, lng, 0.0, settings, ramadan)

    /** Pure calculation used by [times] and by the unit tests. */
    fun timesRaw(
        date: LocalDate,
        lat: Double,
        lng: Double,
        tz: Double,
        settings: Settings,
        ramadan: Boolean
    ): DoubleArray {
        val d = date.toEpochDay() + 0.5 + 2440587.5 - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(g * R) + 0.02 * sin(2 * g * R), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(atan2(cos(e * R) * sin(l * R), cos(l * R)) / R / 15.0, 24.0)
        val dec = asin(sin(e * R) * sin(l * R)) / R
        val noon = 12 + tz - lng / 15.0 - (q / 15.0 - ra)

        fun halfDayAt(angle: Double): Double? {
            val c = (-sin(angle * R) - sin(dec * R) * sin(lat * R)) /
                (cos(dec * R) * cos(lat * R))
            if (c < -1.0 || c > 1.0) return null
            return acos(c) / R / 15.0
        }

        val sunriseHalf = halfDayAt(0.833)
        val sunrise: Double
        val sunset: Double
        if (sunriseHalf == null) {
            // Polar day/night: no real sunrise. Keep a usable six-hour reference so the
            // high-latitude rule below can still produce something sensible.
            sunrise = noon - 6.0
            sunset = noon + 6.0
        } else {
            sunrise = noon - sunriseHalf
            sunset = noon + sunriseHalf
        }

        val fajrHalf = halfDayAt(settings.method.fajrAngle)
        val fajr = fajrHalf?.let { noon - it }
            ?: highLatitudeTime(
                noon = noon,
                sunrise = sunrise,
                sunset = sunset,
                angle = settings.method.fajrAngle,
                isFajr = true,
                rule = settings.highLatitude
            )

        val maghrib = sunset
        val isha = if (settings.method.ishaAngle != null) {
            val ishaHalf = halfDayAt(settings.method.ishaAngle)
            ishaHalf?.let { noon + it }
                ?: highLatitudeTime(
                    noon = noon,
                    sunrise = sunrise,
                    sunset = sunset,
                    angle = settings.method.ishaAngle,
                    isFajr = false,
                    rule = settings.highLatitude
                )
        } else {
            val interval = settings.method.ishaIntervalMinutes!! + if (ramadan) 30 else 0
            maghrib + interval / 60.0
        }

        val shadow = settings.asr.shadowFactor + tan(abs(lat - dec) * R)
        val asrAltitude = atan(1.0 / shadow)
        val asrC = (sin(asrAltitude) - sin(dec * R) * sin(lat * R)) /
            (cos(dec * R) * cos(lat * R))
        val asrTime = acos(asrC.coerceIn(-1.0, 1.0)) / R / 15.0

        return doubleArrayOf(fajr, sunrise, noon, noon + asrTime, maghrib, isha)
    }

    private fun highLatitudeTime(
        noon: Double,
        sunrise: Double,
        sunset: Double,
        angle: Double,
        isFajr: Boolean,
        rule: HighLatitudeRule
    ): Double {
        if (rule == HighLatitudeRule.NONE) {
            // The old fallback: a midnight value rather than an invalid acos argument.
            return if (isFajr) noon - 12.0 else noon + 12.0
        }
        val dayLength = (sunset - sunrise).coerceIn(0.0, 24.0)
        val nightLength = 24.0 - dayLength
        val portion = when (rule) {
            HighLatitudeRule.ANGLE_BASED -> (angle / 60.0).coerceIn(0.0, 1.0)
            HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
            HighLatitudeRule.MIDDLE_OF_NIGHT -> 0.5
            HighLatitudeRule.NONE -> 0.0
        }
        return if (isFajr) sunrise - nightLength * portion
        else sunset + nightLength * portion
    }
}
