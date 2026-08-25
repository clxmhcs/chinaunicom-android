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

enum class CachedBusinessEntryMode(val rawValue: String) {
    CACHE_PREFERRED("cachePreferred"),
    REFRESH_WHEN_EXPIRED("refreshWhenExpired"),
    EVERY_ENTRY("everyEntry"),
    MANUAL_ONLY("manualOnly");

    companion object {
        fun fromRawValue(value: String?): CachedBusinessEntryMode? = entries.firstOrNull { it.rawValue == value }
    }
}

data class OrderedBusinessRefreshPolicy(
    val entryMode: CachedBusinessEntryMode = CachedBusinessEntryMode.CACHE_PREFERRED,
    val cacheValidityHours: Int = 12,
    val noCacheAutoQuery: Boolean = false,
    val refreshAllAccountGapSeconds: Int = 1,
)

data class PhoneBillRefreshPolicy(
    val currentMonthCacheMinutes: Int = 10,
    val historicalCacheDays: Int = 15,
    val monthlyRecheckDay: Int = 2,
    val monthlyRecheckHour: Int = 8,
)

enum class IntegralRefreshCycleMode(val rawValue: String) {
    MONTHLY("monthly"),
    FIXED_INTERVAL("fixedInterval"),
    MANUAL_ONLY("manualOnly");

    companion object {
        fun fromRawValue(value: String?): IntegralRefreshCycleMode? = entries.firstOrNull { it.rawValue == value }
    }
}

data class IntegralRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val cycleMode: IntegralRefreshCycleMode = IntegralRefreshCycleMode.MONTHLY,
    val monthlyRefreshDay: Int = 2,
    val monthlyRefreshHour: Int = 8,
    val fixedIntervalHours: Int = 24,
    val checkOnEntry: Boolean = true,
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

data class OrderedBusinessRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: OrderedBusinessRefreshPolicy,
)

data class PhoneBillRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: PhoneBillRefreshPolicy,
)

data class IntegralRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: IntegralRefreshPolicy,
)

interface SettingsRepository {
    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy>
    val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy>
    val orderedBusinessRefreshPolicy: StateFlow<OrderedBusinessRefreshPolicy>
    val phoneBillRefreshPolicy: StateFlow<PhoneBillRefreshPolicy>
    val integralRefreshPolicy: StateFlow<IntegralRefreshPolicy>

    fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy
    fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy
    fun loadOrderedBusinessRefreshPolicy(): OrderedBusinessRefreshPolicy
    fun loadPhoneBillRefreshPolicy(): PhoneBillRefreshPolicy
    fun loadIntegralRefreshPolicy(): IntegralRefreshPolicy

    fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult
    fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult
    fun saveOrderedBusinessRefreshPolicy(policy: OrderedBusinessRefreshPolicy): OrderedBusinessRefreshPolicySaveResult
    fun savePhoneBillRefreshPolicy(policy: PhoneBillRefreshPolicy): PhoneBillRefreshPolicySaveResult
    fun saveIntegralRefreshPolicy(policy: IntegralRefreshPolicy): IntegralRefreshPolicySaveResult
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
    private val _orderedBusinessRefreshPolicy = MutableStateFlow(initial.orderedBusiness)
    private val _phoneBillRefreshPolicy = MutableStateFlow(initial.phoneBill)
    private val _integralRefreshPolicy = MutableStateFlow(initial.integral)

    override val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy> = _quotaRefreshPolicy.asStateFlow()
    override val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy> = _balanceRefreshPolicy.asStateFlow()
    override val orderedBusinessRefreshPolicy: StateFlow<OrderedBusinessRefreshPolicy> = _orderedBusinessRefreshPolicy.asStateFlow()
    override val phoneBillRefreshPolicy: StateFlow<PhoneBillRefreshPolicy> = _phoneBillRefreshPolicy.asStateFlow()
    override val integralRefreshPolicy: StateFlow<IntegralRefreshPolicy> = _integralRefreshPolicy.asStateFlow()

    override fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy = reload().quota
    override fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy = reload().balance
    override fun loadOrderedBusinessRefreshPolicy(): OrderedBusinessRefreshPolicy = reload().orderedBusiness
    override fun loadPhoneBillRefreshPolicy(): PhoneBillRefreshPolicy = reload().phoneBill
    override fun loadIntegralRefreshPolicy(): IntegralRefreshPolicy = reload().integral

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

