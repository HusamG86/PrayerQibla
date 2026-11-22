package com.sa.prayerqibla.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStyleTest {
    @Test fun parseRgb_acceptsSixDigitHex() {
        assertEquals(0x0C1511, WidgetStyleParser.parseRgb("#0C1511"))
    }

    @Test fun parseRgb_normalizesEightDigitHexToRgb() {
        assertEquals(0x0C1511, WidgetStyleParser.parseRgb("#800C1511"))
    }

    @Test fun parseRgb_acceptsHexWithoutHash() {
        assertEquals(0xF3F6F1, WidgetStyleParser.parseRgb("F3F6F1"))
    }

    @Test fun parseRgb_rejectsMalformedInput() {
        assertNull(WidgetStyleParser.parseRgb("#12345"))
        assertNull(WidgetStyleParser.parseRgb("not-a-color"))
        assertNull(WidgetStyleParser.parseRgb(""))
    }

    @Test fun clampAlpha_boundsToAllowedRange() {
        assertEquals(WidgetStyleParser.MIN_BG_ALPHA, WidgetStyleParser.clampAlpha(0.01f))
        assertEquals(1f, WidgetStyleParser.clampAlpha(4f))
        assertEquals(0.5f, WidgetStyleParser.clampAlpha(0.5f))
    }

    @Test fun hex_formatsRgbWithLeadingZeros() {
        assertEquals("#0C1511", hex(0x0C1511))
        assertEquals("#00000F", hex(0x00000F))
        assertEquals("#FFFFFF", hex(0xFFFFFFFF.toInt()))
    }

    @Test fun defaults_areFullyOpaque() {
        assertEquals(1f, WidgetStyleParser.defaults().bgAlpha)
    }

    @Test fun deserialize_roundTripsAndFallsBack() {
        val style = WidgetStyle(0x112233, 0xAABBCC, 0xDDEEFF, 0.4f)
        assertEquals(style, WidgetStyleParser.deserialize(WidgetStyleParser.serialize(style)))
        assertEquals(WidgetStyleParser.defaults(), WidgetStyleParser.deserialize(null))
        assertEquals(WidgetStyleParser.defaults(), WidgetStyleParser.deserialize("garbage"))
    }
}
