package com.sa.prayerqibla.widget

import android.content.Context

object WidgetPreferences {
    private const val PREFS_NAME = "prayer_widgets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context, appWidgetId: Int): WidgetStyle =
        WidgetStyleParser.deserialize(prefs(context).getString("style_$appWidgetId", null))

    fun save(context: Context, appWidgetId: Int, style: WidgetStyle) {
        prefs(context).edit()
            .putString("style_$appWidgetId", WidgetStyleParser.serialize(style))
            .apply()
    }
}
