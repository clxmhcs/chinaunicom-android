package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RebateGiftSettingsRepositoryTest {
    @Test
    fun missingDomainUsesIosM9FDefaults() {
        val repository = UnifiedRefreshSettingsRepository(RebateGiftPolicyStorage("""{"schemaVersion":3}"""))

        assertEquals(RebateGiftRefreshPolicy(), repository.loadRebateGiftRefreshPolicy())
        assertTrue(repository.rebateGiftRefreshPolicy.value.automaticRefreshEnabled)
        assertEquals(2, repository.rebateGiftRefreshPolicy.value.monthlyRefreshDay)
        assertEquals(8, repository.rebateGiftRefreshPolicy.value.monthlyRefreshHour)
        assertTrue(repository.rebateGiftRefreshPolicy.value.queryImmediatelyWhenNoCache)
    }

    @Test
    fun tolerantDecodeFallsBackPerFieldAndNormalizesRanges() {
        val repository = UnifiedRefreshSettingsRepository(
            RebateGiftPolicyStorage(
                """
                {
                  "schemaVersion": 3,
                  "rebateGift": {
                    "automaticRefreshEnabled": false,
                    "monthlyRefreshDay": 31,
                    "monthlyRefreshHour": -2,
                    "queryImmediatelyWhenNoCache": false
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            RebateGiftRefreshPolicy(
                automaticRefreshEnabled = false,
                monthlyRefreshDay = 28,
                monthlyRefreshHour = 0,
                queryImmediatelyWhenNoCache = false,
            ),
            repository.loadRebateGiftRefreshPolicy(),
        )
    }

    @Test
    fun malformedFieldsFallBackWithoutDestroyingValidValues() {
        val repository = UnifiedRefreshSettingsRepository(
            RebateGiftPolicyStorage(
                """
                {
                  "schemaVersion": 3,
                  "rebateGift": {
                    "automaticRefreshEnabled": false,
                    "monthlyRefreshDay": "wrong",
                    "monthlyRefreshHour": 11,
                    "queryImmediatelyWhenNoCache": "wrong"
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            RebateGiftRefreshPolicy(
                automaticRefreshEnabled = false,
                monthlyRefreshDay = 2,
                monthlyRefreshHour = 11,
                queryImmediatelyWhenNoCache = true,
            ),
            repository.loadRebateGiftRefreshPolicy(),
        )
    }

    @Test
    fun saveUsesSameSchemaThreeDocumentAndPreservesOtherDomains() {
        val storage = RebateGiftPolicyStorage(
            """{"schemaVersion":3,"quota":{"automaticRefreshEnabled":false},"future":{"keep":7}}""",
        )
        val repository = UnifiedRefreshSettingsRepository(storage)

        val result = repository.saveRebateGiftRefreshPolicy(
            RebateGiftRefreshPolicy(
                automaticRefreshEnabled = false,
                monthlyRefreshDay = 5,
                monthlyRefreshHour = 9,
                queryImmediatelyWhenNoCache = false,
            ),
        )

        assertTrue(result.persisted)
        assertTrue(result.changed)
        val root = Json.parseToJsonElement(storage.value!!) as JsonObject
        assertEquals(JsonPrimitive(3), root["schemaVersion"])
        assertTrue(root.containsKey("quota"))
        assertEquals(JsonObject(mapOf("keep" to JsonPrimitive(7))), root["future"])
        val rebateGift = root["rebateGift"] as JsonObject
        assertEquals(JsonPrimitive(false), rebateGift["automaticRefreshEnabled"])
        assertEquals(JsonPrimitive(5), rebateGift["monthlyRefreshDay"])
        assertEquals(JsonPrimitive(9), rebateGift["monthlyRefreshHour"])
        assertEquals(JsonPrimitive(false), rebateGift["queryImmediatelyWhenNoCache"])
    }

    @Test
    fun saveNormalizesRangesAndPublishesPersistedPolicy() {
        val storage = RebateGiftPolicyStorage()
        val repository = UnifiedRefreshSettingsRepository(storage)

        val result = repository.saveRebateGiftRefreshPolicy(
            RebateGiftRefreshPolicy(monthlyRefreshDay = 0, monthlyRefreshHour = 99),
        )

        assertTrue(result.persisted)
        assertEquals(1, result.policy.monthlyRefreshDay)
        assertEquals(23, result.policy.monthlyRefreshHour)
        assertEquals(result.policy, repository.rebateGiftRefreshPolicy.value)
        assertFalse(result.changed.not())
    }
}

private class RebateGiftPolicyStorage(var value: String? = null) : RefreshLogicPolicyStorage {
    override fun read(): String? = value

    override fun write(value: String): Boolean {
        this.value = value
        return true
    }
}