    override fun saveOrderedBusinessRefreshPolicy(
        policy: OrderedBusinessRefreshPolicy,
    ): OrderedBusinessRefreshPolicySaveResult {
        val normalized = policy.copy(
            cacheValidityHours = policy.cacheValidityHours.coerceAtLeast(1),
            refreshAllAccountGapSeconds = policy.refreshAllAccountGapSeconds.coerceAtLeast(0),
        )
        val previousRaw = storage.read()
        val previous = previousRaw?.let(codec::decode)?.orderedBusiness
        val persisted = storage.write(codec.mergeOrderedBusinessPolicy(previousRaw, normalized))
        if (persisted) _orderedBusinessRefreshPolicy.value = normalized
        return OrderedBusinessRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun savePhoneBillRefreshPolicy(policy: PhoneBillRefreshPolicy): PhoneBillRefreshPolicySaveResult {
        val normalized = policy.copy(
            currentMonthCacheMinutes = policy.currentMonthCacheMinutes.coerceAtLeast(1),
            historicalCacheDays = policy.historicalCacheDays.coerceAtLeast(1),
            monthlyRecheckDay = policy.monthlyRecheckDay.coerceIn(1, 28),
            monthlyRecheckHour = policy.monthlyRecheckHour.coerceIn(0, 23),
        )
        val previousRaw = storage.read()
        val previous = previousRaw?.let(codec::decode)?.phoneBill
        val persisted = storage.write(codec.mergePhoneBillPolicy(previousRaw, normalized))
        if (persisted) _phoneBillRefreshPolicy.value = normalized
        return PhoneBillRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun saveIntegralRefreshPolicy(policy: IntegralRefreshPolicy): IntegralRefreshPolicySaveResult {
        val normalized = policy.copy(
            monthlyRefreshDay = policy.monthlyRefreshDay.coerceIn(1, 28),
            monthlyRefreshHour = policy.monthlyRefreshHour.coerceIn(0, 23),
            fixedIntervalHours = policy.fixedIntervalHours.coerceAtLeast(1),
        )
        val previousRaw = storage.read()
        val previous = previousRaw?.let(codec::decode)?.integral
        val persisted = storage.write(codec.mergeIntegralPolicy(previousRaw, normalized))
        if (persisted) _integralRefreshPolicy.value = normalized
        return IntegralRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    private fun reload(): DecodedAppRefreshLogicPolicy {
        val value = loadFromStorage()
        _quotaRefreshPolicy.value = value.quota
        _balanceRefreshPolicy.value = value.balance
        _orderedBusinessRefreshPolicy.value = value.orderedBusiness
        _phoneBillRefreshPolicy.value = value.phoneBill
        _integralRefreshPolicy.value = value.integral
        return value
    }

    private fun loadFromStorage(): DecodedAppRefreshLogicPolicy {
        val raw = storage.read()
        val decoded = raw?.let(codec::decode) ?: DecodedAppRefreshLogicPolicy.defaults()
        if (raw != null && decoded.schemaVersion < AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION) {
            storage.write(codec.mergeAll(raw, decoded))
        }
        balanceIntervalSynchronizer.setRefreshIntervalMinutes(decoded.balance.intervalMinutes)
        return decoded
    }
}

data class DecodedAppRefreshLogicPolicy(
    val schemaVersion: Int,
    val quota: QuotaRefreshPolicy,
    val balance: BalanceRefreshPolicy,
    val orderedBusiness: OrderedBusinessRefreshPolicy,
    val phoneBill: PhoneBillRefreshPolicy,
    val integral: IntegralRefreshPolicy,
) {
    companion object {
        fun defaults() = DecodedAppRefreshLogicPolicy(
            schemaVersion = AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION,
            quota = QuotaRefreshPolicy(),
            balance = BalanceRefreshPolicy(),
            orderedBusiness = OrderedBusinessRefreshPolicy(),
            phoneBill = PhoneBillRefreshPolicy(),
            integral = IntegralRefreshPolicy(),
        )
    }
}

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
        val orderedDefaults = OrderedBusinessRefreshPolicy()
        val ordered = root[ORDERED_BUSINESS_KEY] as? JsonObject
        val billDefaults = PhoneBillRefreshPolicy()
        val bill = root[PHONE_BILL_KEY] as? JsonObject
        val integralDefaults = IntegralRefreshPolicy()
        val integral = root[INTEGRAL_KEY] as? JsonObject
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
            orderedBusiness = OrderedBusinessRefreshPolicy(
                entryMode = CachedBusinessEntryMode.fromRawValue(stringValue(ordered?.get(ENTRY_MODE_KEY))) ?: orderedDefaults.entryMode,
                cacheValidityHours = (intValue(ordered?.get(CACHE_VALIDITY_HOURS_KEY)) ?: orderedDefaults.cacheValidityHours).coerceAtLeast(1),
                noCacheAutoQuery = boolValue(ordered?.get(NO_CACHE_AUTO_QUERY_KEY)) ?: orderedDefaults.noCacheAutoQuery,
                refreshAllAccountGapSeconds = (intValue(ordered?.get(REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY))
                    ?: orderedDefaults.refreshAllAccountGapSeconds).coerceAtLeast(0),
            ),
            phoneBill = PhoneBillRefreshPolicy(
                currentMonthCacheMinutes = (intValue(bill?.get(CURRENT_MONTH_CACHE_MINUTES_KEY))
                    ?: billDefaults.currentMonthCacheMinutes).coerceAtLeast(1),
                historicalCacheDays = (intValue(bill?.get(HISTORICAL_CACHE_DAYS_KEY))
                    ?: billDefaults.historicalCacheDays).coerceAtLeast(1),
                monthlyRecheckDay = (intValue(bill?.get(MONTHLY_RECHECK_DAY_KEY))
                    ?: billDefaults.monthlyRecheckDay).coerceIn(1, 28),
                monthlyRecheckHour = (intValue(bill?.get(MONTHLY_RECHECK_HOUR_KEY))
                    ?: billDefaults.monthlyRecheckHour).coerceIn(0, 23),
            ),
            integral = IntegralRefreshPolicy(
                automaticRefreshEnabled = boolValue(integral?.get(AUTOMATIC_REFRESH_ENABLED_KEY))
                    ?: integralDefaults.automaticRefreshEnabled,
                cycleMode = IntegralRefreshCycleMode.fromRawValue(stringValue(integral?.get(CYCLE_MODE_KEY)))
                    ?: integralDefaults.cycleMode,
                monthlyRefreshDay = (intValue(integral?.get(MONTHLY_REFRESH_DAY_KEY))
                    ?: integralDefaults.monthlyRefreshDay).coerceIn(1, 28),
                monthlyRefreshHour = (intValue(integral?.get(MONTHLY_REFRESH_HOUR_KEY))
                    ?: integralDefaults.monthlyRefreshHour).coerceIn(0, 23),
                fixedIntervalHours = (intValue(integral?.get(FIXED_INTERVAL_HOURS_KEY))
                    ?: integralDefaults.fixedIntervalHours).coerceAtLeast(1),
                checkOnEntry = boolValue(integral?.get(CHECK_ON_ENTRY_KEY)) ?: integralDefaults.checkOnEntry,
            ),
        )
    }

