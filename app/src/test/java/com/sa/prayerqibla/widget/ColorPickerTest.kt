package com.sa.prayerqibla.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorPickerTest {
    @Test fun rgbToHsv_primaryColors() {
        assertHsv(0xFF0000, 0f, 1f, 1f)
        assertHsv(0x00FF00, 120f, 1f, 1f)
        assertHsv(0x0000FF, 240f, 1f, 1f)
    }

    @Test fun rgbToHsv_achromaticColorsHaveZeroSaturation() {
        assertEquals(0f, rgbToHsv(0x000000)[1], 0f)
        assertEquals(0f, rgbToHsv(0x000000)[2], 0f)
        assertEquals(0f, rgbToHsv(0xFFFFFF)[1], 0f)
        assertEquals(1f, rgbToHsv(0xFFFFFF)[2], 0f)
        assertEquals(0f, rgbToHsv(0x808080)[1], 0f)
    }

    @Test fun hsvToRgb_primaryColors() {
        assertEquals(0xFF0000, hsvToRgb(0f, 1f, 1f))
        assertEquals(0x00FF00, hsvToRgb(120f, 1f, 1f))
        assertEquals(0x0000FF, hsvToRgb(240f, 1f, 1f))
    }

    @Test fun hsvToRgb_normalizesOutOfRangeInputs() {
        assertEquals(0xFF0000, hsvToRgb(360f, 1f, 1f))
        assertEquals(0xFF0000, hsvToRgb(-360f, 1f, 1f))
        assertEquals(0x800000, hsvToRgb(0f, 2f, 0.5f))
        assertEquals(0x808080, hsvToRgb(0f, 0f, 0.5f))
    }

    @Test fun roundTrip_preservesDefaultsAndMidTone() {
        for (rgb in listOf(0x000000, 0xFFFFFF, 0x0C1511, 0xF3F6F1, 0xB4D29A, 0x3A7BD5)) {
            val hsv = rgbToHsv(rgb)
            assertEquals(rgb, hsvToRgb(hsv[0], hsv[1], hsv[2]))
        }
    }

    private fun assertHsv(rgb: Int, hue: Float, saturation: Float, value: Float) {
        val hsv = rgbToHsv(rgb)
        assertEquals(hue, hsv[0], 0.5f)
        assertEquals(saturation, hsv[1], 0.01f)
        assertEquals(value, hsv[2], 0.01f)
    }
}
