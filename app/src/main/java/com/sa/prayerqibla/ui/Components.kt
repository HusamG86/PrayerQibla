package com.sa.prayerqibla.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sa.prayerqibla.data.PermissionState

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilitySheet(
    permissions: PermissionState,
    strings: UiStrings,
    actions: AppActions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(strings.permTitle, style = MaterialTheme.typography.headlineSmall)
            PermissionRow(
                label = strings.permNotif,
                granted = permissions.notifications,
                strings = strings,
                onFix = { actions.requestNotifications() }
            )
            PermissionRow(
                label = strings.permExact,
                granted = permissions.exactAlarms,
                strings = strings,
                onFix = { actions.openExactAlarmSettings() }
            )
            PermissionRow(
                label = strings.permBattery,
                granted = permissions.battery,
                strings = strings,
                onFix = { actions.openBatterySettings() }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    strings: UiStrings,
    onFix: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) strings.permAllowed else strings.permOff,
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        if (!granted) {
            Button(
                onClick = onFix,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(strings.permFix)
            }
        }
    }
}

/** Small helper used by the timetable for centered labels. */
@Composable
internal fun CenteredLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge
    )
}
