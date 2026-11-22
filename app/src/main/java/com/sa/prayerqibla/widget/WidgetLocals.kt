package com.sa.prayerqibla.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Context whose resources use the in-app language, not the system locale. */
fun widgetContext(context: Context, lang: String): Context {
    val locale = runCatching { Locale.forLanguageTag(lang) }.getOrDefault(Locale.ENGLISH)
    val configuration = Configuration(context.resources.configuration).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            setLocale(locale)
        }
        setLayoutDirection(locale)
    }
    return context.createConfigurationContext(configuration)
}
