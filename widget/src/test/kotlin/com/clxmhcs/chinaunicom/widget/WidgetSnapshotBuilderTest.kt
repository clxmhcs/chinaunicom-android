package com.clxmhcs.chinaunicom.widget

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotBuilderTest {
    @Test
    fun singleDefaultSlotsMatchSourceClassification() {
        val account = UnicomAccount(
            displayName = "fixture",
            mobile = "10000000000",
            packages = listOf(
                FlowPackage(id = "domestic", originalName = "国内通用流量", totalMB = 10 * 1024.0, usedMB = 4 * 1024.0, remainingMB = 6 * 1024.0, detectedQuotaType = QuotaType.LIMITED),
                FlowPackage(id = "province", originalName = "省内流量", totalMB = 3 * 1024.0, usedMB = 1 * 1024.0, remainingMB = 2 * 1024.0, detectedQuotaType = QuotaType.LIMITED),
                FlowPackage(id = "cell", originalName = "小区流量", totalMB = 2 * 1024.0, usedMB = 0.5 * 1024.0, remainingMB = 1.5 * 1024.0, detectedQuotaType = QuotaType.LIMITED),
                FlowPackage(id = "campus", originalName = "校区流量", totalMB = 1 * 1024.0, usedMB = 0.25 * 1024.0, remainingMB = 0.75 * 1024.0, detectedQuotaType = QuotaType.LIMITED),
            ),
            voicePackages = listOf(
                VoicePackage(id = "voice", originalName = "国内语音", totalMinutes = 300.0, usedMinutes = 100.0, remainingMinutes = 200.0),
                VoicePackage(id = "family", originalName = "一家亲语音", totalMinutes = 100.0, usedMinutes = 20.0, remainingMinutes = 80.0),
            ),
        )

        val items = WidgetSnapshotBuilder.makeSingleItems(account, WidgetDisplayConfiguration())
        assertEquals(6, items.size)
        assertEquals("国内流量", items[0].titleTop)
        assertEquals(6.0, items[0].remaining, 0.0001)
        assertEquals(2.0, items[1].remaining, 0.0001)
        assertEquals(1.5, items[2].remaining, 0.0001)
        assertEquals(0.75, items[3].remaining, 0.0001)
        assertEquals(200.0, items[4].remaining, 0.0001)
        assertEquals(80.0, items[5].remaining, 0.0001)
    }

    @Test
    fun dualHiddenSlotStaysEmptyAndIdentityStable() {
        val account = UnicomAccount(displayName = "fixture", mobile = "10000000000")
        val items = WidgetSnapshotBuilder.makeDualItems(
            account,
            WidgetDualSlotConfiguration.defaultSlots(WidgetDualSide.LEFT),
        )
        assertEquals(6, items.size)
        assertEquals("left-slot-6", items.last().id)
        assertNull(items.last().remaining)
    }

    @Test
    fun mobileMaskingMatchesSourceRule() {
        assertEquals("100****0000", WidgetSnapshotBuilder.maskedMobile("10000000000"))
        assertEquals("0000", WidgetSnapshotBuilder.mobileSuffix("10000000000"))
        assertTrue(WidgetSnapshotBuilder.maskedMobile("123").contains("123"))
    }
}
