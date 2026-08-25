package com.clxmhcs.chinaunicom.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardFormattingTest {
    @Test
    fun minuteFormattingKeepsDashboardSemanticsStable() {
        assertEquals("--", formatMinutes(null))
        assertEquals("--", formatMinutes(Double.NaN))
        assertEquals("0 分钟", formatMinutes(-1.0))
        assertEquals("60 分钟", formatMinutes(60.0))
        assertEquals("12.50 分钟", formatMinutes(12.5))
    }
}
