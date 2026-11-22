package com.sa.prayerqibla

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sa.prayerqibla.widget.WidgetRefresh

/**
 * Rebuilds alarms after boot, an app update, a clock/time-zone change, or a date change.
 * The manifest declares all of these actions.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        // Only react to the protected broadcasts this receiver is registered for.
        if (intent.action !in ACTIONS) return
        AdhanScheduler.schedule(ctx)
        WidgetRefresh.rescheduleIfNeeded(ctx)
        WidgetRefresh.refreshAll(ctx)
    }
}
