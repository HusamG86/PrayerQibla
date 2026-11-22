package com.sa.prayerqibla.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import com.sa.prayerqibla.ComposeActivity
import com.sa.prayerqibla.data.PrayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun WidgetStyle.backgroundColor(): Color =
    Color(bgRgb).copy(alpha = WidgetStyleParser.clampAlpha(bgAlpha))

internal fun WidgetStyle.textColor(): Color = Color(textRgb).copy(alpha = 1f)
internal fun WidgetStyle.accentColor(): Color = Color(accentRgb).copy(alpha = 1f)

internal fun Color.provider(): ColorProvider = ColorProvider(this)

internal fun cardModifier(style: WidgetStyle): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .padding(8.dp)
    .background(style.backgroundColor())
    .cornerRadius(16.dp)
    .padding(12.dp)
    .clickable(actionStartActivity<ComposeActivity>())

abstract class PrayerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext
        val lang = withContext(Dispatchers.Default) { PrayerRepository(app).loadConfig().lang }
        val appWidgetId = GlanceAppWidgetManager(app).getAppWidgetId(id)
        val style = WidgetPreferences.load(app, appWidgetId)
        val data = withContext(Dispatchers.Default) { WidgetDataProvider.load(app, lang) }
        val localized = widgetContext(app, lang)
        provideContent { Content(style, data, lang, localized) }
    }

    @Composable
    abstract fun Content(style: WidgetStyle, data: WidgetData, lang: String, context: Context)
}

abstract class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefresh.enable(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefresh.disableIfEmpty(context)
    }
}
