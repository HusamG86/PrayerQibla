package com.sa.prayerqibla.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sa.prayerqibla.data.PrayerRepository
import com.sa.prayerqibla.data.PrayerViewModel
import com.sa.prayerqibla.data.ThemeMode

@Composable
fun SettingsScreen(
    vm: PrayerViewModel,
    actions: AppActions,
    modifier: Modifier = Modifier,
    onShowReliability: () -> Unit
) {
    val strings = LocalStrings.current
    val config by vm.config.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val cityName by vm.cityName.collectAsStateWithLifecycle()
    val extras = remember(config.lang) { extraStrings(config.lang) }
    val scroll = rememberScrollState()

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(strings.settings, style = MaterialTheme.typography.headlineMedium)

        SectionCard {
            Text(strings.city, style = MaterialTheme.typography.titleMedium)
            DropdownChoice(
                label = strings.city,
                selectedLabel = cityName.ifBlank { strings.custom },
                items = PrayerRepository.CITIES.map { city ->
                    city to if (config.lang == "ar" || config.lang == "ur") city.ar else city.en
                },
                onSelect = { vm.setCity(it) }
            )
            FilledTonalButton(
                onClick = { actions.requestLocation() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(strings.gps)
            }
        }

        SectionCard {
            Text(strings.language, style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UiText.LANGUAGE_NAMES.forEach { (code, name) ->
                    FilterChip(
                        selected = config.lang == code,
                        onClick = { vm.setLanguage(code) },
                        label = { Text(name) }
                    )
                }
            }
        }

        SectionCard {
            Text(strings.theme, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = config.theme == ThemeMode.SYSTEM,
                    onClick = { vm.setTheme(ThemeMode.SYSTEM) },
                    label = { Text(extras.themeSystem) }
                )
                FilterChip(
                    selected = config.theme == ThemeMode.LIGHT,
                    onClick = { vm.setTheme(ThemeMode.LIGHT) },
                    label = { Text(extras.themeLight) }
                )
                FilterChip(
                    selected = config.theme == ThemeMode.DARK,
                    onClick = { vm.setTheme(ThemeMode.DARK) },
                    label = { Text(extras.themeDark) }
                )
            }
        }

        SectionCard {
            Text(strings.methodLabel, style = MaterialTheme.typography.titleMedium)
            DropdownChoice(
                label = strings.methodLabel,
                selectedLabel = strings.methods[config.method] ?: config.method,
                items = PrayerRepository.METHODS.map { it to (strings.methods[it] ?: it) },
                onSelect = { vm.update { c -> c.copy(method = it) } }
            )
            DropdownChoice(
                label = strings.asrLabel,
                selectedLabel = strings.asrMethods[config.asr] ?: config.asr,
                items = PrayerRepository.ASR_METHODS.map { it to (strings.asrMethods[it] ?: it) },
                onSelect = { vm.update { c -> c.copy(asr = it) } }
            )
            DropdownChoice(
                label = strings.highLatLabel,
                selectedLabel = strings.highLatRules[config.highLat] ?: config.highLat,
                items = PrayerRepository.HIGH_LAT_RULES.map { it to (strings.highLatRules[it] ?: it) },
                onSelect = { vm.update { c -> c.copy(highLat = it) } }
            )
            DropdownChoice(
                label = strings.timeFormatLabel,
                selectedLabel = when (config.timeFormat) {
                    "12" -> strings.format12
                    "24" -> strings.format24
                    else -> strings.formatAuto
                },
                items = listOf(
                    "auto" to strings.formatAuto,
                    "12" to strings.format12,
                    "24" to strings.format24
                ),
                onSelect = { vm.update { c -> c.copy(timeFormat = it) } }
            )
        }

        SectionCard {
            Text(extras.widgets, style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(
                onClick = { actions.manageWidgets() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(extras.manageWidgets)
            }
        }

        SectionCard {
            Text(strings.permTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                if (permissions.allGranted) strings.permAllowed else strings.permOff,
                style = MaterialTheme.typography.bodyMedium,
                color = if (permissions.allGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Button(
                onClick = onShowReliability,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(if (permissions.allGranted) strings.settings else strings.permFix)
            }
        }

        Text(
            strings.settingsNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun <T> DropdownChoice(
    label: String,
    selectedLabel: String,
    items: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().padding(top = 8.dp)) {
        FilledTonalButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(selectedLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
