package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.PhoneCarrier
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.PhoneSegmentAttributionRecord
import com.clxmhcs.chinaunicom.core.network.PhoneAttributionClient
import com.clxmhcs.chinaunicom.core.network.PhoneAttributionLookupResult
import com.clxmhcs.chinaunicom.core.network.PhoneCarrierSegmentFetchResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAttributionSettingsRepositoryTest {
    @Test
    fun manualCorrectionOnlyOverridesDisplayCarrier() {
        val storage = InMemoryPhoneStorage()
        val repository = DefaultPhoneAttributionSettingsRepository(storage, FakePhoneClient())
        val number = "13012345678"

        assertEquals(PhoneCarrier.CHINA_UNICOM, repository.automaticCarrier(number))
        assertEquals(PhoneCarrier.CHINA_UNICOM, repository.carrier(number))

        assertTrue(repository.setCorrection(number, PhoneCarrierCorrection.CHINA_MOBILE))
        assertEquals(PhoneCarrier.CHINA_UNICOM, repository.automaticCarrier(number))
        assertEquals(PhoneCarrier.CHINA_MOBILE, repository.carrier(number))

        assertTrue(repository.setCorrection(number, PhoneCarrierCorrection.AUTOMATIC))
        assertEquals(PhoneCarrier.CHINA_UNICOM, repository.carrier(number))
        assertNull(storage.corrections[number])
    }

    @Test
    fun refreshLocationPersistsSevenDigitPrefixWithoutCredentials() = runBlocking {
        val storage = InMemoryPhoneStorage()
        val clock = Clock.fixed(Instant.parse("2026-08-28T03:00:00Z"), ZoneOffset.UTC)
        val repository = DefaultPhoneAttributionSettingsRepository(storage, FakePhoneClient(), clock)

        val record = repository.refreshLocation("18612345678")

        assertEquals("1861234", record?.prefix)
        assertEquals("济南", record?.location)
        assertEquals(PhoneCarrier.CHINA_UNICOM, record?.carrier)
        assertEquals("济南", repository.cachedLocation("18612345678"))
    }

    @Test
    fun segmentUpdateFallsBackToBuiltInCarrierTableWhenRemoteHasNoMatch() = runBlocking {
        val storage = InMemoryPhoneStorage()
        val repository = DefaultPhoneAttributionSettingsRepository(storage, FakePhoneClient())

        val result = repository.updateCachedSegments()

        assertTrue(result.updatedCount > 0)
        assertEquals(PhoneCarrier.CHINA_UNICOM, storage.segments["130"]?.carrier)
        assertEquals(PhoneCarrier.CHINA_MOBILE, storage.segments["138"]?.carrier)
        assertEquals(PhoneCarrier.CHINA_TELECOM, storage.segments["189"]?.carrier)
        assertEquals(PhoneCarrier.CHINA_BROADNET, storage.segments["192"]?.carrier)
    }

    private class FakePhoneClient : PhoneAttributionClient {
        override suspend fun fetchCarrierSegment(prefix: String): PhoneCarrierSegmentFetchResult =
            PhoneCarrierSegmentFetchResult.NoMatch

        override suspend fun fetchAttribution(prefix: String): PhoneAttributionLookupResult? =
            if (prefix == "1861234") PhoneAttributionLookupResult("济南", PhoneCarrier.CHINA_UNICOM) else null
    }

    private class InMemoryPhoneStorage : PhoneAttributionSettingsStorage {
        var corrections: MutableMap<String, PhoneCarrierCorrection> = linkedMapOf()
        var segments: MutableMap<String, PhoneSegmentAttributionRecord> = linkedMapOf()

        override fun loadCorrections(): Map<String, PhoneCarrierCorrection> = corrections.toMap()
        override fun saveCorrections(value: Map<String, PhoneCarrierCorrection>): Boolean {
            corrections = value.toMutableMap()
            return true
        }

        override fun loadSegments(): Map<String, PhoneSegmentAttributionRecord> = segments.toMap()
        override fun saveSegments(value: Map<String, PhoneSegmentAttributionRecord>): Boolean {
            segments = value.toMutableMap()
            return true
        }
    }
}
