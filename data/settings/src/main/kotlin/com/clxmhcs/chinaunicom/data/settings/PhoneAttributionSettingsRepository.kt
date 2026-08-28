package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.PhoneCarrier
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.PhoneSegmentAttributionRecord
import com.clxmhcs.chinaunicom.core.model.PhoneSegmentUpdateResult
import com.clxmhcs.chinaunicom.core.network.PhoneAttributionClient
import com.clxmhcs.chinaunicom.core.network.PhoneCarrierSegmentFetchResult
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhoneAttributionSettingsState(
    val corrections: Map<String, PhoneCarrierCorrection> = emptyMap(),
    val segments: List<PhoneSegmentAttributionRecord> = emptyList(),
    val isUpdatingSegments: Boolean = false,
    val updateMessage: String? = null,
)

interface PhoneAttributionSettingsStorage {
    fun loadCorrections(): Map<String, PhoneCarrierCorrection>
    fun saveCorrections(value: Map<String, PhoneCarrierCorrection>): Boolean
    fun loadSegments(): Map<String, PhoneSegmentAttributionRecord>
    fun saveSegments(value: Map<String, PhoneSegmentAttributionRecord>): Boolean
}

interface PhoneAttributionSettingsRepository {
    val state: StateFlow<PhoneAttributionSettingsState>
    fun correction(number: String): PhoneCarrierCorrection
    fun setCorrection(number: String, correction: PhoneCarrierCorrection): Boolean
    fun resetCorrections(): Boolean
    fun correctedCount(numbers: Collection<String>): Int
    fun automaticCarrier(number: String): PhoneCarrier
    fun carrier(number: String): PhoneCarrier
    fun cachedLocation(number: String): String?
    suspend fun refreshLocation(number: String): PhoneSegmentAttributionRecord?
    suspend fun updateCachedSegments(): PhoneSegmentUpdateResult
}

