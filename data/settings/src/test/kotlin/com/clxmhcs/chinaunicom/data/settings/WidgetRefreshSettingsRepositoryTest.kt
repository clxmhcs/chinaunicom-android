package com.clxmhcs.chinaunicom.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshSettingsRepositoryTest {
    @Test
    fun widgetRefreshUsesSameSchema3DocumentAndPreservesExistingDomains() {
        val storage = InMemoryRefreshStorage(
            """{"schemaVersion":3,"quota":{"automaticRefreshEnabled":true},"videoRing":{"entryMode":"everyEntry","cacheValidityMinutes":60}}""",
        )
        val repository = UnifiedWidgetRefreshSettingsRepository(storage)

        val result = repository.saveWidgetRefreshPolicy(
            WidgetRefreshPolicy(
                automaticRefreshEnabled = false,
                scheduledMinutes = listOf(1020, 480, 480, -1, 2000),
                compensationMinutes = 9,
                failureRetrySeconds = 45,
            ),
        )

        assertTrue(result.persisted)
        assertTrue(result.changed)
        assertFalse(result.policy.automaticRefreshEnabled)
        assertEquals(listOf(480, 1020), result.policy.scheduledMinutes)
        val raw = storage.read().orEmpty()
        assertTrue(raw.contains("\"schemaVersion\":3"))
        assertTrue(raw.contains("\"quota\""))
        assertTrue(raw.contains("\"videoRing\""))
        assertTrue(raw.contains("\"widget\""))
    }

    @Test
    fun invalidTimesFallBackToSourceDefaults() {
        val storage = InMemoryRefreshStorage(null)
        val repository = UnifiedWidgetRefreshSettingsRepository(storage)

        val result = repository.saveWidgetRefreshPolicy(
            WidgetRefreshPolicy(scheduledMinutes = listOf(-1, 1440, 2000)),
        )

        assertEquals(listOf(480, 660, 840, 1020), result.policy.scheduledMinutes)
    }

    private class InMemoryRefreshStorage(initial: String?) : RefreshLogicPolicyStorage {
        private var raw: String? = initial
        override fun read(): String? = raw
        override fun write(value: String): Boolean {
            raw = value
            return true
        }
    }
}
