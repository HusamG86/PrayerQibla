package com.sa.prayerqibla.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshTest {
    @Test fun shouldSchedule_falseWhenNoWidgetsPlaced() {
        assertFalse(WidgetRefresh.shouldSchedule(emptySet()))
    }

    @Test fun shouldSchedule_trueWhenAnyWidgetPlaced() {
        assertTrue(WidgetRefresh.shouldSchedule(setOf("CompactWidgetReceiver")))
    }

    @Test fun shouldSchedule_falseAfterLastWidgetRemoved() {
        assertFalse(WidgetRefresh.shouldSchedule(emptySet()))
    }
}
