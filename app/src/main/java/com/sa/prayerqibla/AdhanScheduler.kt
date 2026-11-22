package com.sa.prayerqibla

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AdhanScheduler {
    private const val DAYS_AHEAD = 7
    private const val SLOTS_PER_DAY = 6

    private fun pi(ctx: Context, code: Int, idx: Int): PendingIntent {
        val intent = Intent(ctx, AdhanReceiver::class.java).putExtra("i", idx)
        return PendingIntent.getBroadcast(
            ctx,
            code,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Cancels old alarms and schedules the next [DAYS_AHEAD] days of enabled prayers. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (c in 0 until DAYS_AHEAD * SLOTS_PER_DAY) am.cancel(pi(ctx, c, 0))

        val p = ctx.getSharedPreferences("adhan", Context.MODE_PRIVATE)
        val mask = p.getInt("mask", if (p.getBoolean("on", false)) 61 else 0)
        if (mask == 0) return
        val lat = p.getString("lat", null)?.toDoubleOrNull() ?: return
        val lng = p.getString("lng", null)?.toDoubleOrNull() ?: return
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return

        val zone = TimeMath.zoneFromPrefs(
            p.getString("zone", null),
            p.getString("tz", null)?.toDoubleOrNull()
        )
        val settings = PrayerCalc.Settings(
            method = PrayerCalc.Method.fromKey(p.getString("method", null)),
            asr = PrayerCalc.AsrMethod.fromKey(p.getString("asr", null)),
            highLatitude = PrayerCalc.HighLatitudeRule.fromKey(p.getString("highLat", null))
        )
        val now = System.currentTimeMillis()
        val today = TimeMath.todayIn(now, zone)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

        for (off in 0 until DAYS_AHEAD) {
            val date = today.plusDays(off.toLong())
            // UTC hours convert to instants independently, so alarms stay correct when
            // the clocks change during the day.
            val t = PrayerCalc.timesUtc(date, lat, lng, settings)
            for (i in intArrayOf(0, 2, 3, 4, 5)) {
                if ((mask and (1 shl i)) == 0) continue
                val ms = TimeMath.utcHoursToMillis(date, t[i])
                if (ms <= now) continue
                val pend = pi(ctx, off * SLOTS_PER_DAY + i, i)
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pend)
                } else {
                    // Without exact-alarm access Android may delay this by several minutes.
                    // The UI shows an inline warning and a shortcut to grant the permission.
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pend)
                }
            }
        }
    }
}
