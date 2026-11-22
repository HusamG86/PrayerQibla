package com.sa.prayerqibla.data

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.sa.prayerqibla.AdhanScheduler
import com.sa.prayerqibla.PrayerCalc
import com.sa.prayerqibla.TimeMath
import com.sa.prayerqibla.widget.WidgetRefresh
import java.time.LocalDate
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppConfig(
    val mask: Int = 0,
    val lang: String = "ar",
    val lat: Double = 24.7136,
    val lng: Double = 46.6753,
    val zone: String = "Asia/Riyadh",
    val custom: Boolean = false,
    val cityId: String = "riyadh",
    val name: String = "",
    val method: String = "UMM_AL_QURA",
    val asr: String = "STANDARD",
    val highLat: String = "ANGLE_BASED",
    val timeFormat: String = "auto",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val saved: Boolean = false
)

data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val battery: Boolean
) {
    val allGranted: Boolean get() = notifications && exactAlarms && battery
}

data class City(val id: String, val ar: String, val en: String, val lat: Double, val lng: Double)

/**
 * Kotlin replacement for the old WebView JavaScript bridge. Reads and writes the very same
 * SharedPreferences ("adhan") so alarms and upgraded installs keep working exactly as before.
 */
class PrayerRepository(context: Context) {
    companion object {
        const val ALL_PRAYER_MASK = 61

        /** Indexed by the six UI prayer rows; index 1 (sunrise) has no alarm bit. */
        val PRAYER_BITS = intArrayOf(1, 0, 4, 8, 16, 32)

        val CITIES: List<City> = listOf(
            City("riyadh", "الرياض", "Riyadh", 24.7136, 46.6753),
            City("jeddah", "جدة", "Jeddah", 21.4858, 39.1925),
            City("makkah", "مكة المكرمة", "Makkah", 21.3891, 39.8579),
            City("madinah", "المدينة المنورة", "Madinah", 24.5247, 39.5692),
            City("dammam", "الدمام", "Dammam", 26.4207, 50.0888),
            City("buraydah", "بريدة", "Buraydah", 26.326, 43.975),
            City("abha", "أبها", "Abha", 18.2164, 42.5053),
            City("tabuk", "تبوك", "Tabuk", 28.3838, 36.5662),
            City("taif", "الطائف", "Taif", 21.2703, 40.4158),
            City("hail", "حائل", "Hail", 27.5114, 41.7208),
            City("jazan", "جازان", "Jazan", 16.8892, 42.5611),
            City("najran", "نجران", "Najran", 17.4917, 44.1322),
            City("khobar", "الخبر", "Al Khobar", 26.2172, 50.1971),
            City("yanbu", "ينبع", "Yanbu", 24.0895, 38.0618),
            City("hofuf", "الهفوف", "Hofuf", 25.3648, 49.5878),
            City("khamis", "خميس مشيط", "Khamis Mushait", 18.3, 42.7292),
            City("jubail", "الجبيل", "Jubail", 27.0046, 49.646),
            City("sakaka", "سكاكا", "Sakaka", 29.9697, 40.2064),
            City("arar", "عرعر", "Arar", 30.9753, 41.0381)
        )

        val METHODS = listOf(
            "UMM_AL_QURA",
            "MUSLIM_WORLD_LEAGUE",
            "ISNA",
            "EGYPTIAN",
            "KARACHI"
        )
        val ASR_METHODS = listOf("STANDARD", "HANAFI")
        val HIGH_LAT_RULES = listOf("ANGLE_BASED", "ONE_SEVENTH", "MIDDLE_OF_NIGHT", "NONE")
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("adhan", Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadConfig(): AppConfig {
        val mask = prefs.getInt(
            "mask",
            if (prefs.getBoolean("on", false)) ALL_PRAYER_MASK else 0
        )
        val lang = prefs.getString("lang", "ar") ?: "ar"
        val zone = prefs.getString("zone", null) ?: "Asia/Riyadh"
        val theme = prefs.getString("theme", null)?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
        } ?: ThemeMode.SYSTEM
        return AppConfig(
            mask = mask and ALL_PRAYER_MASK,
            lang = lang,
            lat = prefs.getString("lat", null)?.toDoubleOrNull() ?: 24.7136,
            lng = prefs.getString("lng", null)?.toDoubleOrNull() ?: 46.6753,
            zone = zone,
            custom = prefs.getBoolean("custom", false),
            cityId = prefs.getString("city", "") ?: "",
            name = prefs.getString("name", "") ?: "",
            method = prefs.getString("method", PrayerCalc.Method.UMM_AL_QURA.name)
                ?: PrayerCalc.Method.UMM_AL_QURA.name,
            asr = prefs.getString("asr", PrayerCalc.AsrMethod.STANDARD.name)
                ?: PrayerCalc.AsrMethod.STANDARD.name,
            highLat = prefs.getString("highLat", PrayerCalc.HighLatitudeRule.ANGLE_BASED.name)
                ?: PrayerCalc.HighLatitudeRule.ANGLE_BASED.name,
            timeFormat = prefs.getString("timeFormat", "auto") ?: "auto",
            theme = theme,
            saved = prefs.contains("lat") || prefs.contains("mask")
        )
    }

    fun saveConfig(config: AppConfig) {
        val enabledMask = config.mask and ALL_PRAYER_MASK
        val zone = config.zone.takeIf { it.isNotBlank() } ?: "Asia/Riyadh"
        val zoneId = TimeMath.zoneFromPrefs(zone, null)
        val edit = prefs.edit()
            .putBoolean("on", enabledMask != 0)
            .putInt("mask", enabledMask)
            .putString("lang", config.lang)
            .putString("zone", zone)
            .putString("tz", TimeMath.offsetHoursAt(LocalDate.now(zoneId), zoneId).toString())
            .putString("method", config.method)
            .putString("asr", config.asr)
            .putString("highLat", config.highLat)
            .putBoolean("custom", config.custom)
            .putString("city", config.cityId)
            .putString("name", config.name)
            .putString("timeFormat", config.timeFormat)
            .putString("theme", config.theme.name)
        if (config.lat.isFinite() && config.lng.isFinite() &&
            config.lat in -90.0..90.0 && config.lng in -180.0..180.0
        ) {
            edit.putString("lat", config.lat.toString())
                .putString("lng", config.lng.toString())
        }
        edit.apply()
        AdhanScheduler.schedule(appContext)
        WidgetRefresh.refreshAll(appContext)
    }

    fun dayTimes(epochDay: Long, config: AppConfig): DoubleArray = dayTimes(
        epochDay,
        config.lat,
        config.lng,
        config.zone,
        config.method,
        config.asr,
        config.highLat
    )

    fun dayTimes(
        epochDay: Long,
        lat: Double,
        lng: Double,
        zone: String,
        method: String,
        asr: String,
        highLat: String
    ): DoubleArray {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return DoubleArray(0)
        val date = LocalDate.ofEpochDay(epochDay)
        val zoneId = TimeMath.zoneFromPrefs(zone, null)
        val utc = PrayerCalc.timesUtc(date, lat, lng, settings(method, asr, highLat))
        // Per-instant zone rules, not a single offset: correct on DST transition days.
        return DoubleArray(utc.size) { TimeMath.localHoursAt(date, utc[it], zoneId) }
    }

    fun monthTimes(year: Int, month: Int, config: AppConfig): List<DoubleArray> =
        monthTimes(
            year,
            month,
            config.lat,
            config.lng,
            config.zone,
            config.method,
            config.asr,
            config.highLat
        )

    private fun monthTimes(
        year: Int,
        month: Int,
        lat: Double,
        lng: Double,
        zone: String,
        method: String,
        asr: String,
        highLat: String
    ): List<DoubleArray> {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return emptyList()
        val first = try {
            LocalDate.of(year, month, 1)
        } catch (_: Exception) {
            return emptyList()
        }
        val zoneId = TimeMath.zoneFromPrefs(zone, null)
        val calcSettings = settings(method, asr, highLat)
        return (1..first.lengthOfMonth()).map { day ->
            val date = first.withDayOfMonth(day)
            val utc = PrayerCalc.timesUtc(date, lat, lng, calcSettings)
            DoubleArray(utc.size) { TimeMath.localHoursAt(date, utc[it], zoneId) }
        }
    }

    fun permissionState(): PermissionState {
        val notifications = Build.VERSION.SDK_INT < 33 ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val exactAlarms = Build.VERSION.SDK_INT < 31 ||
            (appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                ?.canScheduleExactAlarms() != false
        val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = power?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
        return PermissionState(notifications, exactAlarms, battery)
    }

    fun declination(lat: Double, lng: Double): Double {
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return 0.0
        return GeomagneticField(
            lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()
        ).declination.toDouble()
    }

    /** Mirrors MainActivity.Bridge.lookupCity: same locale whitelist, field priority and fallbacks. */
    fun lookupCity(lat: Double, lng: Double, lang: String, onResult: (String) -> Unit) {
        if (!Geocoder.isPresent()) return
        val locale = if (lang in setOf("ar", "en", "ur", "fr", "id", "tr")) {
            Locale.forLanguageTag(lang)
        } else {
            Locale.ENGLISH
        }
        val geocoder = if (Build.VERSION.SDK_INT >= 33) {
            Geocoder(appContext, locale)
        } else {
            Geocoder(appContext)
        }
        val deliver: (android.location.Address?) -> Unit = { address ->
            val city = address?.locality?.takeIf { it.isNotBlank() }
                ?: address?.subLocality?.takeIf { it.isNotBlank() }
                ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
                ?: address?.adminArea?.takeIf { it.isNotBlank() }
            if (!city.isNullOrBlank()) {
                // The old bridge always delivered on the main looper; keep that contract.
                mainHandler.post { onResult(city) }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) =
                    deliver(addresses.firstOrNull())

                override fun onError(errorMessage: String?) = Unit
            })
        } else {
            Thread {
                try {
                    deliver(geocoder.getFromLocation(lat, lng, 1)?.firstOrNull())
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    private fun settings(method: String, asr: String, highLat: String) = PrayerCalc.Settings(
        method = PrayerCalc.Method.fromKey(method),
        asr = PrayerCalc.AsrMethod.fromKey(asr),
        highLatitude = PrayerCalc.HighLatitudeRule.fromKey(highLat)
    )
}
