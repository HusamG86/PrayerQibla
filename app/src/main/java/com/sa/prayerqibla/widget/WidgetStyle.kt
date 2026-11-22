package com.sa.prayerqibla.widget

import java.util.Locale

/** Per-widget colors. RGB only — background alpha lives in [bgAlpha]. */
data class WidgetStyle(
    val bgRgb: Int,
    val textRgb: Int,
    val accentRgb: Int,
    val bgAlpha: Float
)

object WidgetStyleParser {
    const val DEFAULT_BG_RGB = 0x0C1511
    const val DEFAULT_TEXT_RGB = 0xF3F6F1
    const val DEFAULT_ACCENT_RGB = 0xB4D29A
    const val DEFAULT_BG_ALPHA = 1f
    const val MIN_BG_ALPHA = 0.15f

    /** Accepts #RRGGBB, RRGGBB, #AARRGGBB or AARRGGBB; returns 0xRRGGBB or null. */
    fun parseRgb(input: String): Int? {
        val hex = input.removePrefix("#")
        if (hex.length !in setOf(6, 8) || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
            return null
        }
        val rgb = if (hex.length == 8) hex.substring(2) else hex
        return rgb.toInt(16)
    }

    fun clampAlpha(alpha: Float): Float = when {
        alpha.isNaN() -> DEFAULT_BG_ALPHA
        else -> alpha.coerceIn(MIN_BG_ALPHA, DEFAULT_BG_ALPHA)
    }

    fun serialize(style: WidgetStyle): String = listOf(
        String.format(Locale.US, "#%06X", style.bgRgb and 0xFFFFFF),
        String.format(Locale.US, "#%06X", style.textRgb and 0xFFFFFF),
        String.format(Locale.US, "#%06X", style.accentRgb and 0xFFFFFF),
        style.bgAlpha.toString()
    ).joinToString("|")

    fun deserialize(raw: String?): WidgetStyle {
        if (raw == null) return defaults()
        val parts = raw.split('|')
        if (parts.size != 4) return defaults()
        val bg = parseRgb(parts[0]) ?: return defaults()
        val text = parseRgb(parts[1]) ?: return defaults()
        val accent = parseRgb(parts[2]) ?: return defaults()
        val alpha = parts[3].toFloatOrNull() ?: return defaults()
        return WidgetStyle(bg, text, accent, clampAlpha(alpha))
    }

    fun defaults(): WidgetStyle = WidgetStyle(
        DEFAULT_BG_RGB, DEFAULT_TEXT_RGB, DEFAULT_ACCENT_RGB, DEFAULT_BG_ALPHA
    )
}
