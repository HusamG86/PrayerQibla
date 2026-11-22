package com.sa.prayerqibla.widget

import android.content.Context
import com.sa.prayerqibla.R
import com.sa.prayerqibla.TimeMath
import com.sa.prayerqibla.data.AppConfig
import com.sa.prayerqibla.data.PrayerRepository
import com.sa.prayerqibla.ui.UiText
import com.sa.prayerqibla.ui.formatClockLabel
import com.sa.prayerqibla.ui.uiLocale
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

data class WidgetPrayerRow(val name: String, val time: String, val isNext: Boolean)

data class WidgetData(
    val city: String,
    val nextName: String,
    val nextTime: String,
    val remainingSeconds: Long,
    val progress: Float,
    val dateLabel: String,
    val rows: List<WidgetPrayerRow>
)

object WidgetDataProvider {
    private val prayerNames = intArrayOf(
        R.string.prayer_fajr,
        R.string.prayer_sunrise,
        R.string.prayer_dhuhr,
        R.string.prayer_asr,
        R.string.prayer_maghrib,
        R.string.prayer_isha
    )
    private val rowIndexes = intArrayOf(0, 2, 3, 4, 5)

    fun load(context: Context, lang: String): WidgetData {
        val appContext = context.applicationContext
        val localized = widgetContext(appContext, lang)
        val repo = PrayerRepository(localized)
        val config = repo.loadConfig()
        val zone = TimeMath.zoneFromPrefs(
            config.zone,
            appContext.getSharedPreferences("adhan", Context.MODE_PRIVATE)
                .getString("tz", null)?.toDoubleOrNull()
        )
        val now = System.currentTimeMillis()
        val today = TimeMath.todayIn(now, zone)
        val city = NextPrayerFinder.cityOrFallback(
            config.name,
            localized.getString(R.string.widget_city_fallback)
        )
        val missingLocation = WidgetData(
            city = city,
            nextName = localized.getString(R.string.widget_no_location),
            nextTime = "—",
            remainingSeconds = 0L,
            progress = 0f,
            dateLabel = shortDate(today, lang),
            rows = emptyList()
        )
        if (!config.saved || !hasLocation(config)) return missingLocation

        val todayHours = repo.dayTimes(today.toEpochDay(), config)
        val tomorrowHours = repo.dayTimes(today.plusDays(1).toEpochDay(), config)
        val next = NextPrayerFinder.find(now, today, zone, todayHours, tomorrowHours)
            ?: return missingLocation

        val strings = UiText.forLanguage(lang)
        fun clock(hours: Double) = formatClockLabel(
            localized, hours, config, lang, strings.am, strings.pm
        )
        val nextHours = Instant.ofEpochMilli(next.atMillis).atZone(zone).run {
            hour + minute / 60.0 + second / 3600.0
        }
        val tomorrowStart = TimeMath.localHoursToMillis(today.plusDays(1), 0.0, zone)
        return WidgetData(
            city = city,
            nextName = localized.getString(prayerNames[next.index]),
            nextTime = clock(nextHours),
            remainingSeconds = ((next.atMillis - now) / 1000).coerceAtLeast(0L),
            progress = NextPrayerFinder.progress(now, next),
            dateLabel = missingLocation.dateLabel,
            rows = rowIndexes.map { index ->
                WidgetPrayerRow(
                    name = localized.getString(prayerNames[index]),
                    time = clock(todayHours[index]),
                    isNext = next.index == index && next.atMillis < tomorrowStart
                )
            }
        )
    }

    private fun hasLocation(config: AppConfig): Boolean =
        config.lat.isFinite() && config.lng.isFinite() &&
            config.lat in -90.0..90.0 && config.lng in -180.0..180.0

    private fun shortDate(date: LocalDate, lang: String): String {
        val locale = uiLocale(lang)
        return DateTimeFormatter.ofPattern("EEE, d MMM", locale)
            .withDecimalStyle(DecimalStyle.of(locale))
            .format(date)
    }
}
