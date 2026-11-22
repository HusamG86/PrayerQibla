package com.sa.prayerqibla.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sa.prayerqibla.R
import com.sa.prayerqibla.ui.formatCountdown

class CountdownWidget : PrayerWidget() {
    @Composable
    override fun Content(style: WidgetStyle, data: WidgetData, lang: String, context: Context) {
        val heading = if (data.rows.isEmpty()) {
            data.nextName
        } else {
            context.getString(R.string.widget_next_prefix) + data.nextName
        }
        Column(
            modifier = cardModifier(style),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = heading,
                style = TextStyle(
                    color = style.textColor().provider(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = formatCountdown(data.remainingSeconds, lang),
                style = TextStyle(
                    color = style.accentColor().provider(),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = "${data.city} · ${data.dateLabel}",
                style = TextStyle(color = style.textColor().provider(), fontSize = 11.sp)
            )
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = data.progress,
                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                color = style.accentColor().provider(),
                backgroundColor = style.textColor().copy(alpha = 0.18f).provider()
            )
        }
    }
}

class CountdownWidgetReceiver : PrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountdownWidget()
}
