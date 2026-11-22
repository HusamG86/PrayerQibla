package com.sa.prayerqibla.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sa.prayerqibla.R
import com.sa.prayerqibla.data.PrayerRepository
import com.sa.prayerqibla.ui.isRtl
import com.sa.prayerqibla.ui.theme.PrayerTheme
import java.util.Locale

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val id = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val config = PrayerRepository(this).loadConfig()
        val provider = AppWidgetManager.getInstance(this)
            .getAppWidgetInfo(id)?.provider?.className?.substringAfterLast('.')
        val kindLabel = when (provider) {
            CompactWidgetReceiver::class.java.simpleName -> R.string.widget_compact_label
            CountdownWidgetReceiver::class.java.simpleName -> R.string.widget_countdown_label
            TimetableWidgetReceiver::class.java.simpleName -> R.string.widget_timetable_label
            else -> null
        }
        val initial = WidgetPreferences.load(this, id)

        setContent {
            CompositionLocalProvider(
                LocalContext provides widgetContext(this, config.lang),
                LocalLayoutDirection provides if (isRtl(config.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                PrayerTheme(config.theme) {
                    ConfigScreen(
                        initial = initial,
                        kindLabel = kindLabel?.let { stringResource(it) }.orEmpty(),
                        onCancel = ::cancel,
                        onSave = { style ->
                            WidgetPreferences.save(this, id, style)
                            WidgetRefresh.refreshAll(this)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                            )
                            finish()
                        }
                    )
                }
            }
        }

        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) cancel()
    }

    private fun cancel() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

private enum class PickerTarget { BACKGROUND, TEXT, ACCENT }

@Composable
private fun ConfigScreen(
    initial: WidgetStyle,
    kindLabel: String,
    onCancel: () -> Unit,
    onSave: (WidgetStyle) -> Unit
) {
    var bg by remember { mutableStateOf(hex(initial.bgRgb)) }
    var text by remember { mutableStateOf(hex(initial.textRgb)) }
    var accent by remember { mutableStateOf(hex(initial.accentRgb)) }
    var alpha by remember { mutableFloatStateOf(initial.bgAlpha) }
    var invalid by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineMedium)
            if (kindLabel.isNotEmpty()) {
                Text(kindLabel, style = MaterialTheme.typography.bodyMedium)
            }
            ColorField(
                stringResource(R.string.widget_config_bg), bg,
                WidgetStyleParser.parseRgb(bg)?.let { Color(it).copy(alpha = alpha) } ?: Color.Gray,
                { pickerTarget = PickerTarget.BACKGROUND }
            ) { bg = it; invalid = false }
            ColorField(
                stringResource(R.string.widget_config_text), text,
                WidgetStyleParser.parseRgb(text)?.let { Color(it).copy(alpha = 1f) } ?: Color.Gray,
                { pickerTarget = PickerTarget.TEXT }
            ) { text = it; invalid = false }
            ColorField(
                stringResource(R.string.widget_config_accent), accent,
                WidgetStyleParser.parseRgb(accent)?.let { Color(it).copy(alpha = 1f) } ?: Color.Gray,
                { pickerTarget = PickerTarget.ACCENT }
            ) { accent = it; invalid = false }
            Text(stringResource(R.string.widget_config_opacity), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = alpha,
                onValueChange = { alpha = WidgetStyleParser.clampAlpha(it) },
                valueRange = WidgetStyleParser.MIN_BG_ALPHA..1f
            )
            if (invalid) {
                Text(
                    stringResource(R.string.widget_config_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val bgRgb = WidgetStyleParser.parseRgb(bg)
                        val textRgb = WidgetStyleParser.parseRgb(text)
                        val accentRgb = WidgetStyleParser.parseRgb(accent)
                        invalid = bgRgb == null || textRgb == null || accentRgb == null
                        if (bgRgb != null && textRgb != null && accentRgb != null) {
                            onSave(WidgetStyle(bgRgb, textRgb, accentRgb, alpha))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.widget_config_save))
                }
                Button(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
            pickerTarget?.let { target ->
                ColorPickerDialog(
                    initialRgb = when (target) {
                        PickerTarget.BACKGROUND -> WidgetStyleParser.parseRgb(bg)
                            ?: WidgetStyleParser.DEFAULT_BG_RGB
                        PickerTarget.TEXT -> WidgetStyleParser.parseRgb(text)
                            ?: WidgetStyleParser.DEFAULT_TEXT_RGB
                        PickerTarget.ACCENT -> WidgetStyleParser.parseRgb(accent)
                            ?: WidgetStyleParser.DEFAULT_ACCENT_RGB
                    },
                    onRgb = { rgb ->
                        when (target) {
                            PickerTarget.BACKGROUND -> bg = hex(rgb)
                            PickerTarget.TEXT -> text = hex(rgb)
                            PickerTarget.ACCENT -> accent = hex(rgb)
                        }
                        invalid = false
                        pickerTarget = null
                    },
                    onDismiss = { pickerTarget = null }
                )
            }
        }
    }
}

@Composable
private fun ColorField(
    label: String,
    value: String,
    swatch: Color,
    onPick: () -> Unit,
    onChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(swatch)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .semantics { contentDescription = label }
        )
    }
}

internal fun hex(rgb: Int): String = String.format(Locale.US, "#%06X", rgb and 0xFFFFFF)
