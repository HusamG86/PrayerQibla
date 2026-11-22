package com.sa.prayerqibla.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sa.prayerqibla.data.PrayerViewModel
import com.sa.prayerqibla.ui.theme.PrayerTheme
import kotlinx.coroutines.CancellationException

enum class Screen { TODAY, QIBLA, SETTINGS }

/** Actions that need the hosting Activity (permissions and system settings). */
data class AppActions(
    val requestLocation: () -> Unit,
    val requestNotifications: () -> Unit,
    val openNotificationSettings: () -> Unit,
    val openExactAlarmSettings: () -> Unit,
    val openBatterySettings: () -> Unit,
    val manageWidgets: () -> Unit
)

@Composable
fun PrayerQiblaApp(
    vm: PrayerViewModel,
    compass: CompassController,
    actions: AppActions
) {
    val config by vm.config.collectAsStateWithLifecycle()
    val strings = remember(config.lang) { UiText.forLanguage(config.lang) }
    // Capture the real Activity before LocalContext is replaced by the locale wrapper.
    val activity = rememberActivity()
    val localizedContext = rememberLocalizedContext(config.lang)
    val layoutDirection = if (isRtl(config.lang)) LayoutDirection.Rtl else LayoutDirection.Ltr

    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalContext provides localizedContext,
        LocalStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        PrayerTheme(config.theme) {
            AppContent(vm, compass, actions, activity)
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun AppContent(
    vm: PrayerViewModel,
    compass: CompassController,
    actions: AppActions,
    activity: Activity
) {
    val strings = LocalStrings.current
    val config by vm.config.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val locationMessage by vm.locationMessage.collectAsStateWithLifecycle()
    val widthClass = calculateWindowSizeClass(activity).widthSizeClass

    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
    var showReliability by rememberSaveable { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val snackbarHostState = remember { SnackbarHostState() }

    PredictiveBackHandler(enabled = screen != Screen.TODAY && !showReliability) { events ->
        try {
            events.collect { event -> backProgress = event.progress }
            screen = Screen.TODAY
            backProgress = 0f
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }
    BackHandler(enabled = showReliability) { showReliability = false }

    val scale by animateFloatAsState(
        targetValue = 1f - (0.04f * backProgress),
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.9f),
        label = "predictive-back"
    )

    val onAlertsChanged = {
        vm.refreshPermissions()
        if (vm.config.value.mask != 0 && !vm.permissions.value.allGranted) {
            showReliability = true
        }
    }

    locationMessage?.let { code ->
        val message = when (code) {
            "denied" -> strings.gpsDenied
            else -> strings.gpsErr
        }
        LaunchedEffect(code) {
            snackbarHostState.showSnackbar(message)
            vm.clearLocationMessage()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.systemBars,
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            val content: @Composable (Modifier) -> Unit = { modifier ->
                when (screen) {
                    Screen.TODAY -> TodayScreen(
                        vm = vm,
                        modifier = modifier,
                        wide = widthClass != WindowWidthSizeClass.Compact,
                        onAlertsChanged = onAlertsChanged,
                        onOpenQibla = { screen = Screen.QIBLA }
                    )

                    Screen.QIBLA -> QiblaScreen(
                        vm = vm,
                        compass = compass,
                        modifier = modifier
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        vm = vm,
                        actions = actions,
                        modifier = modifier,
                        onShowReliability = { showReliability = true }
                    )
                }
            }

            when (widthClass) {
                WindowWidthSizeClass.Compact -> {
                    Column(Modifier.padding(padding)) {
                        Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
                        AppNavigationBar(screen, config.lang) { screen = it }
                    }
                }

                else -> {
                    Row(Modifier.padding(padding)) {
                        AppNavigationRail(screen, config.lang) { screen = it }
                        Box(Modifier.weight(1f)) { content(Modifier.fillMaxSize()) }
                    }
                }
            }
        }

        if (showReliability) {
            ReliabilitySheet(
                permissions = permissions,
                strings = strings,
                actions = actions,
                onDismiss = { showReliability = false }
            )
        }
    }
}

@Composable
private fun AppNavigationBar(selected: Screen, language: String, onSelect: (Screen) -> Unit) {
    val strings = LocalStrings.current
    val extras = remember(language) { extraStrings(language) }
    NavigationBar {
        NavigationBarItem(
            selected = selected == Screen.TODAY,
            onClick = { onSelect(Screen.TODAY) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(extras.tabToday) }
        )
        NavigationBarItem(
            selected = selected == Screen.QIBLA,
            onClick = { onSelect(Screen.QIBLA) },
            icon = { Icon(Icons.Filled.Explore, contentDescription = null) },
            label = { Text(extras.tabQibla) }
        )
        NavigationBarItem(
            selected = selected == Screen.SETTINGS,
            onClick = { onSelect(Screen.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(strings.settings) }
        )
    }
}

@Composable
private fun AppNavigationRail(selected: Screen, language: String, onSelect: (Screen) -> Unit) {
    val strings = LocalStrings.current
    val extras = remember(language) { extraStrings(language) }
    NavigationRail {
        NavigationRailItem(
            selected = selected == Screen.TODAY,
            onClick = { onSelect(Screen.TODAY) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(extras.tabToday) }
        )
        NavigationRailItem(
            selected = selected == Screen.QIBLA,
            onClick = { onSelect(Screen.QIBLA) },
            icon = { Icon(Icons.Filled.Explore, contentDescription = null) },
            label = { Text(extras.tabQibla) }
        )
        NavigationRailItem(
            selected = selected == Screen.SETTINGS,
            onClick = { onSelect(Screen.SETTINGS) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text(strings.settings) }
        )
    }
}
