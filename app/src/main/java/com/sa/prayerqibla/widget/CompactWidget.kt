package com.sa.prayerqibla.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sa.prayerqibla.R

class CompactWidget : PrayerWidget() {
    @Composable
    override fun Content(style: WidgetStyle, data: WidgetData, lang: String, context: Context) {
        val heading = if (data.rows.isEmpty()) {
            data.nextName
        } else {
            context.getString(R.string.widget_next_prefix) + data.nextName
        }
        Column(
            modifier = cardModifier(style),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = heading,
                style = TextStyle(
                    color = style.accentColor().provider(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = data.nextTime,
                style = TextStyle(
                    color = style.accentColor().provider(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = data.city,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = style.textColor().provider(), fontSize = 12.sp)
            )
            Text(
                text = data.dateLabel,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(color = style.textColor().provider(), fontSize = 11.sp)
            )
        }
    }
}

class CompactWidgetReceiver : PrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
}
