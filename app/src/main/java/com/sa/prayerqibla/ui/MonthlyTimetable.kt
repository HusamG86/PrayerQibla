package com.sa.prayerqibla.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sa.prayerqibla.TimeMath
import com.sa.prayerqibla.data.PrayerViewModel
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun MonthlyTimetable(vm: PrayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val config by vm.config.collectAsStateWithLifecycle()
    val rows by vm.month.collectAsStateWithLifecycle()
    val locale = remember(config.lang) { uiLocale(config.lang) }
    val today = remember(System.currentTimeMillis() / 3_600_000, config.zone) {
        TimeMath.todayIn(System.currentTimeMillis(), TimeMath.zoneFromPrefs(config.zone, null))
    }
    var yearMonth by remember {
        mutableStateOf(YearMonth.of(today.year, today.monthValue))
    }

    LaunchedEffect(yearMonth) { vm.loadMonth(yearMonth.year, yearMonth.monthValue) }

    val title = remember(yearMonth, config.lang) {
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(yearMonth)
    }
    val rowScroll = rememberScrollState()
    val cellWidth = 78.dp
    val dayWidth = 58.dp
    val tableWidth = dayWidth + cellWidth * 6

    SectionCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { yearMonth = yearMonth.minusMonths(1) },
                modifier = Modifier.width(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, strings.monthPrev)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            IconButton(
                onClick = { yearMonth = yearMonth.plusMonths(1) },
                modifier = Modifier.width(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, strings.monthNext)
            }
        }

        Column(Modifier.fillMaxWidth().horizontalScroll(rowScroll)) {
            Row(Modifier.width(tableWidth)) {
                Text(
                    strings.gr,
                    modifier = Modifier.width(dayWidth),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                strings.names.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.width(cellWidth),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            rows.forEachIndexed { index, times ->
                val isToday = yearMonth.year == today.year &&
                    yearMonth.monthValue == today.monthValue &&
                    index + 1 == today.dayOfMonth
                val rowColor = if (isToday) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                Row(
                    Modifier
                        .width(tableWidth)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        digits(index + 1, config.lang, 2),
                        modifier = Modifier.width(dayWidth),
                        style = if (isToday) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    for (prayer in 0..5) {
                        Text(
                            if (times.size > prayer) {
                                formatClockLabel(
                                    context,
                                    times[prayer], config, config.lang,
                                    strings.am, strings.pm, force24 = true
                                )
                            } else {
                                "--:--"
                            },
                            modifier = Modifier.width(cellWidth),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (isToday) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
