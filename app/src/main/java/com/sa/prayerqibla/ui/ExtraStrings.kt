package com.sa.prayerqibla.ui

/** Labels introduced by the Compose UI that were not present in the original WebView. */
data class ExtraStrings(
    val tabToday: String,
    val tabQibla: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val widgets: String,
    val manageWidgets: String
)

private val EXTRA_EN = ExtraStrings("Today", "Qibla", "System", "Light", "Dark", "Widgets", "Manage widgets")
private val EXTRA_AR = ExtraStrings("اليوم", "القبلة", "النظام", "فاتح", "داكن", "واجهات", "إدارة الواجهات")
private val EXTRA_UR = ExtraStrings("آج", "قبلہ", "سسٹم", "روشن", "تاریک", "وِجٹس", "وِجٹس کا انتظام")
private val EXTRA_FR = ExtraStrings("Aujourd’hui", "Qibla", "Système", "Clair", "Sombre", "Widgets", "Gérer les widgets")
private val EXTRA_ID = ExtraStrings("Hari ini", "Qibla", "Sistem", "Terang", "Gelap", "Widget", "Kelola widget")
private val EXTRA_TR = ExtraStrings("Bugün", "Kıble", "Sistem", "Açık", "Koyu", "Widget’lar", "Widget’ları yönet")

fun extraStrings(lang: String): ExtraStrings = when (lang) {
    "ar" -> EXTRA_AR
    "ur" -> EXTRA_UR
    "fr" -> EXTRA_FR
    "id" -> EXTRA_ID
    "tr" -> EXTRA_TR
    else -> EXTRA_EN
}