    fun mergeQuotaPolicy(existingRaw: String?, policy: QuotaRefreshPolicy): String =
        mergeDomain(existingRaw, QUOTA_KEY, quotaElement(policy))

    fun mergeBalancePolicy(existingRaw: String?, policy: BalanceRefreshPolicy): String =
        mergeDomain(existingRaw, BALANCE_KEY, balanceElement(policy))

    fun mergeOrderedBusinessPolicy(existingRaw: String?, policy: OrderedBusinessRefreshPolicy): String =
        mergeDomain(existingRaw, ORDERED_BUSINESS_KEY, orderedBusinessElement(policy))

    fun mergePhoneBillPolicy(existingRaw: String?, policy: PhoneBillRefreshPolicy): String =
        mergeDomain(existingRaw, PHONE_BILL_KEY, phoneBillElement(policy))

    fun mergeIntegralPolicy(existingRaw: String?, policy: IntegralRefreshPolicy): String =
        mergeDomain(existingRaw, INTEGRAL_KEY, integralElement(policy))

    fun mergeAll(existingRaw: String?, decoded: DecodedAppRefreshLogicPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[QUOTA_KEY] = quotaElement(decoded.quota)
        merged[BALANCE_KEY] = balanceElement(decoded.balance)
        merged[ORDERED_BUSINESS_KEY] = orderedBusinessElement(decoded.orderedBusiness)
        merged[PHONE_BILL_KEY] = phoneBillElement(decoded.phoneBill)
        merged[INTEGRAL_KEY] = integralElement(decoded.integral)
        return JsonObject(merged).toString()
    }

