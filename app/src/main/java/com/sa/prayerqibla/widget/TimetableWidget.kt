package com.sa.prayerqibla.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class TimetableWidget : PrayerWidget() {
    @Composable
    override fun Content(style: WidgetStyle, data: WidgetData, lang: String, context: Context) {
        Column(modifier = cardModifier(style)) {
            Text(
                text = data.city,
                style = TextStyle(
                    color = style.accentColor().provider(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = data.dateLabel,
                style = TextStyle(color = style.textColor().provider(), fontSize = 11.sp)
            )
            Spacer(GlanceModifier.height(8.dp))
            if (data.rows.isEmpty()) {
                Text(
                    text = data.nextName,
                    style = TextStyle(color = style.textColor().provider(), fontSize = 13.sp)
                )
            } else {
                data.rows.forEach { row ->
                    val color = if (row.isNext) style.accentColor() else style.textColor()
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = row.name,
                            modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(
                                color = color.provider(),
                                fontSize = 13.sp,
                                fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                        Text(
                            text = row.time,
                            style = TextStyle(
                                color = color.provider(),
                                fontSize = 13.sp,
                                fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal
                            )
                        )
                    }
                    Spacer(GlanceModifier.height(2.dp))
                }
            }
        }
    }
}

class TimetableWidgetReceiver : PrayerWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}
