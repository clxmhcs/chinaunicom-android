package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyPackageSettingsRepositoryTest {
    @Test
    fun defaultsMatchIosAndSavePreservesUnknownDomains() {
        val storage = TestMyPackagePolicyStorage("""{"schemaVersion":3,"futureDomain":{"keep":7}}""")
        val repository = DefaultSettingsRepository(storage)

        val defaults = repository.loadMyPackageRefreshPolicy()
        assertEquals(PageEntryRefreshMode.EVERY_ENTRY, defaults.entryMode)
        assertEquals(30, defaults.cacheValidityMinutes)

        val result = repository.saveMyPackageRefreshPolicy(
            MyPackageRefreshPolicy(PageEntryRefreshMode.REFRESH_WHEN_EXPIRED, 45),
        )
        assertTrue(result.persisted)
        assertEquals(PageEntryRefreshMode.REFRESH_WHEN_EXPIRED, repository.myPackageRefreshPolicy.value.entryMode)
        assertEquals(45, repository.myPackageRefreshPolicy.value.cacheValidityMinutes)

        val root = Json.parseToJsonElement(storage.value!!) as JsonObject
        assertEquals(JsonObject(mapOf("keep" to JsonPrimitive(7))), root["futureDomain"])
        val packageDomain = root["myPackage"] as JsonObject
        assertEquals(JsonPrimitive("refreshWhenExpired"), packageDomain["entryMode"])
        assertEquals(JsonPrimitive(45), packageDomain["cacheValidityMinutes"])
    }

    @Test
    fun invalidPersistedModeFallsBackToEveryEntryAndFailedWriteDoesNotPublish() {
        val storage = TestMyPackagePolicyStorage("""{"schemaVersion":3,"myPackage":{"entryMode":"cachePreferred","cacheValidityMinutes":0}}""")
        val repository = DefaultSettingsRepository(storage)
        assertEquals(PageEntryRefreshMode.EVERY_ENTRY, repository.loadMyPackageRefreshPolicy().entryMode)
        assertEquals(1, repository.loadMyPackageRefreshPolicy().cacheValidityMinutes)

        storage.failWrites = true
        val result = repository.saveMyPackageRefreshPolicy(MyPackageRefreshPolicy(PageEntryRefreshMode.MANUAL_ONLY, 10))
        assertFalse(result.persisted)
        assertEquals(PageEntryRefreshMode.EVERY_ENTRY, repository.myPackageRefreshPolicy.value.entryMode)
    }
}

private class TestMyPackagePolicyStorage(var value: String? = null) : RefreshLogicPolicyStorage {
    var failWrites = false
    override fun read(): String? = value
    override fun write(value: String): Boolean {
        if (failWrites) return false
        this.value = value
        return true
    }
}
