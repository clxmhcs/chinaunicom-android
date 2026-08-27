package com.clxmhcs.chinaunicom.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

data class RebateGiftRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val monthlyRefreshDay: Int = 2,
    val monthlyRefreshHour: Int = 8,
    val queryImmediatelyWhenNoCache: Boolean = true,
)

data class RebateGiftRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: RebateGiftRefreshPolicy,
)

interface RebateGiftSettingsRepository : SettingsRepository {
    val rebateGiftRefreshPolicy: StateFlow<RebateGiftRefreshPolicy>
    fun loadRebateGiftRefreshPolicy(): RebateGiftRefreshPolicy
    fun saveRebateGiftRefreshPolicy(policy: RebateGiftRefreshPolicy): RebateGiftRefreshPolicySaveResult
}

/**
 * One Android refresh-settings authority. Existing settings domains are delegated to the closed
 * SettingsRepository implementation while M9-F adds the iOS schema-3 `rebateGift` domain to the
 * same persisted JSON document and storage key.
 */
class UnifiedRefreshSettingsRepository(
    private val storage: RefreshLogicPolicyStorage,
    balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    private val rebateGiftCodec: RebateGiftRefreshPolicyCodec = RebateGiftRefreshPolicyCodec(),
) : RebateGiftSettingsRepository,
    SettingsRepository by DefaultSettingsRepository(storage, balanceIntervalSynchronizer) {

    private val initialRebateGift = loadDecoded()
    private val _rebateGiftRefreshPolicy = MutableStateFlow(initialRebateGift)
    override val rebateGiftRefreshPolicy: StateFlow<RebateGiftRefreshPolicy> = _rebateGiftRefreshPolicy.asStateFlow()

    override fun loadRebateGiftRefreshPolicy(): RebateGiftRefreshPolicy {
        val policy = loadDecoded()
        _rebateGiftRefreshPolicy.value = policy
        return policy
    }

    override fun saveRebateGiftRefreshPolicy(policy: RebateGiftRefreshPolicy): RebateGiftRefreshPolicySaveResult {
        val normalized = policy.copy(
            monthlyRefreshDay = policy.monthlyRefreshDay.coerceIn(1, 28),
            monthlyRefreshHour = policy.monthlyRefreshHour.coerceIn(0, 23),
        )
        val previous = loadDecoded()
        val persisted = storage.write(rebateGiftCodec.merge(storage.read(), normalized))
        if (persisted) _rebateGiftRefreshPolicy.value = normalized
        return RebateGiftRefreshPolicySaveResult(
            persisted = persisted,
            changed = previous != normalized,
            policy = normalized,
        )
    }

    private fun loadDecoded(): RebateGiftRefreshPolicy =
        storage.read()?.let(rebateGiftCodec::decode) ?: RebateGiftRefreshPolicy()
}

class RebateGiftRefreshPolicyCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): RebateGiftRefreshPolicy? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val domain = root[REBATE_GIFT_KEY] as? JsonObject ?: return@runCatching RebateGiftRefreshPolicy()
        val defaults = RebateGiftRefreshPolicy()
        RebateGiftRefreshPolicy(
            automaticRefreshEnabled = (domain[AUTOMATIC_REFRESH_ENABLED_KEY] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.automaticRefreshEnabled,
            monthlyRefreshDay = ((domain[MONTHLY_REFRESH_DAY_KEY] as? JsonPrimitive)?.intOrNull
                ?: defaults.monthlyRefreshDay).coerceIn(1, 28),
            monthlyRefreshHour = ((domain[MONTHLY_REFRESH_HOUR_KEY] as? JsonPrimitive)?.intOrNull
                ?: defaults.monthlyRefreshHour).coerceIn(0, 23),
            queryImmediatelyWhenNoCache = (domain[QUERY_IMMEDIATELY_WHEN_NO_CACHE_KEY] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.queryImmediatelyWhenNoCache,
        )
    }.getOrNull()

    fun merge(existingRaw: String?, policy: RebateGiftRefreshPolicy): String {
        val existing = existingRaw?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION)
        merged[REBATE_GIFT_KEY] = JsonObject(
            linkedMapOf(
                AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(policy.automaticRefreshEnabled),
                MONTHLY_REFRESH_DAY_KEY to JsonPrimitive(policy.monthlyRefreshDay.coerceIn(1, 28)),
                MONTHLY_REFRESH_HOUR_KEY to JsonPrimitive(policy.monthlyRefreshHour.coerceIn(0, 23)),
                QUERY_IMMEDIATELY_WHEN_NO_CACHE_KEY to JsonPrimitive(policy.queryImmediatelyWhenNoCache),
            ),
        )
        return JsonObject(merged).toString()
    }

    companion object {
        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val REBATE_GIFT_KEY = "rebateGift"
        private const val AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val MONTHLY_REFRESH_DAY_KEY = "monthlyRefreshDay"
        private const val MONTHLY_REFRESH_HOUR_KEY = "monthlyRefreshHour"
        private const val QUERY_IMMEDIATELY_WHEN_NO_CACHE_KEY = "queryImmediatelyWhenNoCache"
    }
}
