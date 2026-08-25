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

data class BalanceRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val checkOnForeground: Boolean = true,
    val intervalMinutes: Int = 60,
    val failureRetryMinutes: Int = 15,
)

fun interface BalanceRefreshIntervalSynchronizer {
    fun setRefreshIntervalMinutes(minutes: Int): Boolean
}

data class QuotaRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: QuotaRefreshPolicy,
)

data class BalanceRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: BalanceRefreshPolicy,
)

interface SettingsRepository {
    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy>
    val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy>

    fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy
    fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy
    fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult
    fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult
}

interface RefreshLogicPolicyStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

class DefaultSettingsRepository(
    private val storage: RefreshLogicPolicyStorage,
    private val balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    private val codec: AppRefreshLogicPolicyCodec = AppRefreshLogicPolicyCodec(),
) : SettingsRepository {
    private val initial = loadFromStorage()
    private val _quotaRefreshPolicy = MutableStateFlow(initial.quota)
    private val _balanceRefreshPolicy = MutableStateFlow(initial.balance)

    override val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = _quotaRefreshPolicy.asStateFlow()
    override val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy> = _balanceRefreshPolicy.asStateFlow()

    override fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy = reload().quota
    override fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy = reload().balance

    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult {
        val previousRaw = storage.read()
        val previous = previousRaw?.let(codec::decode)?.quota
        val encoded = codec.mergeQuotaPolicy(previousRaw, policy)
        val persisted = storage.write(encoded)
        if (persisted) _quotaRefreshPolicy.value = policy
        return QuotaRefreshPolicySaveResult(persisted, previous == null || previous != policy, policy)
    }

    override fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult {
        val normalized = policy.copy(intervalMinutes = policy.intervalMinutes.coerceIn(1, 24 * 60))
        val previousRaw = storage.read()
        val previous = previousRaw?.let(codec::decode)?.balance
        if (!balanceIntervalSynchronizer.setRefreshIntervalMinutes(normalized.intervalMinutes)) {
            return BalanceRefreshPolicySaveResult(false, previous == null || previous != normalized, normalized)
        }
        val encoded = codec.mergeBalancePolicy(previousRaw, normalized)
        val persisted = storage.write(encoded)
        if (persisted) _balanceRefreshPolicy.value = normalized
        return BalanceRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    private fun reload(): DecodedAppRefreshLogicPolicy {
        val value = loadFromStorage()
        _quotaRefreshPolicy.value = value.quota
        _balanceRefreshPolicy.value = value.balance
        return value
    }

    private fun loadFromStorage(): DecodedAppRefreshLogicPolicy {
        val raw = storage.read()
        val decoded = raw?.let(codec::decode) ?: DecodedAppRefreshLogicPolicy(
            schemaVersion = AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION,
            quota = QuotaRefreshPolicy(),
            balance = BalanceRefreshPolicy(),
        )
        if (raw != null && decoded.schemaVersion < AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION) {
            storage.write(codec.mergeAll(raw, decoded.quota, decoded.balance))
        }
        balanceIntervalSynchronizer.setRefreshIntervalMinutes(decoded.balance.intervalMinutes)
        return decoded
    }
}

data class DecodedAppRefreshLogicPolicy(
    val schemaVersion: Int,
    val quota: QuotaRefreshPolicy,
    val balance: BalanceRefreshPolicy,
)

class AppRefreshLogicPolicyCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): DecodedAppRefreshLogicPolicy? {
        val root = parseRoot(raw) ?: return null
        val schemaVersion = intValue(root[SCHEMA_VERSION_KEY]) ?: 1
        val quotaDefaults = QuotaRefreshPolicy()
        val quota = root[QUOTA_KEY] as? JsonObject
        val balanceDefaults = BalanceRefreshPolicy()
        val balance = root[BALANCE_KEY] as? JsonObject
        val decodedInterval = intValue(balance?.get(BALANCE_INTERVAL_MINUTES_KEY)) ?: balanceDefaults.intervalMinutes
        val migratedInterval = if (schemaVersion < 3 && decodedInterval == 15) 60 else decodedInterval
        return DecodedAppRefreshLogicPolicy(
            schemaVersion = schemaVersion,
            quota = QuotaRefreshPolicy(
                automaticRefreshEnabled = boolValue(quota?.get(AUTOMATIC_REFRESH_ENABLED_KEY)) ?: quotaDefaults.automaticRefreshEnabled,
                refreshOnColdLaunch = boolValue(quota?.get(REFRESH_ON_COLD_LAUNCH_KEY)) ?: quotaDefaults.refreshOnColdLaunch,
                refreshOnForeground = boolValue(quota?.get(REFRESH_ON_FOREGROUND_KEY)) ?: quotaDefaults.refreshOnForeground,
                minimumIntervalMinutes = intValue(quota?.get(MINIMUM_INTERVAL_MINUTES_KEY)) ?: quotaDefaults.minimumIntervalMinutes,
                accountGapSeconds = intValue(quota?.get(ACCOUNT_GAP_SECONDS_KEY)) ?: quotaDefaults.accountGapSeconds,
            ),
            balance = BalanceRefreshPolicy(
                automaticRefreshEnabled = boolValue(balance?.get(BALANCE_AUTOMATIC_REFRESH_ENABLED_KEY)) ?: balanceDefaults.automaticRefreshEnabled,
                checkOnForeground = boolValue(balance?.get(BALANCE_CHECK_ON_FOREGROUND_KEY)) ?: balanceDefaults.checkOnForeground,
                intervalMinutes = migratedInterval.coerceIn(1, 24 * 60),
                failureRetryMinutes = intValue(balance?.get(BALANCE_FAILURE_RETRY_MINUTES_KEY)) ?: balanceDefaults.failureRetryMinutes,
            ),
        )
    }

    fun mergeQuotaPolicy(existingRaw: String?, policy: QuotaRefreshPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[QUOTA_KEY] = quotaElement(policy)
        return JsonObject(merged).toString()
    }

    fun mergeBalancePolicy(existingRaw: String?, policy: BalanceRefreshPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[BALANCE_KEY] = balanceElement(policy)
        return JsonObject(merged).toString()
    }

    fun mergeAll(existingRaw: String?, quota: QuotaRefreshPolicy, balance: BalanceRefreshPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[QUOTA_KEY] = quotaElement(quota)
        merged[BALANCE_KEY] = balanceElement(balance)
        return JsonObject(merged).toString()
    }

    private fun quotaElement(policy: QuotaRefreshPolicy) = JsonObject(linkedMapOf(
        AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(policy.automaticRefreshEnabled),
        REFRESH_ON_COLD_LAUNCH_KEY to JsonPrimitive(policy.refreshOnColdLaunch),
        REFRESH_ON_FOREGROUND_KEY to JsonPrimitive(policy.refreshOnForeground),
        MINIMUM_INTERVAL_MINUTES_KEY to JsonPrimitive(policy.minimumIntervalMinutes),
        ACCOUNT_GAP_SECONDS_KEY to JsonPrimitive(policy.accountGapSeconds),
    ))

    private fun balanceElement(policy: BalanceRefreshPolicy) = JsonObject(linkedMapOf(
        BALANCE_AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(policy.automaticRefreshEnabled),
        BALANCE_CHECK_ON_FOREGROUND_KEY to JsonPrimitive(policy.checkOnForeground),
        BALANCE_INTERVAL_MINUTES_KEY to JsonPrimitive(policy.intervalMinutes.coerceIn(1, 24 * 60)),
        BALANCE_FAILURE_RETRY_MINUTES_KEY to JsonPrimitive(policy.failureRetryMinutes),
    ))

    private fun parseRoot(raw: String): JsonObject? = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    private fun boolValue(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.booleanOrNull
    private fun intValue(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val STORAGE_KEY = "chinaunicom.appRefreshLogic.policy.v1"
        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val QUOTA_KEY = "quota"
        private const val BALANCE_KEY = "balance"
        private const val AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val REFRESH_ON_COLD_LAUNCH_KEY = "refreshOnColdLaunch"
        private const val REFRESH_ON_FOREGROUND_KEY = "refreshOnForeground"
        private const val MINIMUM_INTERVAL_MINUTES_KEY = "minimumIntervalMinutes"
        private const val ACCOUNT_GAP_SECONDS_KEY = "accountGapSeconds"
        private const val BALANCE_AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val BALANCE_CHECK_ON_FOREGROUND_KEY = "checkOnForeground"
        private const val BALANCE_INTERVAL_MINUTES_KEY = "intervalMinutes"
        private const val BALANCE_FAILURE_RETRY_MINUTES_KEY = "failureRetryMinutes"
    }
}
