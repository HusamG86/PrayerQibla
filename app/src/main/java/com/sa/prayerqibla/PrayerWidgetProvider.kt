package com.sa.prayerqibla

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Home-screen summary of the next prayer, kept in sync with the app's saved settings. */
class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    companion object {
        private val prayerIndexes = intArrayOf(0, 2, 3, 4, 5)

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences("adhan", Context.MODE_PRIVATE)
            val lang = prefs.getString("lang", "ar") ?: "ar"
            val locale = Locale.forLanguageTag(lang)
            val views = RemoteViews(context.packageName, R.layout.prayer_widget)
            val launch = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launch)

            val lat = prefs.getString("lat", null)?.toDoubleOrNull()
            val lng = prefs.getString("lng", null)?.toDoubleOrNull()
            val zone = TimeMath.zoneFromPrefs(
                prefs.getString("zone", null), prefs.getString("tz", null)?.toDoubleOrNull()
            )
            val now = System.currentTimeMillis()
            val today = TimeMath.todayIn(now, zone)
            val city = prefs.getString("name", "")?.takeIf { it.isNotBlank() }
                ?: prefs.getString("city", "")?.takeIf { it.isNotBlank() }
                ?: label(lang, "Prayer times", "مواقيت الصلاة", "Heures de prière", "Namaz vakitleri", "اوقات نماز", "Waktu salat")
            views.setTextViewText(R.id.widget_location, city)

            if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                views.setTextViewText(R.id.widget_prayer, label(lang, "Open the app to set your location", "افتح التطبيق لتحديد موقعك", "Ouvrez l’app pour définir votre position", "Konumunuzu ayarlamak için uygulamayı açın", "مقام طے کرنے کے لیے ایپ کھولیں", "Buka aplikasi untuk mengatur lokasi"))
                views.setTextViewText(R.id.widget_time, "—")
                views.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)))
            } else {
                val settings = PrayerCalc.Settings(
                    method = PrayerCalc.Method.fromKey(prefs.getString("method", null)),
                    asr = PrayerCalc.AsrMethod.fromKey(prefs.getString("asr", null)),
                    highLatitude = PrayerCalc.HighLatitudeRule.fromKey(prefs.getString("highLat", null))
                )
                val prayerNames = prayerNames(lang)
                var found: Pair<Int, Long>? = null
                for (offset in 0..1) {
                    val date = today.plusDays(offset.toLong())
                    val times = PrayerCalc.timesUtc(date, lat, lng, settings)
                    for (index in prayerIndexes) {
                        val at = TimeMath.utcHoursToMillis(date, times[index])
                        if (at > now && (found == null || at < found!!.second)) found = index to at
                    }
                    if (found != null) break
                }
                if (found != null) {
                    val prayer = found!!.first
                    val at = found!!.second
                    val time = java.time.Instant.ofEpochMilli(at).atZone(zone)
                        .format(DateTimeFormatter.ofPattern(if (lang == "ar" || lang == "ur") "h:mm a" else "h:mm a", locale))
                    views.setTextViewText(R.id.widget_prayer, label(lang, "Next prayer · ", "الصلاة القادمة · ", "Prochaine prière · ", "Sıradaki namaz · ", "اگلی نماز · ", "Salat berikutnya · ") + prayerNames[prayer])
                    views.setTextViewText(R.id.widget_time, time)
                    views.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale)))
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun prayerNames(lang: String): Array<String> = when (lang) {
            "ar" -> arrayOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
            "fr" -> arrayOf("Fajr", "Lever du soleil", "Dhuhr", "Asr", "Maghrib", "Isha")
            "tr" -> arrayOf("İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı")
            "ur" -> arrayOf("فجر", "طلوع آفتاب", "ظہر", "عصر", "مغرب", "عشاء")
            "in", "id" -> arrayOf("Subuh", "Terbit", "Zuhur", "Asar", "Magrib", "Isya")
            else -> arrayOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
        }

        private fun label(lang: String, en: String, ar: String, fr: String, tr: String, ur: String, id: String) =
            when (lang) { "ar" -> ar; "fr" -> fr; "tr" -> tr; "ur" -> ur; "in", "id" -> id; else -> en }
    }
}