    private fun mergeDomain(existingRaw: String?, key: String, value: JsonObject): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION)
        merged[key] = value
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

    private fun orderedBusinessElement(policy: OrderedBusinessRefreshPolicy) = JsonObject(linkedMapOf(
        ENTRY_MODE_KEY to JsonPrimitive(policy.entryMode.rawValue),
        CACHE_VALIDITY_HOURS_KEY to JsonPrimitive(policy.cacheValidityHours.coerceAtLeast(1)),
        NO_CACHE_AUTO_QUERY_KEY to JsonPrimitive(policy.noCacheAutoQuery),
        REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY to JsonPrimitive(policy.refreshAllAccountGapSeconds.coerceAtLeast(0)),
    ))

    private fun phoneBillElement(policy: PhoneBillRefreshPolicy) = JsonObject(linkedMapOf(
        CURRENT_MONTH_CACHE_MINUTES_KEY to JsonPrimitive(policy.currentMonthCacheMinutes.coerceAtLeast(1)),
        HISTORICAL_CACHE_DAYS_KEY to JsonPrimitive(policy.historicalCacheDays.coerceAtLeast(1)),
        MONTHLY_RECHECK_DAY_KEY to JsonPrimitive(policy.monthlyRecheckDay.coerceIn(1, 28)),
        MONTHLY_RECHECK_HOUR_KEY to JsonPrimitive(policy.monthlyRecheckHour.coerceIn(0, 23)),
    ))

    private fun integralElement(policy: IntegralRefreshPolicy) = JsonObject(linkedMapOf(
        AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(policy.automaticRefreshEnabled),
        CYCLE_MODE_KEY to JsonPrimitive(policy.cycleMode.rawValue),
        MONTHLY_REFRESH_DAY_KEY to JsonPrimitive(policy.monthlyRefreshDay.coerceIn(1, 28)),
        MONTHLY_REFRESH_HOUR_KEY to JsonPrimitive(policy.monthlyRefreshHour.coerceIn(0, 23)),
        FIXED_INTERVAL_HOURS_KEY to JsonPrimitive(policy.fixedIntervalHours.coerceAtLeast(1)),
        CHECK_ON_ENTRY_KEY to JsonPrimitive(policy.checkOnEntry),
    ))

    private fun parseRoot(raw: String): JsonObject? = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    private fun boolValue(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.booleanOrNull
    private fun intValue(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull
    private fun stringValue(value: JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val STORAGE_KEY = "chinaunicom.appRefreshLogic.policy.v1"

        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val QUOTA_KEY = "quota"
        private const val BALANCE_KEY = "balance"
        private const val ORDERED_BUSINESS_KEY = "orderedBusiness"
        private const val PHONE_BILL_KEY = "phoneBill"
        private const val INTEGRAL_KEY = "integral"

        private const val AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val REFRESH_ON_COLD_LAUNCH_KEY = "refreshOnColdLaunch"
        private const val REFRESH_ON_FOREGROUND_KEY = "refreshOnForeground"
        private const val MINIMUM_INTERVAL_MINUTES_KEY = "minimumIntervalMinutes"
        private const val ACCOUNT_GAP_SECONDS_KEY = "accountGapSeconds"

        private const val BALANCE_AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"
        private const val BALANCE_CHECK_ON_FOREGROUND_KEY = "checkOnForeground"
        private const val BALANCE_INTERVAL_MINUTES_KEY = "intervalMinutes"
        private const val BALANCE_FAILURE_RETRY_MINUTES_KEY = "failureRetryMinutes"

        private const val ENTRY_MODE_KEY = "entryMode"
        private const val CACHE_VALIDITY_HOURS_KEY = "cacheValidityHours"
        private const val NO_CACHE_AUTO_QUERY_KEY = "noCacheAutoQuery"
        private const val REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY = "refreshAllAccountGapSeconds"

        private const val CURRENT_MONTH_CACHE_MINUTES_KEY = "currentMonthCacheMinutes"
        private const val HISTORICAL_CACHE_DAYS_KEY = "historicalCacheDays"
        private const val MONTHLY_RECHECK_DAY_KEY = "monthlyRecheckDay"
        private const val MONTHLY_RECHECK_HOUR_KEY = "monthlyRecheckHour"

        private const val CYCLE_MODE_KEY = "cycleMode"
        private const val MONTHLY_REFRESH_DAY_KEY = "monthlyRefreshDay"
        private const val MONTHLY_REFRESH_HOUR_KEY = "monthlyRefreshHour"
        private const val FIXED_INTERVAL_HOURS_KEY = "fixedIntervalHours"
        private const val CHECK_ON_ENTRY_KEY = "checkOnEntry"
    }
}
