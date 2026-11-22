package com.sa.prayerqibla.ui

import android.content.Context
import android.text.format.DateFormat
import com.sa.prayerqibla.data.AppConfig
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.chrono.HijrahDate
import java.util.Locale

internal fun uiLocale(lang: String): Locale =
    runCatching { Locale.forLanguageTag(lang) }.getOrDefault(Locale.ENGLISH)

internal fun digits(value: Int, lang: String, minWidth: Int = 1): String =
    String.format(uiLocale(lang), "%0${minWidth}d", value)

internal fun digits(value: Long, lang: String): String =
    String.format(uiLocale(lang), "%d", value)

/** Wall-clock formatting with the user's 12/24-hour choice, locale digits and translated AM/PM. */
internal fun formatClockLabel(
    context: Context,
    hours: Double,
    config: AppConfig,
    lang: String,
    amLabel: String,
    pmLabel: String,
    force24: Boolean = false
): String {
    val total = Math.round(hours * 60).toInt()
    val hour = Math.floorMod(total / 60, 24)
    val minute = Math.floorMod(total % 60, 60)
    val use24 = force24 || config.timeFormat == "24" ||
        (config.timeFormat == "auto" && DateFormat.is24HourFormat(context))
    if (use24) return "${digits(hour, lang, 2)}:${digits(minute, lang, 2)}"
    val suffix = if (hour >= 12) pmLabel else amLabel
    val display = if (hour % 12 == 0) 12 else hour % 12
    return "${digits(display, lang)}:${digits(minute, lang, 2)} $suffix"
}

internal fun formatCountdown(seconds: Long, lang: String): String {
    val safe = seconds.coerceAtLeast(0)
    val h = safe / 3600
    val m = (safe % 3600) / 60
    val s = safe % 60
    return "${digits(h.toInt(), lang, 2)}:${digits(m.toInt(), lang, 2)}:${digits(s.toInt(), lang, 2)}"
}

internal fun formatDates(date: LocalDate, lang: String): String {
    val locale = uiLocale(lang)
    val hijriFormatter = DateTimeFormatter.ofPattern("d MMMM y", locale)
        .withDecimalStyle(DecimalStyle.of(locale))
    val gregorianFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM y", locale)
        .withDecimalStyle(DecimalStyle.of(locale))
    val hijri = try {
        hijriFormatter.format(HijrahDate.from(date))
    } catch (_: Exception) {
        date.toString()
    }
    return "$hijri · ${gregorianFormatter.format(date)}"
}
