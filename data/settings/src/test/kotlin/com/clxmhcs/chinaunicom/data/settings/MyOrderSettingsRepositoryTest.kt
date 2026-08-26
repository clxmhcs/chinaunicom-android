package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyOrderSettingsRepositoryTest {
    @Test fun defaultsTrueAndSavesFalseWithoutDroppingUnknownDomain() {
        val storage = TestOrderPolicyStorage("""{"schemaVersion":3,"futureDomain":{"keep":7}}""")
        val repository = DefaultSettingsRepository(storage)
        assertTrue(repository.loadOrderRefreshPolicy().refreshOnEntry)
        val result = repository.saveOrderRefreshPolicy(OrderRefreshPolicy(false))
        assertTrue(result.persisted)
        assertFalse(repository.orderRefreshPolicy.value.refreshOnEntry)
        val root = Json.parseToJsonElement(storage.value!!) as JsonObject
        assertEquals(JsonObject(mapOf("keep" to JsonPrimitive(7))), root["futureDomain"])
        assertEquals(JsonPrimitive(false), (root["orders"] as JsonObject)["refreshOnEntry"])
    }

    @Test fun failedWriteDoesNotPublish() {
        val storage = TestOrderPolicyStorage().apply { failWrites = true }
        val repository = DefaultSettingsRepository(storage)
        val result = repository.saveOrderRefreshPolicy(OrderRefreshPolicy(false))
        assertFalse(result.persisted)
        assertTrue(repository.orderRefreshPolicy.value.refreshOnEntry)
    }
}

private class TestOrderPolicyStorage(var value: String? = null) : RefreshLogicPolicyStorage {
    var failWrites = false
    override fun read(): String? = value
    override fun write(value: String): Boolean {
        if (failWrites) return false
        this.value = value
        return true
    }
}
