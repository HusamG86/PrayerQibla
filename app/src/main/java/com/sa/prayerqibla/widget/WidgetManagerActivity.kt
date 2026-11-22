package com.sa.prayerqibla.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sa.prayerqibla.R
import com.sa.prayerqibla.data.PrayerRepository
import com.sa.prayerqibla.ui.isRtl
import com.sa.prayerqibla.ui.theme.PrayerTheme

class WidgetManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val config = PrayerRepository(this).loadConfig()
        val manager = AppWidgetManager.getInstance(this)
        val placed = listOf(
            CompactWidgetReceiver::class.java,
            CountdownWidgetReceiver::class.java,
            TimetableWidgetReceiver::class.java
        ).flatMap { receiver ->
            manager.getAppWidgetIds(ComponentName(this, receiver))
                .map { id -> id to receiver.simpleName }
        }

        setContent {
            CompositionLocalProvider(
                LocalContext provides widgetContext(this, config.lang),
                LocalLayoutDirection provides if (isRtl(config.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                PrayerTheme(config.theme) {
                    ManagerScreen(placed) { id ->
                        startActivity(
                            Intent(this, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerScreen(placed: List<Pair<Int, String>>, onEdit: (Int) -> Unit) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.widget_manager_title), style = MaterialTheme.typography.headlineMedium)
            if (placed.isEmpty()) {
                Text(stringResource(R.string.widget_manager_empty), style = MaterialTheme.typography.bodyMedium)
            }
            placed.forEach { (id, provider) ->
                val style = WidgetPreferences.load(LocalContext.current, id)
                val label = when (provider) {
                    CompactWidgetReceiver::class.java.simpleName -> R.string.widget_compact_label
                    CountdownWidgetReceiver::class.java.simpleName -> R.string.widget_countdown_label
                    TimetableWidgetReceiver::class.java.simpleName -> R.string.widget_timetable_label
                    else -> null
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label?.let { stringResource(it) } ?: provider, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(style.bgRgb, style.textRgb, style.accentRgb).forEach { rgb ->
                                Box(
                                    Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color(rgb))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            }
                        }
                    }
                    Button(onClick = { onEdit(id) }) {
                        Text(stringResource(R.string.widget_manager_edit))
                    }
                }
            }
        }
    }
}
