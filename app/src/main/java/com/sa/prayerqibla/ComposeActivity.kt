package com.sa.prayerqibla

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sa.prayerqibla.data.PrayerViewModel
import com.sa.prayerqibla.ui.AppActions
import com.sa.prayerqibla.ui.CompassController
import com.sa.prayerqibla.ui.LocationController
import com.sa.prayerqibla.ui.PrayerQiblaApp
import com.sa.prayerqibla.widget.WidgetManagerActivity

/** Compose entry point. The WebView-backed [MainActivity] is kept only as a legacy fallback. */
class ComposeActivity : ComponentActivity() {
    companion object {
        private const val RC_NOTIFICATIONS = 2
    }

    private val vm: PrayerViewModel by viewModels()
    private lateinit var compass: CompassController
    private lateinit var location: LocationController

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        compass = CompassController(this)
        location = LocationController(this, vm)
        val actions = AppActions(
            requestLocation = { location.requestLocation() },
            requestNotifications = ::requestNotifications,
            openNotificationSettings = ::openNotificationSettings,
            openExactAlarmSettings = ::openExactAlarmSettings,
            openBatterySettings = ::openBatterySettings,
            manageWidgets = { startActivity(Intent(this, WidgetManagerActivity::class.java)) }
        )

        setContent {
            PrayerQiblaApp(vm = vm, compass = compass, actions = actions)
        }
    }

    override fun onResume() {
        super.onResume()
        compass.onResume()
        vm.refreshPermissions()
    }

    override fun onPause() {
        compass.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        location.destroy()
        compass.stop()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        location.onRequestPermissionsResult(requestCode, grantResults)
        if (requestCode == RC_NOTIFICATIONS) vm.refreshPermissions()
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            val prefs = getSharedPreferences("adhan", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("askedNotif", false)) {
                prefs.edit().putBoolean("askedNotif", true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    RC_NOTIFICATIONS
                )
            } else {
                openNotificationSettings()
            }
        } else {
            vm.refreshPermissions()
        }
    }

    private fun openNotificationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openExactAlarmSettings() {
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

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
        }
    }
}
