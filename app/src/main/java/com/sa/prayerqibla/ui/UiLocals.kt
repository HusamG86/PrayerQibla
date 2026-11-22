package com.sa.prayerqibla.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Localized strings chosen in Settings, independent of the system locale. */
val LocalStrings = compositionLocalOf { UiText.forLanguage("en") }

/** A context whose resources use the in-app language, so date and number formatting follow it. */
@Composable
fun rememberLocalizedContext(language: String): Context {
    val base = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    return remember(base, systemConfiguration, language) {
        val locale = runCatching { Locale.forLanguageTag(language) }.getOrDefault(Locale.ENGLISH)
        val configuration = android.content.res.Configuration(systemConfiguration).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            } else {
                @Suppress("DEPRECATION")
                setLocale(locale)
            }
            setLayoutDirection(locale)
        }
        base.createConfigurationContext(configuration)
    }
}

/** Finds the hosting Activity through configuration wrappers created for in-app locale. */
@Composable
fun rememberActivity(): Activity {
    var context = LocalContext.current
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    error("PrayerQiblaApp must be hosted in an Activity")
}

internal fun isRtl(language: String): Boolean = language == "ar" || language == "ur"
