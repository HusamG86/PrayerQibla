package com.sa.prayerqibla

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

class MainActivity : Activity() {
    companion object {
        private const val RC_LOCATION = 1
        private const val RC_NOTIFICATIONS = 2
        private const val PREFS = "adhan"
        private const val APP_ORIGIN = "https://appassets.androidplatform.net"
        private const val LIGHT_BG = "#f5f7f2"
        private const val DARK_BG = "#0c1511"
        private const val ALL_PRAYER_MASK =
            (1 shl 0) or (1 shl 2) or (1 shl 3) or (1 shl 4) or (1 shl 5)
    }

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private var pendingOrigin: String? = null
    private var pendingCb: GeolocationPermissions.Callback? = null
    private var pendingLocationPermission = false

    private val locationHandler = Handler(Looper.getMainLooper())
    private var locationPending = false
    private var locationTimeout: Runnable? = null
    private val locationProviders = mutableListOf<String>()

    @Suppress("DEPRECATION")
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (locationPending) finishLocationSuccess(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val sensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var compassActive = false
    private var sensorsRegistered = false
    private var useAccelMag = false
    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var lastHeadingSent = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                    val rm = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rm, event.values)
                    sendHeading(rm)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    if (useAccelMag) computeFromAccelMag()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    if (useAccelMag) computeFromAccelMag()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            val label = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
                else -> "unreliable"
            }
            runOnUiThread {
                if (::web.isInitialized) {
                    web.evaluateJavascript(
                        "window.setCompassAccuracy && window.setCompassAccuracy('$label')",
                        null
                    )
                }
            }
        }
    }

    /** Called from the page: saves settings and reschedules alarms. */
    inner class Bridge {
        @JavascriptInterface
        fun getPrayerMask(): Int =
            prefs.getInt("mask", if (prefs.getBoolean("on", false)) ALL_PRAYER_MASK else 0)

        @JavascriptInterface
        fun getConfig(): String {
            val o = JSONObject()
                .put("mask", getPrayerMask())
                .put("lang", prefs.getString("lang", "ar") ?: "ar")
                .put("lat", prefs.getString("lat", null)?.toDoubleOrNull() ?: JSONObject.NULL)
                .put("lng", prefs.getString("lng", null)?.toDoubleOrNull() ?: JSONObject.NULL)
                .put("zone", prefs.getString("zone", null) ?: "Asia/Riyadh")
                .put("custom", prefs.getBoolean("custom", false))
                .put("city", prefs.getString("city", "") ?: "")
                .put("name", prefs.getString("name", "") ?: "")
                .put("method", prefs.getString("method", PrayerCalc.Method.UMM_AL_QURA.name))
                .put("asr", prefs.getString("asr", PrayerCalc.AsrMethod.STANDARD.name))
                .put(
                    "highLat",
                    prefs.getString("highLat", PrayerCalc.HighLatitudeRule.ANGLE_BASED.name)
                )
                // Lets the page distinguish "never configured" from "configured as Riyadh".
                .put("saved", prefs.contains("lat") || prefs.contains("mask"))
            return o.toString()
        }

        @JavascriptInterface
        fun getDeclination(lat: Double, lng: Double): Double {
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return 0.0
            return GeomagneticField(
                lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()
            ).declination.toDouble()
        }

        @JavascriptInterface
        fun getDayTimes(
            epochDay: Long,
            lat: Double,
            lng: Double,
            zone: String,
            method: String,
            asr: String,
            highLat: String
        ): String {
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return "[]"
            val date = LocalDate.ofEpochDay(epochDay)
            val zoneId = TimeMath.zoneFromPrefs(zone, null)
            val calcSettings = prayerSettings(method, asr, highLat)
            val utc = PrayerCalc.timesUtc(date, lat, lng, calcSettings)
            // Convert each event with the zone rule for that instant, not one offset
            // for the whole day; this matters on DST transition days.
            val local = DoubleArray(utc.size) {
                TimeMath.localHoursAt(date, utc[it], zoneId)
            }
            return toJson(local)
        }

        @JavascriptInterface
        fun getMonthTimes(
            year: Int,
            month: Int,
            lat: Double,
            lng: Double,
            zone: String,
            method: String,
            asr: String,
            highLat: String
        ): String {
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return "[]"
            val first = try {
                LocalDate.of(year, month, 1)
            } catch (_: Exception) {
                return "[]"
            }
            val zoneId = TimeMath.zoneFromPrefs(zone, null)
            val calcSettings = prayerSettings(method, asr, highLat)
            val out = JSONArray()
            for (day in 1..first.lengthOfMonth()) {
                val date = first.withDayOfMonth(day)
                val utc = PrayerCalc.timesUtc(date, lat, lng, calcSettings)
                val local = utc.map { TimeMath.localHoursAt(date, it, zoneId) }
                out.put(JSONArray(local))
            }
            return out.toString()
        }

        @JavascriptInterface
        fun lookupCity(lat: Double, lng: Double, language: String) {
            if (!Geocoder.isPresent()) return
            val locale = if (language in setOf("ar", "en", "ur", "fr", "id", "tr")) {
                Locale.forLanguageTag(language)
            } else {
                Locale.ENGLISH
            }
            val geocoder = if (Build.VERSION.SDK_INT >= 33) {
                Geocoder(this@MainActivity, locale)
            } else {
                Geocoder(this@MainActivity)
            }
            val onResult: (Address?) -> Unit = { address ->
                val city = address?.locality?.takeIf { it.isNotBlank() }
                    ?: address?.subLocality?.takeIf { it.isNotBlank() }
                    ?: address?.subAdminArea?.takeIf { it.isNotBlank() }
                    ?: address?.adminArea?.takeIf { it.isNotBlank() }
                if (!city.isNullOrBlank()) {
                    Handler(Looper.getMainLooper()).post {
                        if (::web.isInitialized) {
                            web.evaluateJavascript(
                                "window.setAcquiredCity(${JSONObject.quote(city)}, $lat, $lng)",
                                null
                            )
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) =
                        onResult(addresses.firstOrNull())

                    override fun onError(errorMessage: String?) = Unit
                })
            } else {
                Thread {
                    try {
                        onResult(geocoder.getFromLocation(lat, lng, 1)?.firstOrNull())
                    } catch (_: Exception) {
                    }
                }.start()
            }
        }

        @JavascriptInterface
        fun sync(json: String) {
            val o = try {
                JSONObject(json)
            } catch (_: Exception) {
                return
            }
            val enabledMask = o.optInt("mask", 0) and ALL_PRAYER_MASK
            val lat = o.optDouble("lat", Double.NaN)
            val lng = o.optDouble("lng", Double.NaN)
            val zone = o.optString("zone", "Asia/Riyadh").takeIf { it.isNotBlank() } ?: "Asia/Riyadh"
            val zoneId = TimeMath.zoneFromPrefs(zone, null)
            val edit = prefs.edit()
                .putBoolean("on", enabledMask != 0)
                .putInt("mask", enabledMask)
                .putString("lang", o.optString("lang", "ar"))
                .putString("zone", zone)
                .putString("tz", TimeMath.offsetHoursAt(LocalDate.now(zoneId), zoneId).toString())
                .putString("method", o.optString("method", PrayerCalc.Method.UMM_AL_QURA.name))
                .putString("asr", o.optString("asr", PrayerCalc.AsrMethod.STANDARD.name))
                .putString(
                    "highLat",
                    o.optString("highLat", PrayerCalc.HighLatitudeRule.ANGLE_BASED.name)
                )
                .putBoolean("custom", o.optBoolean("custom", false))
                .putString("city", o.optString("city", ""))
                .putString("name", o.optString("name", ""))
            if (lat.isFinite() && lng.isFinite() &&
                lat in -90.0..90.0 && lng in -180.0..180.0
            ) {
                edit.putString("lat", lat.toString()).putString("lng", lng.toString())
            }
            edit.apply()
            AdhanScheduler.schedule(applicationContext)
            PrayerWidgetProvider.refreshAll(applicationContext)
        }

        @JavascriptInterface
        fun getPermissionState(): String {
            val notifications = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            val exactAlarms = Build.VERSION.SDK_INT < 31 ||
                (getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
                    ?.canScheduleExactAlarms() != false
            val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val battery = power?.isIgnoringBatteryOptimizations(packageName) ?: true
            return JSONObject()
                .put("notifications", notifications)
                .put("exactAlarms", exactAlarms)
                .put("battery", battery)
                .toString()
        }

        @JavascriptInterface
        fun requestLocation() {
            runOnUiThread { startLocationRequest() }
        }

        @JavascriptInterface
        fun requestNotificationPermission() {
            runOnUiThread { requestNotifications() }
        }

        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread { openNotificationSettingsScreen() }
        }

        @JavascriptInterface
        fun openExactAlarmSettings() {
            runOnUiThread { openExactAlarmScreen() }
        }

        @JavascriptInterface
        fun openBatterySettings() {
            runOnUiThread { openBatteryScreen() }
        }

        @JavascriptInterface
        fun startCompass(): Boolean = startNativeCompass()

        @JavascriptInterface
        fun stopCompass() = stopNativeCompass()

        @JavascriptInterface
        fun setNativeTheme(dark: Boolean) {
            runOnUiThread { applyNativeTheme(dark) }
        }
    }

    private fun prayerSettings(method: String, asr: String, highLat: String) =
        PrayerCalc.Settings(
            method = PrayerCalc.Method.fromKey(method),
            asr = PrayerCalc.AsrMethod.fromKey(asr),
            highLatitude = PrayerCalc.HighLatitudeRule.fromKey(highLat)
        )

    private fun toJson(values: DoubleArray): String {
        val a = JSONArray()
        values.forEach { a.put(it) }
        return a.toString()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dark = isSystemDark()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        root = FrameLayout(this).apply { setBackgroundColor(backgroundColor(dark)) }
        web = WebView(this).apply { setBackgroundColor(backgroundColor(dark)) }
        root.addView(
            web,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        updateSystemBars(dark)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
        }
        web.addJavascriptInterface(Bridge(), "NativeAdhan")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ) = loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (url.scheme == "https" && url.host == "appassets.androidplatform.net") {
                    return false
                }
                // Never let a remote page inherit the NativeAdhan bridge. Open it externally.
                if (url.scheme == "http" || url.scheme == "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                    }
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                refreshJsPermissions()
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                // Without this the whole app process is killed when the renderer
                // dies (e.g. low memory). Recreating the activity restores the UI.
                if (::web.isInitialized && web === view) recreate()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                cb: GeolocationPermissions.Callback
            ) {
                // Origin strings can differ by trailing slash; compare the host.
                val host = try {
                    Uri.parse(origin).host
                } catch (_: Exception) {
                    null
                }
                if (host != "appassets.androidplatform.net") {
                    cb.invoke(origin, false, false)
                    return
                }
                if (hasLocationPermission()) {
                    // Don't retain the grant: a later revocation must not keep
                    // the WebView fallback working silently.
                    cb.invoke(origin, true, false)
                    return
                }
                pendingOrigin = origin
                pendingCb = cb
                pendingLocationPermission = false
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    RC_LOCATION
                )
            }
        }
        web.loadUrl("$APP_ORIGIN/assets/index.html")
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
        AdhanScheduler.schedule(applicationContext)
        if (compassActive) registerSensors()
        refreshJsPermissions()
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        unregisterSensors()
        super.onPause()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        stopNativeCompass()
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        code: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (code) {
            RC_LOCATION -> {
                val granted = grantResults.indices.any { index ->
                    grantResults[index] == PackageManager.PERMISSION_GRANTED &&
                        (permissions.getOrNull(index) == Manifest.permission.ACCESS_FINE_LOCATION ||
                            permissions.getOrNull(index) == Manifest.permission.ACCESS_COARSE_LOCATION)
                }
                if (pendingLocationPermission) {
                    pendingLocationPermission = false
                    if (granted) startLocationRequest() else finishLocationError("denied")
                }
                pendingCb?.invoke(pendingOrigin, granted, false)
                pendingCb = null
                pendingOrigin = null
            }
            RC_NOTIFICATIONS -> {
                AdhanScheduler.schedule(applicationContext)
                refreshJsPermissions()
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun startLocationRequest() {
        if (!hasLocationPermission()) {
            pendingLocationPermission = true
            pendingOrigin = null
            pendingCb = null
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                RC_LOCATION
            )
            return
        }
        if (locationPending) return

        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            finishLocationError("unavailable")
            return
        }
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val providers = try {
            manager.getProviders(true)
        } catch (_: Exception) {
            emptyList()
        }.filter { provider ->
            when (provider) {
                LocationManager.GPS_PROVIDER -> fine
                LocationManager.NETWORK_PROVIDER -> true
                LocationManager.PASSIVE_PROVIDER -> false
                else -> fine
            }
        }

        // A recent cached fix returns instantly and avoids waiting for GPS.
        val lastKnown = bestLastKnown(manager, providers)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= 10 * 60 * 1000) {
            finishLocationSuccess(lastKnown)
            return
        }

        locationPending = true
        locationProviders.clear()
        locationProviders.addAll(providers)
        var requested = false
        for (provider in providers) {
            try {
                manager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                requested = true
            } catch (_: Exception) {
                // Provider disabled or permission revoked between the checks.
            }
        }
        if (!requested) {
            if (lastKnown != null) {
                finishLocationSuccess(lastKnown)
            } else {
                finishLocationError("unavailable")
            }
            return
        }

        val timeout = Runnable {
            if (!locationPending) return@Runnable
            val fallback = bestLastKnown(manager, locationProviders.toList())
            if (fallback != null) {
                finishLocationSuccess(fallback)
            } else {
                finishLocationError("unavailable")
            }
        }
        locationTimeout = timeout
        locationHandler.postDelayed(timeout, 15000)
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnown(manager: LocationManager, providers: List<String>): Location? {
        if (providers.isEmpty()) return null
        return providers.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }
    }

    private fun finishLocationSuccess(location: Location) {
        stopLocationUpdates()
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript(
                    "window.setNativeLocation && window.setNativeLocation(" +
                        location.latitude + ", " + location.longitude + ")",
                    null
                )
            }
        }
    }

    private fun finishLocationError(code: String) {
        stopLocationUpdates()
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript(
                    "window.setNativeLocationError && window.setNativeLocationError(" +
                        JSONObject.quote(code) + ")",
                    null
                )
            }
        }
    }

    private fun stopLocationUpdates() {
        locationPending = false
        locationTimeout?.let { locationHandler.removeCallbacks(it) }
        locationTimeout = null
        if (locationProviders.isEmpty()) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager != null) {
            try {
                manager.removeUpdates(locationListener)
            } catch (_: Exception) {
            }
        }
        locationProviders.clear()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            if (!prefs.getBoolean("askedNotif", false)) {
                prefs.edit().putBoolean("askedNotif", true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    RC_NOTIFICATIONS
                )
            } else {
                openNotificationSettingsScreen()
            }
        } else {
            // Already granted: enabling a single prayer must not open Settings.
            refreshJsPermissions()
        }
    }

    private fun openNotificationSettingsScreen() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openExactAlarmScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            } catch (_: Exception) {
            }
        }
        openAppSettings()
    }

    private fun openBatteryScreen() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun refreshJsPermissions() {
        if (!::web.isInitialized) return
        web.evaluateJavascript(
            "window.refreshPermissionState && window.refreshPermissionState()",
            null
        )
    }

    private fun isSystemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun backgroundColor(dark: Boolean) =
        Color.parseColor(if (dark) DARK_BG else LIGHT_BG)

    private fun applyNativeTheme(dark: Boolean) {
        if (!::root.isInitialized) return
        root.setBackgroundColor(backgroundColor(dark))
        web.setBackgroundColor(backgroundColor(dark))
        updateSystemBars(dark)
    }

    private fun updateSystemBars(dark: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(
                if (dark) 0 else mask,
                mask
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (dark) {
                0
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    // ---------------------------------------------------------------- compass

    private fun startNativeCompass(): Boolean {
        if (compassActive) return true
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (rotation != null) {
            rotationSensor = rotation
            useAccelMag = false
            compassActive = true
            runOnUiThread { registerSensors() }
            return true
        }
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accel != null && mag != null) {
            accelSensor = accel
            magSensor = mag
            useAccelMag = true
            compassActive = true
            runOnUiThread { registerSensors() }
            return true
        }
        return false
    }

    private fun stopNativeCompass() {
        compassActive = false
        unregisterSensors()
        rotationSensor = null
        accelSensor = null
        magSensor = null
    }

    private fun registerSensors() {
        if (!compassActive || sensorsRegistered) return
        val rotation = rotationSensor
        if (rotation != null) {
            sensorManager.registerListener(
                sensorListener,
                rotation,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            accelSensor?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            magSensor?.let {
                sensorManager.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
        sensorsRegistered = true
    }

    private fun unregisterSensors() {
        if (!sensorsRegistered) return
        sensorManager.unregisterListener(sensorListener)
        sensorsRegistered = false
    }

    private fun computeFromAccelMag() {
        val rm = FloatArray(9)
        if (SensorManager.getRotationMatrix(rm, null, gravity, geomagnetic)) sendHeading(rm)
    }

    /** Degrees the UI is rotated clockwise from the device's natural portrait orientation. */
    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Double {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            windowManager.defaultDisplay.rotation
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90.0
            Surface.ROTATION_180 -> 180.0
            Surface.ROTATION_270 -> 270.0
            else -> 0.0
        }
    }

    private fun sendHeading(rotationMatrix: FloatArray) {
        val now = System.currentTimeMillis()
        if (now - lastHeadingSent < 50) return
        lastHeadingSent = now
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        // getOrientation's azimuth follows the device's Y axis; compensate for landscape.
        val degrees = (
            Math.toDegrees(orientation[0].toDouble()) + displayRotationDegrees() + 360.0
            ) % 360.0
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript(
                    "window.setNativeHeading && window.setNativeHeading($degrees)",
                    null
                )
            }
        }
    }
}