class DefaultPhoneAttributionSettingsRepository(
    private val storage: PhoneAttributionSettingsStorage,
    private val client: PhoneAttributionClient,
    private val clock: Clock = Clock.systemUTC(),
) : PhoneAttributionSettingsRepository {
    private val _state = MutableStateFlow(snapshot())
    override val state: StateFlow<PhoneAttributionSettingsState> = _state.asStateFlow()

    override fun correction(number: String): PhoneCarrierCorrection {
        val key = normalizedNumberKey(number) ?: return PhoneCarrierCorrection.AUTOMATIC
        return storage.loadCorrections()[key] ?: PhoneCarrierCorrection.AUTOMATIC
    }

    override fun setCorrection(number: String, correction: PhoneCarrierCorrection): Boolean {
        val key = normalizedNumberKey(number) ?: return false
        val values = storage.loadCorrections().toMutableMap()
        if (correction == PhoneCarrierCorrection.AUTOMATIC) values.remove(key) else values[key] = correction
        val persisted = storage.saveCorrections(values)
        if (persisted) publish()
        return persisted
    }

    override fun resetCorrections(): Boolean {
        val persisted = storage.saveCorrections(emptyMap())
        if (persisted) publish()
        return persisted
    }

    override fun correctedCount(numbers: Collection<String>): Int {
        val keys = numbers.mapNotNull(::normalizedNumberKey).toSet()
        if (keys.isEmpty()) return 0
        return storage.loadCorrections().keys.count(keys::contains)
    }

    override fun automaticCarrier(number: String): PhoneCarrier {
        val key = carrierSegmentKey(number)
        val cached = key?.let { storage.loadSegments()[it]?.carrier }
        return cached?.takeIf { it != PhoneCarrier.UNKNOWN } ?: localCarrier(number)
    }

    override fun carrier(number: String): PhoneCarrier = correction(number).carrier ?: automaticCarrier(number)

    override fun cachedLocation(number: String): String? {
        val key = attributionPrefix(number) ?: return null
        return storage.loadSegments()[key]?.location?.trim()?.takeIf(String::isNotEmpty)
    }

    override suspend fun refreshLocation(number: String): PhoneSegmentAttributionRecord? {
        val key = attributionPrefix(number) ?: return null
        val result = client.fetchAttribution(key) ?: return null
        val automatic = if (result.carrier == PhoneCarrier.UNKNOWN) localCarrier(number) else result.carrier
        if (result.location == null && automatic == PhoneCarrier.UNKNOWN) return null
        val record = PhoneSegmentAttributionRecord(
            prefix = key,
            location = result.location,
            carrier = automatic,
            updatedAt = Instant.now(clock),
        )
        val values = storage.loadSegments().toMutableMap()
        values[key] = record
        carrierSegmentKey(number)?.let { carrierKey ->
            val existing = values[carrierKey]
            values[carrierKey] = PhoneSegmentAttributionRecord(
                prefix = carrierKey,
                location = existing?.location,
                carrier = localCarrier(number).takeIf { it != PhoneCarrier.UNKNOWN } ?: automatic,
                updatedAt = existing?.updatedAt ?: record.updatedAt,
            )
        }
        if (storage.saveSegments(values)) publish()
        return record
    }

    override suspend fun updateCachedSegments(): PhoneSegmentUpdateResult {
        _state.update { it.copy(isUpdatingSegments = true, updateMessage = null) }
        val values = storage.loadSegments().toMutableMap()
        var updated = 0
        var failed = 0
        for (value in 100..199) {
            val prefix = value.toString()
            val now = Instant.now(clock)
            when (val result = client.fetchCarrierSegment(prefix)) {
                is PhoneCarrierSegmentFetchResult.Matched -> {
                    values[prefix] = PhoneSegmentAttributionRecord(prefix, null, result.carrier, now)
                    updated += 1
                }
                PhoneCarrierSegmentFetchResult.NoMatch -> {
                    val local = LOCAL_CARRIER_SEGMENTS[prefix]
                    if (local != null) {
                        values[prefix] = PhoneSegmentAttributionRecord(prefix, null, local, now)
                        updated += 1
                    } else {
                        values.remove(prefix)
                    }
                }
                PhoneCarrierSegmentFetchResult.Failed -> {
                    val local = LOCAL_CARRIER_SEGMENTS[prefix]
                    if (local != null) {
                        values[prefix] = PhoneSegmentAttributionRecord(prefix, null, local, now)
                        updated += 1
                    } else {
                        failed += 1
                    }
                }
            }
        }
        storage.saveSegments(values)
        val result = PhoneSegmentUpdateResult(updated, failed)
        _state.value = snapshot().copy(
            isUpdatingSegments = false,
            updateMessage = when {
                result.totalCount == 0 -> "未获取到可保存号段。"
                result.failedCount == 0 -> "已更新 ${result.updatedCount} 个号段。"
                else -> "已更新 ${result.updatedCount} 个号段，${result.failedCount} 个失败。"
            },
        )
        return result
    }

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot(): PhoneAttributionSettingsState = PhoneAttributionSettingsState(
        corrections = storage.loadCorrections(),
        segments = storage.loadSegments().values
            .filter { it.prefix.length == 3 && it.carrier != PhoneCarrier.UNKNOWN }
            .sortedWith(compareBy<PhoneSegmentAttributionRecord> { it.prefix }.thenByDescending { it.updatedAt }),
    )

    companion object {
        fun normalizedNumberKey(number: String): String? = number.filter(Char::isDigit).takeIf(String::isNotEmpty)
        fun attributionPrefix(number: String): String? = number.filter(Char::isDigit).takeIf { it.length >= 7 }?.take(7)
        fun carrierSegmentKey(number: String): String? = number.filter(Char::isDigit).takeIf { it.length >= 3 }?.take(3)

        fun localCarrier(number: String): PhoneCarrier = carrierSegmentKey(number)
            ?.let(LOCAL_CARRIER_SEGMENTS::get)
            ?: PhoneCarrier.UNKNOWN

        val SHANDONG_BROADBAND_AREA_CODES: List<Pair<String, String>> = listOf(
            "菏泽" to "0530", "济南" to "0531", "青岛" to "0532", "淄博" to "0533",
            "德州" to "0534", "烟台" to "0535", "潍坊" to "0536", "济宁" to "0537",
            "泰安" to "0538", "临沂" to "0539", "滨州" to "0543", "东营" to "0546",
            "威海" to "0631", "枣庄" to "0632", "日照" to "0633", "聊城" to "0635",
        )

        private val LOCAL_CARRIER_SEGMENTS: Map<String, PhoneCarrier> = buildMap {
            listOf("130", "131", "132", "145", "146", "155", "156", "166", "167", "171", "175", "176", "185", "186", "196")
                .forEach { put(it, PhoneCarrier.CHINA_UNICOM) }
            listOf("134", "135", "136", "137", "138", "139", "147", "148", "150", "151", "152", "157", "158", "159", "165", "172", "178", "182", "183", "184", "187", "188", "195", "197", "198")
                .forEach { put(it, PhoneCarrier.CHINA_MOBILE) }
            listOf("133", "149", "153", "162", "173", "174", "177", "180", "181", "189", "190", "191", "193", "199")
                .forEach { put(it, PhoneCarrier.CHINA_TELECOM) }
            put("192", PhoneCarrier.CHINA_BROADNET)
        }
    }
}
