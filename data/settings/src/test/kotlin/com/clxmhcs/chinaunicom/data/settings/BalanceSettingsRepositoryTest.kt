package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceSettingsRepositoryTest {
    @Test
    fun balanceDefaultsAndLegacyFifteenMinuteV2MigrateToSixty() {
        assertEquals(BalanceRefreshPolicy(), DefaultSettingsRepository(FakeBalancePolicyStorage()).loadBalanceRefreshPolicy())
        val storage = FakeBalancePolicyStorage(
            """{"schemaVersion":2,"balance":{"automaticRefreshEnabled":true,"checkOnForeground":true,"intervalMinutes":15,"failureRetryMinutes":9}}""",
        )
        val policy = DefaultSettingsRepository(storage).loadBalanceRefreshPolicy()
        assertEquals(60, policy.intervalMinutes)
        assertEquals(9, policy.failureRetryMinutes)
        assertEquals(JsonPrimitive(3), (Json.parseToJsonElement(storage.value!!) as JsonObject)["schemaVersion"])
    }

    @Test
    fun saveBalanceSynchronizesSharedIntervalBeforeLocalPolicyAndPreservesQuota() {
        val storage = FakeBalancePolicyStorage(
            """{"schemaVersion":3,"quota":{"automaticRefreshEnabled":false},"future":{"keep":1}}""",
        )
        val synchronized = mutableListOf<Int>()
        val repository = DefaultSettingsRepository(
            storage,
            BalanceRefreshIntervalSynchronizer { minutes -> synchronized += minutes; true },
        )
        synchronized.clear()
        val result = repository.saveBalanceRefreshPolicy(
            BalanceRefreshPolicy(false, true, 90, 20),
        )
        assertTrue(result.persisted)
        assertEquals(listOf(90), synchronized)
        val root = Json.parseToJsonElement(storage.value!!) as JsonObject
        assertEquals(JsonObject(mapOf("keep" to JsonPrimitive(1))), root["future"])
        assertTrue(root.containsKey("quota"))
        assertEquals(JsonPrimitive(90), (root["balance"] as JsonObject)["intervalMinutes"])
    }

    @Test
    fun sharedIntervalFailurePreventsLocalPolicyCommit() {
        val storage = FakeBalancePolicyStorage()
        val before = storage.value
        val repository = DefaultSettingsRepository(
            storage,
            BalanceRefreshIntervalSynchronizer { false },
        )
        val result = repository.saveBalanceRefreshPolicy(BalanceRefreshPolicy(intervalMinutes = 120))
        assertFalse(result.persisted)
        assertEquals(before, storage.value)
        assertEquals(BalanceRefreshPolicy(), repository.balanceRefreshPolicy.value)
    }
}

private class FakeBalancePolicyStorage(var value: String? = null) : RefreshLogicPolicyStorage {
    override fun read(): String? = value
    override fun write(value: String): Boolean {
        this.value = value
        return true
    }
}
