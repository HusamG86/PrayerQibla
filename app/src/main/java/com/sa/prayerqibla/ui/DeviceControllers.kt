package com.sa.prayerqibla.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.core.content.ContextCompat
import com.sa.prayerqibla.data.PrayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Native compass sensors, owned outside the WebView. The UI observes [heading] and
 * [accuracy] and applies spring-damped rendering; accuracy is surfaced as text, not color.
 */
class CompassController(private val context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private val _accuracy = MutableStateFlow<String?>(null)
    val accuracy: StateFlow<String?> = _accuracy.asStateFlow()

    private var rotationSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var magSensor: Sensor? = null
    private var useAccelMag = false
    private var registered = false
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var lastSent = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                    val matrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    emit(matrix)
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    if (useAccelMag) emitFromAccelMag()
                }

                Sensor.TYPE_MAGNETIC_FIELD -> {
                    System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                    if (useAccelMag) emitFromAccelMag()
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            _accuracy.value = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
                else -> "unreliable"
            }
        }
    }

    fun start(): Boolean {
        if (_active.value) return true
        val sensors = manager ?: return false
        val rotation = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        if (rotation != null) {
            rotationSensor = rotation
            useAccelMag = false
            _active.value = true
            register()
            return true
        }
        val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (accel != null && mag != null) {
            accelSensor = accel
            magSensor = mag
            useAccelMag = true
            _active.value = true
            register()
            return true
        }
        return false
    }

    fun stop() {
        _active.value = false
        unregister()
        rotationSensor = null
        accelSensor = null
        magSensor = null
        _accuracy.value = null
    }

    fun onResume() {
        if (_active.value) register()
    }

    fun onPause() = unregister()

    fun declination(lat: Double, lng: Double): Double = GeomagneticField(
        lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()
    ).declination.toDouble()

    private fun register() {
        val sensors = manager ?: return
        if (registered || !_active.value) return
        rotationSensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            ?: run {
                accelSensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                magSensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
            }
        registered = true
    }

    private fun unregister() {
        if (!registered) return
        manager?.unregisterListener(listener)
        registered = false
    }

    private fun emitFromAccelMag() {
        val matrix = FloatArray(9)
        if (SensorManager.getRotationMatrix(matrix, null, gravity, geomagnetic)) emit(matrix)
    }

    private fun emit(matrix: FloatArray) {
        val now = System.currentTimeMillis()
        if (now - lastSent < 50) return
        lastSent = now
        val orientation = FloatArray(3)
        SensorManager.getOrientation(matrix, orientation)
        val degrees = (Math.toDegrees(orientation[0].toDouble()) + displayRotation() + 360.0) % 360.0
        _heading.value = degrees.toFloat()
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
        return when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}

/** Location acquisition with the same cached-fix/timeout strategy as the old bridge. */
class LocationController(private val activity: Activity, private val vm: PrayerViewModel) {
    companion object {
        const val RC_LOCATION = 21
        private const val TIMEOUT_MS = 15_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private var awaitingPermission = false
    private var timeout: Runnable? = null
    private val providers = mutableListOf<String>()

    @Suppress("DEPRECATION")
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (pending) finish(location)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun requestLocation() {
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        val granted = ContextCompat.checkSelfPermission(activity, fine) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(activity, coarse) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            awaitingPermission = true
            activity.requestPermissions(arrayOf(fine, coarse), RC_LOCATION)
            return
        }
        start()
    }

    fun onRequestPermissionsResult(code: Int, grantResults: IntArray): Boolean {
        if (code != RC_LOCATION) return false
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (awaitingPermission) {
            awaitingPermission = false
            if (granted) start() else vm.setLocationError("denied")
        }
        return true
    }

    fun destroy() {
        stopUpdates()
    }

    @Suppress("MissingPermission")
    private fun start() {
        if (pending) return
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            vm.setLocationError("unavailable")
            return
        }
        val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = try {
            manager.getProviders(true).filter { provider ->
                when (provider) {
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER -> provider != LocationManager.GPS_PROVIDER || fine

                    else -> false
                }
            }
        } catch (_: SecurityException) {
            emptyList()
        }
        val lastKnown = bestLastKnown(manager, enabled)
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.time <= 10 * 60 * 1000) {
            finish(lastKnown)
            return
        }
        pending = true
        providers.clear()
        providers.addAll(enabled)
        var requested = false
        for (provider in enabled) {
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                requested = true
            } catch (_: Exception) {
            }
        }
        if (!requested) {
            if (lastKnown != null) finish(lastKnown) else vm.setLocationError("unavailable")
            return
        }
        timeout = Runnable {
            if (!pending) return@Runnable
            val fallback = bestLastKnown(manager, providers.toList())
            if (fallback != null) finish(fallback) else vm.setLocationError("unavailable")
        }
        handler.postDelayed(timeout!!, TIMEOUT_MS)
    }

    @Suppress("MissingPermission")
    private fun bestLastKnown(manager: LocationManager, enabled: List<String>): Location? =
        enabled.mapNotNull { provider ->
            try {
                manager.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
        }.maxByOrNull { it.time }

    private fun finish(location: Location) {
        stopUpdates()
        vm.setLocation(location.latitude, location.longitude)
    }

    private fun stopUpdates() {
        pending = false
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
        if (providers.isEmpty()) return
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try {
            manager?.removeUpdates(listener)
        } catch (_: Exception) {
        }
        providers.clear()
    }
}
