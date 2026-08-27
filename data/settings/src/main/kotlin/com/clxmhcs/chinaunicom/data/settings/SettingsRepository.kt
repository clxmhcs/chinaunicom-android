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
import kotlinx.serialization.json.contentOrNull
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
    companion object { fun fromRawValue(value: String?): CachedBusinessEntryMode? = entries.firstOrNull { it.rawValue == value } }
}

enum class PageEntryRefreshMode(val rawValue: String) {
    EVERY_ENTRY("everyEntry"),
    REFRESH_WHEN_EXPIRED("refreshWhenExpired"),
    MANUAL_ONLY("manualOnly");
    companion object { fun fromRawValue(value: String?): PageEntryRefreshMode? = entries.firstOrNull { it.rawValue == value } }
}

data class OrderedBusinessRefreshPolicy(
    val entryMode: CachedBusinessEntryMode = CachedBusinessEntryMode.CACHE_PREFERRED,
    val cacheValidityHours: Int = 12,
    val noCacheAutoQuery: Boolean = false,
    val refreshAllAccountGapSeconds: Int = 1,
)

data class MyPackageRefreshPolicy(
    val entryMode: PageEntryRefreshMode = PageEntryRefreshMode.EVERY_ENTRY,
    val cacheValidityMinutes: Int = 30,
)

data class PhoneBillRefreshPolicy(
    val currentMonthCacheMinutes: Int = 10,
    val historicalCacheDays: Int = 15,
    val monthlyRecheckDay: Int = 2,
    val monthlyRecheckHour: Int = 8,
)

enum class IntegralRefreshCycleMode(val rawValue: String) {
    MONTHLY("monthly"), FIXED_INTERVAL("fixedInterval"), MANUAL_ONLY("manualOnly");
    companion object { fun fromRawValue(value: String?): IntegralRefreshCycleMode? = entries.firstOrNull { it.rawValue == value } }
}

data class IntegralRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val cycleMode: IntegralRefreshCycleMode = IntegralRefreshCycleMode.MONTHLY,
    val monthlyRefreshDay: Int = 2,
    val monthlyRefreshHour: Int = 8,
    val fixedIntervalHours: Int = 24,
    val checkOnEntry: Boolean = true,
)

data class OrderRefreshPolicy(val refreshOnEntry: Boolean = true)

fun interface BalanceRefreshIntervalSynchronizer { fun setRefreshIntervalMinutes(minutes: Int): Boolean }

data class QuotaRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: QuotaRefreshPolicy)
data class BalanceRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: BalanceRefreshPolicy)
data class OrderedBusinessRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: OrderedBusinessRefreshPolicy)
data class MyPackageRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: MyPackageRefreshPolicy)
data class PhoneBillRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: PhoneBillRefreshPolicy)
data class IntegralRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: IntegralRefreshPolicy)
data class OrderRefreshPolicySaveResult(val persisted: Boolean, val changed: Boolean, val policy: OrderRefreshPolicy)

interface SettingsRepository {
    val quotaRefreshPolicy: StateFlow<QuotaRefreshPolicy>
    val balanceRefreshPolicy: StateFlow<BalanceRefreshPolicy>
    val orderedBusinessRefreshPolicy: StateFlow<OrderedBusinessRefreshPolicy>
    val phoneBillRefreshPolicy: StateFlow<PhoneBillRefreshPolicy>
    val integralRefreshPolicy: StateFlow<IntegralRefreshPolicy>
    val orderRefreshPolicy: StateFlow<OrderRefreshPolicy>
    val myPackageRefreshPolicy: StateFlow<MyPackageRefreshPolicy>
        get() = MutableStateFlow(MyPackageRefreshPolicy()).asStateFlow()

    fun loadQuotaRefreshPolicy(): QuotaRefreshPolicy
    fun loadBalanceRefreshPolicy(): BalanceRefreshPolicy
    fun loadOrderedBusinessRefreshPolicy(): OrderedBusinessRefreshPolicy
    fun loadPhoneBillRefreshPolicy(): PhoneBillRefreshPolicy
    fun loadIntegralRefreshPolicy(): IntegralRefreshPolicy
    fun loadOrderRefreshPolicy(): OrderRefreshPolicy
    fun loadMyPackageRefreshPolicy(): MyPackageRefreshPolicy = myPackageRefreshPolicy.value

    fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult
    fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult
    fun saveOrderedBusinessRefreshPolicy(policy: OrderedBusinessRefreshPolicy): OrderedBusinessRefreshPolicySaveResult
    fun savePhoneBillRefreshPolicy(policy: PhoneBillRefreshPolicy): PhoneBillRefreshPolicySaveResult
    fun saveIntegralRefreshPolicy(policy: IntegralRefreshPolicy): IntegralRefreshPolicySaveResult
    fun saveOrderRefreshPolicy(policy: OrderRefreshPolicy): OrderRefreshPolicySaveResult
    fun saveMyPackageRefreshPolicy(policy: MyPackageRefreshPolicy): MyPackageRefreshPolicySaveResult =
        MyPackageRefreshPolicySaveResult(false, false, policy)
}

interface RefreshLogicPolicyStorage { fun read(): String?; fun write(value: String): Boolean }

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
    private val _orderRefreshPolicy = MutableStateFlow(initial.orders)
    private val _myPackageRefreshPolicy = MutableStateFlow(initial.myPackage)

    override val quotaRefreshPolicy = _quotaRefreshPolicy.asStateFlow()
    override val balanceRefreshPolicy = _balanceRefreshPolicy.asStateFlow()
    override val orderedBusinessRefreshPolicy = _orderedBusinessRefreshPolicy.asStateFlow()
    override val phoneBillRefreshPolicy = _phoneBillRefreshPolicy.asStateFlow()
    override val integralRefreshPolicy = _integralRefreshPolicy.asStateFlow()
    override val orderRefreshPolicy = _orderRefreshPolicy.asStateFlow()
    override val myPackageRefreshPolicy = _myPackageRefreshPolicy.asStateFlow()

    override fun loadQuotaRefreshPolicy() = reload().quota
    override fun loadBalanceRefreshPolicy() = reload().balance
    override fun loadOrderedBusinessRefreshPolicy() = reload().orderedBusiness
    override fun loadPhoneBillRefreshPolicy() = reload().phoneBill
    override fun loadIntegralRefreshPolicy() = reload().integral
    override fun loadOrderRefreshPolicy() = reload().orders
    override fun loadMyPackageRefreshPolicy() = reload().myPackage

    override fun saveQuotaRefreshPolicy(policy: QuotaRefreshPolicy): QuotaRefreshPolicySaveResult {
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.quota
        val persisted = storage.write(codec.mergeQuotaPolicy(previousRaw, policy)); if (persisted) _quotaRefreshPolicy.value = policy
        return QuotaRefreshPolicySaveResult(persisted, previous == null || previous != policy, policy)
    }

    override fun saveBalanceRefreshPolicy(policy: BalanceRefreshPolicy): BalanceRefreshPolicySaveResult {
        val normalized = policy.copy(intervalMinutes = policy.intervalMinutes.coerceIn(1, 24 * 60))
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.balance
        if (!balanceIntervalSynchronizer.setRefreshIntervalMinutes(normalized.intervalMinutes)) return BalanceRefreshPolicySaveResult(false, previous == null || previous != normalized, normalized)
        val persisted = storage.write(codec.mergeBalancePolicy(previousRaw, normalized)); if (persisted) _balanceRefreshPolicy.value = normalized
        return BalanceRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun saveOrderedBusinessRefreshPolicy(policy: OrderedBusinessRefreshPolicy): OrderedBusinessRefreshPolicySaveResult {
        val normalized = policy.copy(cacheValidityHours = policy.cacheValidityHours.coerceAtLeast(1), refreshAllAccountGapSeconds = policy.refreshAllAccountGapSeconds.coerceAtLeast(0))
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.orderedBusiness
        val persisted = storage.write(codec.mergeOrderedBusinessPolicy(previousRaw, normalized)); if (persisted) _orderedBusinessRefreshPolicy.value = normalized
        return OrderedBusinessRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun saveMyPackageRefreshPolicy(policy: MyPackageRefreshPolicy): MyPackageRefreshPolicySaveResult {
        val normalized = policy.copy(cacheValidityMinutes = policy.cacheValidityMinutes.coerceAtLeast(1))
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.myPackage
        val persisted = storage.write(codec.mergeMyPackagePolicy(previousRaw, normalized)); if (persisted) _myPackageRefreshPolicy.value = normalized
        return MyPackageRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun savePhoneBillRefreshPolicy(policy: PhoneBillRefreshPolicy): PhoneBillRefreshPolicySaveResult {
        val normalized = policy.copy(currentMonthCacheMinutes = policy.currentMonthCacheMinutes.coerceAtLeast(1), historicalCacheDays = policy.historicalCacheDays.coerceAtLeast(1), monthlyRecheckDay = policy.monthlyRecheckDay.coerceIn(1, 28), monthlyRecheckHour = policy.monthlyRecheckHour.coerceIn(0, 23))
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.phoneBill
        val persisted = storage.write(codec.mergePhoneBillPolicy(previousRaw, normalized)); if (persisted) _phoneBillRefreshPolicy.value = normalized
        return PhoneBillRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun saveIntegralRefreshPolicy(policy: IntegralRefreshPolicy): IntegralRefreshPolicySaveResult {
        val normalized = policy.copy(monthlyRefreshDay = policy.monthlyRefreshDay.coerceIn(1, 28), monthlyRefreshHour = policy.monthlyRefreshHour.coerceIn(0, 23), fixedIntervalHours = policy.fixedIntervalHours.coerceAtLeast(1))
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.integral
        val persisted = storage.write(codec.mergeIntegralPolicy(previousRaw, normalized)); if (persisted) _integralRefreshPolicy.value = normalized
        return IntegralRefreshPolicySaveResult(persisted, previous == null || previous != normalized, normalized)
    }

    override fun saveOrderRefreshPolicy(policy: OrderRefreshPolicy): OrderRefreshPolicySaveResult {
        val previousRaw = storage.read(); val previous = previousRaw?.let(codec::decode)?.orders
        val persisted = storage.write(codec.mergeOrderPolicy(previousRaw, policy)); if (persisted) _orderRefreshPolicy.value = policy
        return OrderRefreshPolicySaveResult(persisted, previous == null || previous != policy, policy)
    }

    private fun reload(): DecodedAppRefreshLogicPolicy {
        val value = loadFromStorage()
        _quotaRefreshPolicy.value = value.quota; _balanceRefreshPolicy.value = value.balance; _orderedBusinessRefreshPolicy.value = value.orderedBusiness
        _phoneBillRefreshPolicy.value = value.phoneBill; _integralRefreshPolicy.value = value.integral; _orderRefreshPolicy.value = value.orders; _myPackageRefreshPolicy.value = value.myPackage
        return value
    }

    private fun loadFromStorage(): DecodedAppRefreshLogicPolicy {
        val raw = storage.read(); val decoded = raw?.let(codec::decode) ?: DecodedAppRefreshLogicPolicy.defaults()
        if (raw != null && decoded.schemaVersion < AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION) storage.write(codec.mergeAll(raw, decoded))
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
    val orders: OrderRefreshPolicy,
    val myPackage: MyPackageRefreshPolicy,
) {
    companion object {
        fun defaults() = DecodedAppRefreshLogicPolicy(
            AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION, QuotaRefreshPolicy(), BalanceRefreshPolicy(), OrderedBusinessRefreshPolicy(),
            PhoneBillRefreshPolicy(), IntegralRefreshPolicy(), OrderRefreshPolicy(), MyPackageRefreshPolicy(),
        )
    }
}

class AppRefreshLogicPolicyCodec(private val json: Json = Json { ignoreUnknownKeys = true }) {
    fun decode(raw: String): DecodedAppRefreshLogicPolicy? {
        val root = parseRoot(raw) ?: return null
        val schemaVersion = intValue(root[SCHEMA_VERSION_KEY]) ?: 1
        val qd = QuotaRefreshPolicy(); val q = root[QUOTA_KEY] as? JsonObject
        val bd = BalanceRefreshPolicy(); val b = root[BALANCE_KEY] as? JsonObject
        val od = OrderedBusinessRefreshPolicy(); val o = root[ORDERED_BUSINESS_KEY] as? JsonObject
        val pd = PhoneBillRefreshPolicy(); val p = root[PHONE_BILL_KEY] as? JsonObject
        val id = IntegralRefreshPolicy(); val i = root[INTEGRAL_KEY] as? JsonObject
        val rd = OrderRefreshPolicy(); val r = root[ORDERS_KEY] as? JsonObject
        val md = MyPackageRefreshPolicy(); val m = root[MY_PACKAGE_KEY] as? JsonObject
        val decodedInterval = intValue(b?.get(BALANCE_INTERVAL_MINUTES_KEY)) ?: bd.intervalMinutes
        val migratedInterval = if (schemaVersion < 3 && decodedInterval == 15) 60 else decodedInterval
        return DecodedAppRefreshLogicPolicy(
            schemaVersion,
            QuotaRefreshPolicy(
                boolValue(q?.get(AUTOMATIC_REFRESH_ENABLED_KEY)) ?: qd.automaticRefreshEnabled,
                boolValue(q?.get(REFRESH_ON_COLD_LAUNCH_KEY)) ?: qd.refreshOnColdLaunch,
                boolValue(q?.get(REFRESH_ON_FOREGROUND_KEY)) ?: qd.refreshOnForeground,
                intValue(q?.get(MINIMUM_INTERVAL_MINUTES_KEY)) ?: qd.minimumIntervalMinutes,
                intValue(q?.get(ACCOUNT_GAP_SECONDS_KEY)) ?: qd.accountGapSeconds,
            ),
            BalanceRefreshPolicy(
                boolValue(b?.get(AUTOMATIC_REFRESH_ENABLED_KEY)) ?: bd.automaticRefreshEnabled,
                boolValue(b?.get(CHECK_ON_FOREGROUND_KEY)) ?: bd.checkOnForeground,
                migratedInterval.coerceIn(1, 24 * 60),
                intValue(b?.get(FAILURE_RETRY_MINUTES_KEY)) ?: bd.failureRetryMinutes,
            ),
            OrderedBusinessRefreshPolicy(
                CachedBusinessEntryMode.fromRawValue(stringValue(o?.get(ENTRY_MODE_KEY))) ?: od.entryMode,
                (intValue(o?.get(CACHE_VALIDITY_HOURS_KEY)) ?: od.cacheValidityHours).coerceAtLeast(1),
                boolValue(o?.get(NO_CACHE_AUTO_QUERY_KEY)) ?: od.noCacheAutoQuery,
                (intValue(o?.get(REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY)) ?: od.refreshAllAccountGapSeconds).coerceAtLeast(0),
            ),
            PhoneBillRefreshPolicy(
                (intValue(p?.get(CURRENT_MONTH_CACHE_MINUTES_KEY)) ?: pd.currentMonthCacheMinutes).coerceAtLeast(1),
                (intValue(p?.get(HISTORICAL_CACHE_DAYS_KEY)) ?: pd.historicalCacheDays).coerceAtLeast(1),
                (intValue(p?.get(MONTHLY_RECHECK_DAY_KEY)) ?: pd.monthlyRecheckDay).coerceIn(1, 28),
                (intValue(p?.get(MONTHLY_RECHECK_HOUR_KEY)) ?: pd.monthlyRecheckHour).coerceIn(0, 23),
            ),
            IntegralRefreshPolicy(
                boolValue(i?.get(AUTOMATIC_REFRESH_ENABLED_KEY)) ?: id.automaticRefreshEnabled,
                IntegralRefreshCycleMode.fromRawValue(stringValue(i?.get(CYCLE_MODE_KEY))) ?: id.cycleMode,
                (intValue(i?.get(MONTHLY_REFRESH_DAY_KEY)) ?: id.monthlyRefreshDay).coerceIn(1, 28),
                (intValue(i?.get(MONTHLY_REFRESH_HOUR_KEY)) ?: id.monthlyRefreshHour).coerceIn(0, 23),
                (intValue(i?.get(FIXED_INTERVAL_HOURS_KEY)) ?: id.fixedIntervalHours).coerceAtLeast(1),
                boolValue(i?.get(CHECK_ON_ENTRY_KEY)) ?: id.checkOnEntry,
            ),
            OrderRefreshPolicy(boolValue(r?.get(REFRESH_ON_ENTRY_KEY)) ?: rd.refreshOnEntry),
            MyPackageRefreshPolicy(
                PageEntryRefreshMode.fromRawValue(stringValue(m?.get(ENTRY_MODE_KEY))) ?: md.entryMode,
                (intValue(m?.get(CACHE_VALIDITY_MINUTES_KEY)) ?: md.cacheValidityMinutes).coerceAtLeast(1),
            ),
        )
    }

    fun mergeQuotaPolicy(existingRaw: String?, policy: QuotaRefreshPolicy) = mergeDomain(existingRaw, QUOTA_KEY, quotaElement(policy))
    fun mergeBalancePolicy(existingRaw: String?, policy: BalanceRefreshPolicy) = mergeDomain(existingRaw, BALANCE_KEY, balanceElement(policy))
    fun mergeOrderedBusinessPolicy(existingRaw: String?, policy: OrderedBusinessRefreshPolicy) = mergeDomain(existingRaw, ORDERED_BUSINESS_KEY, orderedElement(policy))
    fun mergePhoneBillPolicy(existingRaw: String?, policy: PhoneBillRefreshPolicy) = mergeDomain(existingRaw, PHONE_BILL_KEY, phoneBillElement(policy))
    fun mergeIntegralPolicy(existingRaw: String?, policy: IntegralRefreshPolicy) = mergeDomain(existingRaw, INTEGRAL_KEY, integralElement(policy))
    fun mergeOrderPolicy(existingRaw: String?, policy: OrderRefreshPolicy) = mergeDomain(existingRaw, ORDERS_KEY, orderElement(policy))
    fun mergeMyPackagePolicy(existingRaw: String?, policy: MyPackageRefreshPolicy) = mergeDomain(existingRaw, MY_PACKAGE_KEY, myPackageElement(policy))

    fun mergeAll(existingRaw: String?, decoded: DecodedAppRefreshLogicPolicy): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap()); val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION); merged[QUOTA_KEY] = quotaElement(decoded.quota); merged[BALANCE_KEY] = balanceElement(decoded.balance)
        merged[ORDERED_BUSINESS_KEY] = orderedElement(decoded.orderedBusiness); merged[PHONE_BILL_KEY] = phoneBillElement(decoded.phoneBill); merged[INTEGRAL_KEY] = integralElement(decoded.integral)
        merged[ORDERS_KEY] = orderElement(decoded.orders); merged[MY_PACKAGE_KEY] = myPackageElement(decoded.myPackage)
        return JsonObject(merged).toString()
    }

    private fun mergeDomain(existingRaw: String?, key: String, value: JsonObject): String {
        val existing = existingRaw?.let(::parseRoot) ?: JsonObject(emptyMap()); val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(CURRENT_SCHEMA_VERSION); merged[key] = value; return JsonObject(merged).toString()
    }
    private fun quotaElement(p: QuotaRefreshPolicy) = JsonObject(linkedMapOf(AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(p.automaticRefreshEnabled), REFRESH_ON_COLD_LAUNCH_KEY to JsonPrimitive(p.refreshOnColdLaunch), REFRESH_ON_FOREGROUND_KEY to JsonPrimitive(p.refreshOnForeground), MINIMUM_INTERVAL_MINUTES_KEY to JsonPrimitive(p.minimumIntervalMinutes), ACCOUNT_GAP_SECONDS_KEY to JsonPrimitive(p.accountGapSeconds)))
    private fun balanceElement(p: BalanceRefreshPolicy) = JsonObject(linkedMapOf(AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(p.automaticRefreshEnabled), CHECK_ON_FOREGROUND_KEY to JsonPrimitive(p.checkOnForeground), BALANCE_INTERVAL_MINUTES_KEY to JsonPrimitive(p.intervalMinutes.coerceIn(1, 24 * 60)), FAILURE_RETRY_MINUTES_KEY to JsonPrimitive(p.failureRetryMinutes)))
    private fun orderedElement(p: OrderedBusinessRefreshPolicy) = JsonObject(linkedMapOf(ENTRY_MODE_KEY to JsonPrimitive(p.entryMode.rawValue), CACHE_VALIDITY_HOURS_KEY to JsonPrimitive(p.cacheValidityHours.coerceAtLeast(1)), NO_CACHE_AUTO_QUERY_KEY to JsonPrimitive(p.noCacheAutoQuery), REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY to JsonPrimitive(p.refreshAllAccountGapSeconds.coerceAtLeast(0))))
    private fun myPackageElement(p: MyPackageRefreshPolicy) = JsonObject(linkedMapOf(ENTRY_MODE_KEY to JsonPrimitive(p.entryMode.rawValue), CACHE_VALIDITY_MINUTES_KEY to JsonPrimitive(p.cacheValidityMinutes.coerceAtLeast(1))))
    private fun phoneBillElement(p: PhoneBillRefreshPolicy) = JsonObject(linkedMapOf(CURRENT_MONTH_CACHE_MINUTES_KEY to JsonPrimitive(p.currentMonthCacheMinutes.coerceAtLeast(1)), HISTORICAL_CACHE_DAYS_KEY to JsonPrimitive(p.historicalCacheDays.coerceAtLeast(1)), MONTHLY_RECHECK_DAY_KEY to JsonPrimitive(p.monthlyRecheckDay.coerceIn(1, 28)), MONTHLY_RECHECK_HOUR_KEY to JsonPrimitive(p.monthlyRecheckHour.coerceIn(0, 23))))
    private fun integralElement(p: IntegralRefreshPolicy) = JsonObject(linkedMapOf(AUTOMATIC_REFRESH_ENABLED_KEY to JsonPrimitive(p.automaticRefreshEnabled), CYCLE_MODE_KEY to JsonPrimitive(p.cycleMode.rawValue), MONTHLY_REFRESH_DAY_KEY to JsonPrimitive(p.monthlyRefreshDay.coerceIn(1, 28)), MONTHLY_REFRESH_HOUR_KEY to JsonPrimitive(p.monthlyRefreshHour.coerceIn(0, 23)), FIXED_INTERVAL_HOURS_KEY to JsonPrimitive(p.fixedIntervalHours.coerceAtLeast(1)), CHECK_ON_ENTRY_KEY to JsonPrimitive(p.checkOnEntry)))
    private fun orderElement(p: OrderRefreshPolicy) = JsonObject(linkedMapOf(REFRESH_ON_ENTRY_KEY to JsonPrimitive(p.refreshOnEntry)))
    private fun parseRoot(raw: String): JsonObject? = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    private fun boolValue(v: JsonElement?) = (v as? JsonPrimitive)?.booleanOrNull
    private fun intValue(v: JsonElement?) = (v as? JsonPrimitive)?.intOrNull
    private fun stringValue(v: JsonElement?) = (v as? JsonPrimitive)?.contentOrNull

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3
        const val STORAGE_KEY = "chinaunicom.appRefreshLogic.policy.v1"
        private const val SCHEMA_VERSION_KEY = "schemaVersion"; private const val QUOTA_KEY = "quota"; private const val BALANCE_KEY = "balance"; private const val ORDERED_BUSINESS_KEY = "orderedBusiness"
        private const val MY_PACKAGE_KEY = "myPackage"; private const val PHONE_BILL_KEY = "phoneBill"; private const val INTEGRAL_KEY = "integral"; private const val ORDERS_KEY = "orders"
        private const val AUTOMATIC_REFRESH_ENABLED_KEY = "automaticRefreshEnabled"; private const val REFRESH_ON_COLD_LAUNCH_KEY = "refreshOnColdLaunch"; private const val REFRESH_ON_FOREGROUND_KEY = "refreshOnForeground"
        private const val MINIMUM_INTERVAL_MINUTES_KEY = "minimumIntervalMinutes"; private const val ACCOUNT_GAP_SECONDS_KEY = "accountGapSeconds"; private const val CHECK_ON_FOREGROUND_KEY = "checkOnForeground"
        private const val BALANCE_INTERVAL_MINUTES_KEY = "intervalMinutes"; private const val FAILURE_RETRY_MINUTES_KEY = "failureRetryMinutes"; private const val ENTRY_MODE_KEY = "entryMode"; private const val CACHE_VALIDITY_HOURS_KEY = "cacheValidityHours"
        private const val CACHE_VALIDITY_MINUTES_KEY = "cacheValidityMinutes"; private const val NO_CACHE_AUTO_QUERY_KEY = "noCacheAutoQuery"; private const val REFRESH_ALL_ACCOUNT_GAP_SECONDS_KEY = "refreshAllAccountGapSeconds"
        private const val CURRENT_MONTH_CACHE_MINUTES_KEY = "currentMonthCacheMinutes"; private const val HISTORICAL_CACHE_DAYS_KEY = "historicalCacheDays"; private const val MONTHLY_RECHECK_DAY_KEY = "monthlyRecheckDay"; private const val MONTHLY_RECHECK_HOUR_KEY = "monthlyRecheckHour"
        private const val CYCLE_MODE_KEY = "cycleMode"; private const val MONTHLY_REFRESH_DAY_KEY = "monthlyRefreshDay"; private const val MONTHLY_REFRESH_HOUR_KEY = "monthlyRefreshHour"; private const val FIXED_INTERVAL_HOURS_KEY = "fixedIntervalHours"
        private const val CHECK_ON_ENTRY_KEY = "checkOnEntry"; private const val REFRESH_ON_ENTRY_KEY = "refreshOnEntry"
    }
}
