package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

data class WidgetRefreshPolicy(
    val automaticRefreshEnabled: Boolean = true,
    val scheduledMinutes: List<Int> = WidgetDisplayConfiguration.DEFAULT_AUTOMATIC_REFRESH_MINUTES,
    val compensationMinutes: Int = 6,
    val failureRetrySeconds: Int = 30,
) {
    fun normalized(): WidgetRefreshPolicy = copy(
        scheduledMinutes = scheduledMinutes
            .filter { it in 0 until WidgetDisplayConfiguration.MINUTES_PER_DAY }
            .distinct()
            .sorted()
            .ifEmpty { WidgetDisplayConfiguration.DEFAULT_AUTOMATIC_REFRESH_MINUTES },
        compensationMinutes = compensationMinutes.coerceIn(0, 120),
        failureRetrySeconds = failureRetrySeconds.coerceIn(1, 3_600),
    )
}

data class WidgetRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: WidgetRefreshPolicy,
)

interface WidgetRefreshSettingsRepository : VideoRingSettingsRepository {
    val widgetRefreshPolicy: StateFlow<WidgetRefreshPolicy>
    fun loadWidgetRefreshPolicy(): WidgetRefreshPolicy
    fun saveWidgetRefreshPolicy(policy: WidgetRefreshPolicy): WidgetRefreshPolicySaveResult
}

/**
 * M11-C extends the existing schema-3 refresh JSON with the iOS `widget` domain.
 * This is a wrapper over the same storage key; it is not a second refresh authority.
 */
class UnifiedWidgetRefreshSettingsRepository(
    private val storage: RefreshLogicPolicyStorage,
    balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    private val widgetCodec: WidgetRefreshPolicyCodec = WidgetRefreshPolicyCodec(),
) : WidgetRefreshSettingsRepository,
    VideoRingSettingsRepository by UnifiedVideoRingSettingsRepository(storage, balanceIntervalSynchronizer) {

    private val _widgetRefreshPolicy = MutableStateFlow(loadDecoded())
    override val widgetRefreshPolicy: StateFlow<WidgetRefreshPolicy> = _widgetRefreshPolicy.asStateFlow()

    override fun loadWidgetRefreshPolicy(): WidgetRefreshPolicy {
        val policy = loadDecoded()
        _widgetRefreshPolicy.value = policy
        return policy
    }

    override fun saveWidgetRefreshPolicy(policy: WidgetRefreshPolicy): WidgetRefreshPolicySaveResult {
        val normalized = policy.normalized()
        val previous = loadDecoded()
        val persisted = storage.write(widgetCodec.merge(storage.read(), normalized))
        if (persisted) _widgetRefreshPolicy.value = normalized
        return WidgetRefreshPolicySaveResult(persisted, previous != normalized, normalized)
    }

    private fun loadDecoded(): WidgetRefreshPolicy =
        storage.read()?.let(widgetCodec::decode)?.normalized() ?: WidgetRefreshPolicy()
}

class WidgetRefreshPolicyCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): WidgetRefreshPolicy? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val domain = root[WIDGET_KEY] as? JsonObject ?: return@runCatching WidgetRefreshPolicy()
        val defaults = WidgetRefreshPolicy()
        WidgetRefreshPolicy(
            automaticRefreshEnabled = (domain[AUTOMATIC_KEY] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.automaticRefreshEnabled,
            scheduledMinutes = (domain[SCHEDULED_MINUTES_KEY] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                ?: defaults.scheduledMinutes,
            compensationMinutes = (domain[COMPENSATION_MINUTES_KEY] as? JsonPrimitive)?.intOrNull
                ?: defaults.compensationMinutes,
            failureRetrySeconds = (domain[FAILURE_RETRY_SECONDS_KEY] as? JsonPrimitive)?.intOrNull
                ?: defaults.failureRetrySeconds,
        ).normalized()
    }.getOrNull()

    fun merge(existingRaw: String?, policy: WidgetRefreshPolicy): String {
        val normalized = policy.normalized()
        val existing = existingRaw?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION)
        merged[WIDGET_KEY] = JsonObject(
            linkedMapOf(
                AUTOMATIC_KEY to JsonPrimitive(normalized.automaticRefreshEnabled),
                SCHEDULED_MINUTES_KEY to JsonArray(normalized.scheduledMinutes.map(::JsonPrimitive)),
                COMPENSATION_MINUTES_KEY to JsonPrimitive(normalized.compensationMinutes),
                FAILURE_RETRY_SECONDS_KEY to JsonPrimitive(normalized.failureRetrySeconds),
            ),
        )
        return JsonObject(merged).toString()
    }

    companion object {
        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val WIDGET_KEY = "widget"
        private const val AUTOMATIC_KEY = "automaticRefreshEnabled"
        private const val SCHEDULED_MINUTES_KEY = "scheduledMinutes"
        private const val COMPENSATION_MINUTES_KEY = "compensationMinutes"
        private const val FAILURE_RETRY_SECONDS_KEY = "failureRetrySeconds"
    }
}
