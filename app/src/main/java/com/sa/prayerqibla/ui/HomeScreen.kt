@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sa.prayerqibla.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sa.prayerqibla.TimeMath
import com.sa.prayerqibla.data.PrayerRepository
import com.sa.prayerqibla.data.PrayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class NextInfo(
    val index: Int,
    val atMillis: Long,
    val windowStart: Long,
    val tomorrow: Boolean
)

@Composable
fun TodayScreen(
    vm: PrayerViewModel,
    modifier: Modifier = Modifier,
    wide: Boolean,
    onAlertsChanged: () -> Unit,
    onOpenQibla: () -> Unit
) {
    val strings = LocalStrings.current
    val config by vm.config.collectAsStateWithLifecycle()
    val times by vm.times.collectAsStateWithLifecycle()
    val cityName by vm.cityName.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val zone = remember(config.zone) { TimeMath.zoneFromPrefs(config.zone, null) }
    val date = remember(now / 60_000, config.zone) { TimeMath.todayIn(now, zone) }
    LaunchedEffect(date) { vm.refreshTimes() }

    var nextInfo by remember { mutableStateOf<NextInfo?>(null) }
    LaunchedEffect(times, date, now / 60_000) {
        val today = times
        if (today == null || today.size < 6) return@LaunchedEffect
        val instants = today.map { TimeMath.localHoursToMillis(date, it, zone) }
        var index = instants.indexOfFirst { it > now + 500 }
        var at = if (index >= 0) instants[index] else Long.MAX_VALUE
        var tomorrow = false
        if (index < 0) {
            val nextDate = date.plusDays(1)
            val nextTimes = withContext(Dispatchers.Default) {
                vm.timesFor(nextDate.toEpochDay())
            }
            at = if (nextTimes.size > 0) {
                TimeMath.localHoursToMillis(nextDate, nextTimes[0], zone)
            } else {
                instants[0] + 86_400_000L
            }
            index = 0
            tomorrow = true
        }
        val start = if (!tomorrow && index > 0) instants[index - 1] else instants[5] - 86_400_000L
        nextInfo = NextInfo(index, at, maxOf(start, at - 86_400_000L), tomorrow)
    }

    var monthOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = rememberScrollState()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LocationBar(config.lang, cityName, vm)

        val info = nextInfo
        val remaining = if (info == null) 0L else (info.atMillis - now) / 1000
        val progress = if (info == null) 0f else {
            ((now - info.windowStart).toFloat() / (info.atMillis - info.windowStart))
                .coerceIn(0f, 1f)
        }
        HeroCard(
            label = strings.next,
            name = if (info == null) "—" else strings.names[info.index],
            countdown = formatCountdown(remaining, config.lang),
            progress = progress
        )

        Text(
            formatDates(date, config.lang),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    PrayerList(
                        times = times,
                        config = config,
                        strings = strings,
                        nextIndex = if (info?.tomorrow == true) -1 else info?.index ?: -1,
                        onToggle = { index, enabled ->
                            vm.setPrayerEnabled(index, enabled)
                            onAlertsChanged()
                        }
                    )
                }
                Column(Modifier.weight(1.15f)) {
                    MonthlyTimetable(vm = vm, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            PrayerList(
                times = times,
                config = config,
                strings = strings,
                nextIndex = if (info?.tomorrow == true) -1 else info?.index ?: -1,
                onToggle = { index, enabled ->
                    vm.setPrayerEnabled(index, enabled)
                    onAlertsChanged()
                }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.setAllAlerts(config.mask == 0)
                    onAlertsChanged()
                },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) {
                Text(if (config.mask == 0) strings.enableAll else strings.disableAll)
            }
            FilledTonalButton(
                onClick = { monthOpen = !monthOpen },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp)
            ) {
                Text(if (monthOpen) strings.mHide else strings.mBtn)
            }
        }

        if (!wide) {
            AnimatedVisibility(monthOpen) {
                MonthlyTimetable(vm = vm, modifier = Modifier.fillMaxWidth())
            }
            FilledTonalButton(
                onClick = onOpenQibla,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Text(strings.qt)
            }
        }
    }
}

@Composable
private fun LocationBar(lang: String, cityName: String, vm: PrayerViewModel) {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(cityName.ifBlank { strings.custom }) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PrayerRepository.CITIES.forEach { city ->
                DropdownMenuItem(
                    text = { Text(if (lang == "ar" || lang == "ur") city.ar else city.en) },
                    onClick = {
                        vm.setCity(city)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HeroCard(label: String, name: String, countdown: String, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialShapes.Cookie6Sided.toShape(),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(
                countdown,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFeatureSettings = "tnum"
                ),
                modifier = Modifier.semantics {
                    contentDescription = "$label $name $countdown"
                }
            )
            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PrayerList(
    times: DoubleArray?,
    config: com.sa.prayerqibla.data.AppConfig,
    strings: UiStrings,
    nextIndex: Int,
    onToggle: (Int, Boolean) -> Unit
) {
    val context = LocalContext.current
    SectionCard {
        Text(
            strings.title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        for (index in 0..5) {
            val isNext = index == nextIndex
            val rowModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(
                    if (isNext) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    MaterialTheme.shapes.medium
                )
                .padding(horizontal = 12.dp, vertical = 4.dp)
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                if (isNext) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                MaterialShapes.Pill.toShape()
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    strings.names[index],
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (times != null && times.size > index) {
                        formatClockLabel(
                            context,
                            times[index],
                            config,
                            config.lang,
                            strings.am,
                            strings.pm
                        )
                    } else {
                        "--:--"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                if (index != 1) {
                    val on = config.mask and PrayerRepository.PRAYER_BITS[index] != 0
                    val description = (if (on) strings.prayerOn else strings.prayerOff) + strings.names[index]
                    IconToggleButton(
                        checked = on,
                        onCheckedChange = { onToggle(index, it) },
                        modifier = Modifier.size(48.dp).semantics { contentDescription = description }
                    ) {
                        Icon(
                            imageVector = if (on) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = null
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}
