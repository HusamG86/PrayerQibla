package com.sa.prayerqibla

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import java.util.Locale

class AdhanReceiver : BroadcastReceiver() {
    companion object {
        // Bump this when the sound or channel configuration changes. Android does not allow
        // editing a channel's sound after it has been created.
        private const val CHANNEL_VERSION = 2
        private val PRAYER_IDS = intArrayOf(
            R.string.prayer_fajr,
            R.string.prayer_sunrise,
            R.string.prayer_dhuhr,
            R.string.prayer_asr,
            R.string.prayer_maghrib,
            R.string.prayer_isha
        )
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val i = intent.getIntExtra("i", 0).coerceIn(0, 5)
        val language = ctx.getSharedPreferences("adhan", Context.MODE_PRIVATE)
            .getString("lang", "ar") ?: "ar"

        val config = Configuration(ctx.resources.configuration)
        config.setLocale(
            if (language in setOf("ar", "en", "ur", "fr", "id", "tr")) {
                Locale.forLanguageTag(language)
            } else {
                Locale.ENGLISH
            }
        )
        val localized = ctx.createConfigurationContext(config)
        val res = localized.resources
        val prayerName = localized.getString(PRAYER_IDS[i])

        val nm = ctx.getSystemService(NotificationManager::class.java)

        // Replace app/src/main/res/raw/adhan.mp3 to use a different adhan sound.
        val chId = "adhan_v${CHANNEL_VERSION}_custom"
        val uri: Uri = Uri.parse("android.resource://${ctx.packageName}/${R.raw.adhan}")

        val ch = NotificationChannel(
            chId,
            localized.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        )
        ch.setSound(
            uri,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)

        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AdhanScheduler.schedule(ctx)
            return
        }

        val open = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, chId)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(localized.getString(R.string.notification_title, prayerName))
            .setContentText(localized.getString(R.string.notification_text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
        nm.notify(100 + i, n)
        AdhanScheduler.schedule(ctx)
    }
}
