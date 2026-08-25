package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComprehensiveBusinessSettingsTest {
    @Test
    fun missingDomainsUseExactIosM8Defaults() {
        val repository = DefaultSettingsRepository(M8PolicyStorage())

        assertEquals(OrderedBusinessRefreshPolicy(), repository.loadOrderedBusinessRefreshPolicy())
        assertEquals(PhoneBillRefreshPolicy(), repository.loadPhoneBillRefreshPolicy())
        assertEquals(IntegralRefreshPolicy(), repository.loadIntegralRefreshPolicy())

        assertEquals(CachedBusinessEntryMode.CACHE_PREFERRED, repository.orderedBusinessRefreshPolicy.value.entryMode)
        assertFalse(repository.orderedBusinessRefreshPolicy.value.noCacheAutoQuery)
        assertEquals(12, repository.orderedBusinessRefreshPolicy.value.cacheValidityHours)
        assertEquals(1, repository.orderedBusinessRefreshPolicy.value.refreshAllAccountGapSeconds)

        assertEquals(10, repository.phoneBillRefreshPolicy.value.currentMonthCacheMinutes)
        assertEquals(15, repository.phoneBillRefreshPolicy.value.historicalCacheDays)
        assertEquals(2, repository.phoneBillRefreshPolicy.value.monthlyRecheckDay)
        assertEquals(8, repository.phoneBillRefreshPolicy.value.monthlyRecheckHour)

        assertTrue(repository.integralRefreshPolicy.value.automaticRefreshEnabled)
        assertEquals(IntegralRefreshCycleMode.MONTHLY, repository.integralRefreshPolicy.value.cycleMode)
        assertEquals(2, repository.integralRefreshPolicy.value.monthlyRefreshDay)
        assertEquals(8, repository.integralRefreshPolicy.value.monthlyRefreshHour)
        assertEquals(24, repository.integralRefreshPolicy.value.fixedIntervalHours)
        assertTrue(repository.integralRefreshPolicy.value.checkOnEntry)
    }

    @Test
    fun tolerantDecodeFallsBackPerFieldAndPreservesValidValues() {
        val storage = M8PolicyStorage(
            """
            {
              "schemaVersion": 3,
              "orderedBusiness": {
                "entryMode": "refreshWhenExpired",
                "cacheValidityHours": "wrong",
                "noCacheAutoQuery": true,
                "refreshAllAccountGapSeconds": 4
              },
              "phoneBill": {
                "currentMonthCacheMinutes": 20,
                "historicalCacheDays": null,
                "monthlyRecheckDay": 5,
                "monthlyRecheckHour": 9
              },
              "integral": {
                "automaticRefreshEnabled": false,
                "cycleMode": "fixedInterval",
                "monthlyRefreshDay": "wrong",
                "monthlyRefreshHour": 7,
                "fixedIntervalHours": 36,
                "checkOnEntry": false
              }
            }
            """.trimIndent(),
        )
        val repository = DefaultSettingsRepository(storage)

        assertEquals(
            OrderedBusinessRefreshPolicy(
                entryMode = CachedBusinessEntryMode.REFRESH_WHEN_EXPIRED,
                cacheValidityHours = 12,
                noCacheAutoQuery = true,
                refreshAllAccountGapSeconds = 4,
            ),
            repository.loadOrderedBusinessRefreshPolicy(),
        )
        assertEquals(PhoneBillRefreshPolicy(20, 15, 5, 9), repository.loadPhoneBillRefreshPolicy())
        assertEquals(
            IntegralRefreshPolicy(false, IntegralRefreshCycleMode.FIXED_INTERVAL, 2, 7, 36, false),
            repository.loadIntegralRefreshPolicy(),
        )
    }

    @Test
    fun savesEachM8DomainWithoutDestroyingOtherOrFutureDomains() {
        val storage = M8PolicyStorage(
            """{"schemaVersion":3,"quota":{"automaticRefreshEnabled":false},"future":{"keep":7}}""",
        )
        val repository = DefaultSettingsRepository(storage)

        assertTrue(repository.saveOrderedBusinessRefreshPolicy(
            OrderedBusinessRefreshPolicy(CachedBusinessEntryMode.EVERY_ENTRY, 24, true, 3),
        ).persisted)
        assertTrue(repository.savePhoneBillRefreshPolicy(PhoneBillRefreshPolicy(30, 20, 3, 10)).persisted)
        assertTrue(repository.saveIntegralRefreshPolicy(
            IntegralRefreshPolicy(true, IntegralRefreshCycleMode.FIXED_INTERVAL, 4, 6, 48, true),
        ).persisted)

        val root = Json.parseToJsonElement(storage.value!!) as JsonObject
        assertEquals(JsonObject(mapOf("keep" to JsonPrimitive(7))), root["future"])
        assertTrue(root.containsKey("quota"))
        assertEquals(JsonPrimitive("everyEntry"), (root["orderedBusiness"] as JsonObject)["entryMode"])
        assertEquals(JsonPrimitive(30), (root["phoneBill"] as JsonObject)["currentMonthCacheMinutes"])
        assertEquals(JsonPrimitive("fixedInterval"), (root["integral"] as JsonObject)["cycleMode"])
    }

    @Test
    fun invalidSavedRangesAreNormalizedBeforePublication() {
        val storage = M8PolicyStorage()
        val repository = DefaultSettingsRepository(storage)

        val ordered = repository.saveOrderedBusinessRefreshPolicy(
            OrderedBusinessRefreshPolicy(cacheValidityHours = 0, refreshAllAccountGapSeconds = -2),
        ).policy
        val bill = repository.savePhoneBillRefreshPolicy(
            PhoneBillRefreshPolicy(currentMonthCacheMinutes = 0, historicalCacheDays = 0, monthlyRecheckDay = 31, monthlyRecheckHour = 99),
        ).policy
        val integral = repository.saveIntegralRefreshPolicy(
            IntegralRefreshPolicy(monthlyRefreshDay = 0, monthlyRefreshHour = -1, fixedIntervalHours = 0),
        ).policy

        assertEquals(1, ordered.cacheValidityHours)
        assertEquals(0, ordered.refreshAllAccountGapSeconds)
        assertEquals(1, bill.currentMonthCacheMinutes)
        assertEquals(1, bill.historicalCacheDays)
        assertEquals(28, bill.monthlyRecheckDay)
        assertEquals(23, bill.monthlyRecheckHour)
        assertEquals(1, integral.monthlyRefreshDay)
        assertEquals(0, integral.monthlyRefreshHour)
        assertEquals(1, integral.fixedIntervalHours)
    }
}

private class M8PolicyStorage(var value: String? = null) : RefreshLogicPolicyStorage {
    override fun read(): String? = value
    override fun write(value: String): Boolean {
        this.value = value
        return true
    }
}
