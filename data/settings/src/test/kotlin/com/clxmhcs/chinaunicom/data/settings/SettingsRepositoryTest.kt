package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun missingOrCorruptStorageFallsBackToSourceDefaults() {
        val missing = FakeStorage()
        assertEquals(QuotaRefreshPolicy(), DefaultSettingsRepository(missing).load())

        val corrupt = FakeStorage("{not-json")
        assertEquals(QuotaRefreshPolicy(), DefaultSettingsRepository(corrupt).load())
    }

    @Test
    fun tolerantDecodeDefaultsMissingOrWrongTypedQuotaFields() {
        val storage = FakeStorage(
            """
            {
              "schemaVersion": 3,
              "quota": {
                "automaticRefreshEnabled": false,
                "refreshOnColdLaunch": "wrong",
                "minimumIntervalMinutes": 30,
                "accountGapSeconds": null
              },
              "futureDomain": {"keep": true}
            }
            """.trimIndent(),
        )
        val policy = DefaultSettingsRepository(storage).load()

        assertEquals(
            QuotaRefreshPolicy(
                automaticRefreshEnabled = false,
                refreshOnColdLaunch = true,
                refreshOnForeground = true,
                minimumIntervalMinutes = 30,
                accountGapSeconds = 2,
            ),
            policy,
        )
    }

    @Test
    fun validLegacyDocumentAdvancesSchemaAndPreservesUnknownDomains() {
        val storage = FakeStorage(
            """
            {
              "schemaVersion": 1,
              "quota": {"automaticRefreshEnabled": false},
              "futureDomain": {"keep": 7}
            }
            """.trimIndent(),
        )

        val policy = DefaultSettingsRepository(storage).load()

        assertFalse(policy.automaticRefreshEnabled)
        val migrated = root(storage.value!!)
        assertEquals(JsonPrimitive(3), migrated["schemaVersion"])
        assertEquals(
            JsonObject(mapOf("keep" to JsonPrimitive(7))),
            migrated["futureDomain"],
        )
    }

    @Test
    fun saveWritesAllQuotaFieldsAndPreservesUnknownTopLevelFields() {
        val storage = FakeStorage(
            """{"schemaVersion":3,"balance":{"intervalMinutes":60}}""",
        )
        val repository = DefaultSettingsRepository(storage)
        val policy = QuotaRefreshPolicy(
            automaticRefreshEnabled = false,
            refreshOnColdLaunch = false,
            refreshOnForeground = true,
            minimumIntervalMinutes = 15,
            accountGapSeconds = 5,
        )

        val result = repository.saveQuotaRefreshPolicy(policy)

        assertTrue(result.persisted)
        assertTrue(result.changed)
        assertEquals(policy, repository.quotaRefreshPolicy.value)
        val saved = root(storage.value!!)
        assertEquals(JsonObject(mapOf("intervalMinutes" to JsonPrimitive(60))), saved["balance"])
        val quota = saved["quota"] as JsonObject
        assertEquals(JsonPrimitive(false), quota["automaticRefreshEnabled"])
        assertEquals(JsonPrimitive(false), quota["refreshOnColdLaunch"])
        assertEquals(JsonPrimitive(true), quota["refreshOnForeground"])
        assertEquals(JsonPrimitive(15), quota["minimumIntervalMinutes"])
        assertEquals(JsonPrimitive(5), quota["accountGapSeconds"])
    }

    @Test
    fun saveReportsNoDomainChangeForSamePolicyButStillPersists() {
        val policy = QuotaRefreshPolicy()
        val storage = FakeStorage(AppRefreshLogicPolicyCodec().mergeQuotaPolicy(null, policy))
        val repository = DefaultSettingsRepository(storage)
        val writesBefore = storage.writes

        val result = repository.saveQuotaRefreshPolicy(policy)

        assertTrue(result.persisted)
        assertFalse(result.changed)
        assertEquals(writesBefore + 1, storage.writes)
    }

    @Test
    fun failedWriteDoesNotPublishUnpersistedPolicy() {
        val storage = FakeStorage()
        val repository = DefaultSettingsRepository(storage)
        storage.failWrites = true
        val changed = QuotaRefreshPolicy(minimumIntervalMinutes = 60)

        val result = repository.saveQuotaRefreshPolicy(changed)

        assertFalse(result.persisted)
        assertTrue(result.changed)
        assertEquals(QuotaRefreshPolicy(), repository.quotaRefreshPolicy.value)
    }

    private fun root(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject
}

private class FakeStorage(
    var value: String? = null,
) : RefreshLogicPolicyStorage {
    var failWrites = false
    var writes = 0

    override fun read(): String? = value

    override fun write(value: String): Boolean {
        writes += 1
        if (failWrites) return false
        this.value = value
        return true
    }
}
