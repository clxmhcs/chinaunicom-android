package com.clxmhcs.chinaunicom.core.model

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFeatureModelsTest {
    @Test
    fun singleWidgetDefaultsMatchSourceRefreshTimes() {
        val configuration = WidgetDisplayConfiguration().normalized()

        assertEquals(listOf(480, 660, 840, 1020), configuration.automaticRefreshMinutes)
        assertEquals(6, configuration.slots.size)
        assertTrue(configuration.slots.all { it.id.isNotBlank() })
    }

    @Test
    fun dualWidgetNormalizesSixSlotsAndPreventsSameAccountOnBothSides() {
        val accountID = UUID.randomUUID()
        val configuration = WidgetDualDisplayConfiguration(
            leftAccountID = accountID,
            rightAccountID = accountID,
            leftSlots = emptyList(),
            rightSlots = emptyList(),
        ).normalized()

        assertEquals(accountID, configuration.leftAccountID)
        assertNull(configuration.rightAccountID)
        assertEquals(WidgetDualSlotConfiguration.SLOT_COUNT, configuration.leftSlots.size)
        assertEquals(WidgetDualSlotConfiguration.SLOT_COUNT, configuration.rightSlots.size)
    }

    @Test
    fun integralDualSlotDropsResourceBindingsAndUsesFixedTitle() {
        val slot = WidgetDualSlotConfiguration(
            id = "slot",
            title = "custom",
            kind = WidgetDualSlotKind.INTEGRAL,
            flowSummaryGroupID = "flow",
            voiceSummaryGroupID = "voice",
            packageIDs = listOf("a", "b"),
        ).normalized("fallback")

        assertEquals("可用积分", slot.title)
        assertNull(slot.flowSummaryGroupID)
        assertNull(slot.voiceSummaryGroupID)
        assertTrue(slot.packageIDs.isEmpty())
    }
}
