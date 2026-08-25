package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

data class QuotaRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: QuotaRefreshPolicy,
)

interface SettingsRepository {
    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy>

    fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy

    fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult
}

interface RefreshLogicPolicyStorage {
    fun read(): String?

    fun write(value: String): Boolean
}

/**
 * Quota slice of iOS AppRefreshLogicPolicyStore.
 *
 * The source document currently uses schemaVersion 3 and the storage key
 * `chinaunicom.appRefreshLogic.policy.v1`. Android M6-C persists only the quota domain needed by
 * the current production refresh coordinator, while preserving unknown top-level JSON fields so
 * later M6/M8-M13 domains can extend the same document without being erased by quota edits.
 */
class DefaultSettingsRepository(
    private val storage: RefreshLogicPolicyStorage,
    private val codec: AppRefreshLogicPolicyCodec = AppRefreshLogicPolicyCodec(),
) : SettingsRepository {
    private val _quotaRefreshPolicy = MutableStateFlow(loadFromStorage())

    override val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = _quotaRefreshPolicy.asStateFlow()

    override fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy {
        val loaded = loadFromStorage()
        if (_quotaRefreshPolicy.value != loaded) {
            _quotaRefreshPolicy.value = loaded
        }
        return loaded
    }

    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult {
        val previousRaw = storage.read()
        val previousPolicy = previousRaw?.let(codec::decode)?.quota
        val changed = previousPolicy == null || previousPolicy != policy
        val encoded = codec.mergeQuotaPolicy(previousRaw, policy)
        val persisted = storage.write(encoded)
        if (persisted) {
            _quotaRefreshPolicy.value = policy
        }
        return QuotaRefreshPolicySaveResult(
            persisted = persisted,
            changed = changed,
            policy = policy,
        )
    }

    private fun loadFromStorage(): QuotaRefreshPolicy {
        val raw = storage.read() ?: return QuotaRefreshPolicy()
        val decoded = codec.decode(raw) ?: return QuotaRefreshPolicy()

        if (decoded.schemaVersion < AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION) {
            // Source AppRefreshLogicPolicyStore rewrites valid legacy documents to the current
            // schema during load. Quota has no value migration in v3, so only the envelope/version
            // is advanced here while preserving unknown domains.
            storage.write(codec.mergeQuotaPolicy(raw, decoded.quota))
        }
        return decoded.quota
    }
}

data class DecodedAppRefreshLogicPolicy(
    val schemaVersion: Int,
    val quota: QuotaRefreshPolicy,
)

class AppRefreshLogicPolicyCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): DecodedAppRefreshLogicPolicy? {
        val root = parseRoot(raw) ?: return null
        val defaults = QuotaRefreshPolicy()
        val quota = root[QUOTA_KEY] as? JsonObject

        return DecodedAppRefreshLogicPolicy(
            schemaVersion = intValue(root[SCHEMA_VERSION_KEY]) ?: 1,
            quota = QuotaRefreshPolicy(
                automaticRefreshEnabled = boolValue(quota?.get(AUTOMATIC_REFRESH_ENABLED_KEY))
                    ?: defaults.automaticRefreshEnabled,
                refreshOnColdLaunch = boolValue(quota?.get(REFRESH_ON_COLD_LAUNCH_KEY))
                    ?: defaults.refreshOnColdLaunch,
                refreshOnForeground = boolValue(quota?.get(REFRESH_ON_FOREGROUND_KEY))
                    ?: defaults.refreshOnForeground,
                minimumIntervalMinutes = intValue(quota?.get(MINIMUM_INTERVAL_MINUTES_KEY))
                    ?: defaults.minimumIntervalMinutes,
                accountGapSeconds = intValue(quota?.get(ACCOUNT_GAP_SECONDS_KEY))
                    ?: defaults.accountGapSeconds,
            ),
        )
    }

    fun mergeQuotaPolicy(existingRaw: String?, policy: QuotaRefreshPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val quota = JsonObject(
            linkedMapOf(
                AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(policy.automaticRefreshEnabled),
                REFRESH_ON_COLD_LAUNCH_KEY to JsonPrimitive(policy.refreshOnColdLaunch),
                REFRESH_ON_FOREGROUND_KEY to JsonPrimitive(policy.refreshOnForeground),
                MINIMUM_INTERVAL_MINUTES_KEY to JsonPrimitive(policy.minimumIntervalMinutes),
                ACCOUNT_GAP_SECONDS_KEY to JsonPrimitive(policy.accountGapSeconds),
            ),
        )
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[QUOTA_KEY] = quota
        return JsonObject(merged).toString()
    }

    private fun parseRoot(raw: String): JsonObject? = runCatching {
        json.parseToJsonElement(raw) as? JsonObject
    }.getOrNull()

    private fun boolValue(value: JsonElement?): Boolean? =
        (value as? JsonPrimitive)?.booleanOrNull

    private fun intValue(value: JsonElement?): Int? =
        (value as? JsonPrimitive)?.intOrNull

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val STORAGE_KEY = "chinaunicom.appRefreshLogic.policy.v1"

        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val QUOTA_KEY = "quota"
        private const val AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val REFRESH_ON_COLD_LAUNCH_KEY = "refreshOnColdLaunch"
        private const val REFRESH_ON_FOREGROUND_KEY = "refreshOnForeground"
        private const val MINIMUM_INTERVAL_MINUTES_KEY = "minimumIntervalMinutes"
        private const val ACCOUNT_GAP_SECONDS_KEY = "accountGapSeconds"
    }
}
