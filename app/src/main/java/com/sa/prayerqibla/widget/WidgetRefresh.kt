package com.sa.prayerqibla.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetRefresh {
    private const val REQUEST_CODE = 41
    private const val INTERVAL_MS = 60_000L
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    fun shouldSchedule(activeKinds: Set<String>): Boolean = activeKinds.isNotEmpty()

    fun enable(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            pendingIntent(app)
        )
    }

    fun disableIfEmpty(context: Context) {
        val app = context.applicationContext
        if (!shouldSchedule(activeKinds(app))) cancel(app)
    }

    /** Used after boot/time changes: never leave an alarm running with no widgets placed. */
    fun rescheduleIfNeeded(context: Context) {
        val app = context.applicationContext
        if (shouldSchedule(activeKinds(app))) enable(app) else cancel(app)
    }

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            CompactWidget().updateAll(app)
            CountdownWidget().updateAll(app)
            TimetableWidget().updateAll(app)
        }
    }

    private fun activeKinds(context: Context): Set<String> {
        val manager = AppWidgetManager.getInstance(context)
        return listOf(
            CompactWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
            TimetableWidgetReceiver::class.java
        ).filter { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
            .map { it.simpleName }
            .toSet()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarm.cancel(pendingIntent(context))
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetRefresh.refreshAll(context)
    }
}
