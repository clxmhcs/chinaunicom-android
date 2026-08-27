package com.clxmhcs.chinaunicom.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoRingSettingsRepositoryTest {
    @Test
    fun videoRingUsesSameSchemaDocumentAndPreservesOtherDomains() {
        val storage = MemoryStorage(
            """{"schemaVersion":3,"rebateGift":{"automaticRefreshEnabled":false},"futureDomain":{"x":1}}""",
        )
        val repository = UnifiedVideoRingSettingsRepository(storage)

        val defaults = repository.loadVideoRingRefreshPolicy()
        assertEquals(PageEntryRefreshMode.EVERY_ENTRY, defaults.entryMode)
        assertEquals(60, defaults.cacheValidityMinutes)

        val result = repository.saveVideoRingRefreshPolicy(
            VideoRingRefreshPolicy(PageEntryRefreshMode.REFRESH_WHEN_EXPIRED, 0),
        )

        assertTrue(result.persisted)
        assertEquals(1, result.policy.cacheValidityMinutes)
        val raw = storage.value.orEmpty()
        assertTrue(raw.contains("\"videoRing\""))
        assertTrue(raw.contains("\"refreshWhenExpired\""))
        assertTrue(raw.contains("\"rebateGift\""))
        assertTrue(raw.contains("\"futureDomain\""))
    }

    @Test
    fun malformedVideoRingFieldsFallBackToIosDefaults() {
        val codec = VideoRingRefreshPolicyCodec()
        val value = codec.decode(
            """{"schemaVersion":3,"videoRing":{"entryMode":"bad","cacheValidityMinutes":0}}""",
        )

        assertEquals(PageEntryRefreshMode.EVERY_ENTRY, value?.entryMode)
        assertEquals(1, value?.cacheValidityMinutes)
    }

    private class MemoryStorage(var value: String?) : RefreshLogicPolicyStorage {
        override fun read(): String? = value
        override fun write(value: String): Boolean {
            this.value = value
            return true
        }
    }
}
