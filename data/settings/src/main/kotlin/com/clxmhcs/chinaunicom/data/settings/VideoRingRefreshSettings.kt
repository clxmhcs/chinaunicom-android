package com.clxmhcs.chinaunicom.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

data class VideoRingRefreshPolicy(
    val entryMode: PageEntryRefreshMode = PageEntryRefreshMode.EVERY_ENTRY,
    val cacheValidityMinutes: Int = 60,
)

data class VideoRingRefreshPolicySaveResult(
    val persisted: Boolean,
    val changed: Boolean,
    val policy: VideoRingRefreshPolicy,
)

interface VideoRingSettingsRepository : RebateGiftSettingsRepository {
    val videoRingRefreshPolicy: StateFlow<VideoRingRefreshPolicy>
    fun loadVideoRingRefreshPolicy(): VideoRingRefreshPolicy
    fun saveVideoRingRefreshPolicy(policy: VideoRingRefreshPolicy): VideoRingRefreshPolicySaveResult
}

/** Extends the existing unified refresh authority with iOS schema-3 `videoRing` in the same JSON/storage key. */
class UnifiedVideoRingSettingsRepository(
    private val storage: RefreshLogicPolicyStorage,
    balanceIntervalSynchronizer: BalanceRefreshIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { true },
    private val videoRingCodec: VideoRingRefreshPolicyCodec = VideoRingRefreshPolicyCodec(),
) : VideoRingSettingsRepository,
    RebateGiftSettingsRepository by UnifiedRefreshSettingsRepository(storage, balanceIntervalSynchronizer) {

    private val _videoRingRefreshPolicy = MutableStateFlow(loadDecoded())
    override val videoRingRefreshPolicy: StateFlow<VideoRingRefreshPolicy> = _videoRingRefreshPolicy.asStateFlow()

    override fun loadVideoRingRefreshPolicy(): VideoRingRefreshPolicy {
        val policy = loadDecoded()
        _videoRingRefreshPolicy.value = policy
        return policy
    }

    override fun saveVideoRingRefreshPolicy(policy: VideoRingRefreshPolicy): VideoRingRefreshPolicySaveResult {
        val normalized = policy.copy(cacheValidityMinutes = policy.cacheValidityMinutes.coerceAtLeast(1))
        val previous = loadDecoded()
        val persisted = storage.write(videoRingCodec.merge(storage.read(), normalized))
        if (persisted) _videoRingRefreshPolicy.value = normalized
        return VideoRingRefreshPolicySaveResult(persisted, previous != normalized, normalized)
    }

    private fun loadDecoded(): VideoRingRefreshPolicy =
        storage.read()?.let(videoRingCodec::decode) ?: VideoRingRefreshPolicy()
}

class VideoRingRefreshPolicyCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): VideoRingRefreshPolicy? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val domain = root[VIDEO_RING_KEY] as? JsonObject ?: return@runCatching VideoRingRefreshPolicy()
        val defaults = VideoRingRefreshPolicy()
        VideoRingRefreshPolicy(
            entryMode = PageEntryRefreshMode.fromRawValue((domain[ENTRY_MODE_KEY] as? JsonPrimitive)?.contentOrNull)
                ?: defaults.entryMode,
            cacheValidityMinutes = ((domain[CACHE_VALIDITY_MINUTES_KEY] as? JsonPrimitive)?.intOrNull
                ?: defaults.cacheValidityMinutes).coerceAtLeast(1),
        )
    }.getOrNull()

    fun merge(existingRaw: String?, policy: VideoRingRefreshPolicy): String {
        val existing = existingRaw?.let { raw ->
            runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())
        val merged = LinkedHashMap(existing)
        merged[SCHEMA_VERSION_KEY] = JsonPrimitive(AppRefreshLogicPolicyCodec.CURRENT_SCHEMA_VERSION)
        merged[VIDEO_RING_KEY] = JsonObject(
            linkedMapOf(
                ENTRY_MODE_KEY to JsonPrimitive(policy.entryMode.rawValue),
                CACHE_VALIDITY_MINUTES_KEY to JsonPrimitive(policy.cacheValidityMinutes.coerceAtLeast(1)),
            ),
        )
        return JsonObject(merged).toString()
    }

    companion object {
        private const val SCHEMA_VERSION_KEY = "schemaVersion"
        private const val VIDEO_RING_KEY = "videoRing"
        private const val ENTRY_MODE_KEY = "entryMode"
        private const val CACHE_VALIDITY_MINUTES_KEY = "cacheValidityMinutes"
    }
}
