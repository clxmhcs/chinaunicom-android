package com.clxmhcs.chinaunicom.data.refresh

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.CarryForwardScope
import com.clxmhcs.chinaunicom.core.model.DailyUsageBaseline
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.ShareScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.max
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

interface DailyUsageBaselineStore {
    fun load(accountID: UUID, dateKey: String): DailyUsageBaseline?
    fun save(value: DailyUsageBaseline): Boolean
    fun delete(accountID: UUID, dateKey: String): Boolean
    fun deleteAccount(accountID: UUID): Boolean
    fun loadTodayUsageMB(accountID: UUID, dateKey: String): Double?
    fun recordTodayUsageMB(accountID: UUID, dateKey: String, usedMB: Double): Double
}

class AndroidDailyUsageBaselineStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : DailyUsageBaselineStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(accountID: UUID, dateKey: String): DailyUsageBaseline? {
        val key = baselineKey(accountID, normalizedDateKey(dateKey) ?: return null)
        val raw = preferences.getString(key, null) ?: return null
        return decode(raw)?.takeIf { it.accountID == accountID && it.dateKey == dateKey }
    }

    override fun save(value: DailyUsageBaseline): Boolean {
        val dateKey = normalizedDateKey(value.dateKey) ?: return false
        val normalized = value.copy(
            schemaVersion = DailyUsageBaseline.CURRENT_SCHEMA_VERSION,
            dateKey = dateKey,
        )
        return preferences.edit()
            .putString(baselineKey(value.accountID, dateKey), encode(normalized))
            .remove(todayUsageKey(value.accountID, dateKey))
            .commit()
    }

    override fun delete(accountID: UUID, dateKey: String): Boolean {
        val normalized = normalizedDateKey(dateKey) ?: return false
        return preferences.edit()
            .remove(baselineKey(accountID, normalized))
            .remove(todayUsageKey(accountID, normalized))
            .commit()
    }

    override fun deleteAccount(accountID: UUID): Boolean {
        val accountToken = accountID.toString().lowercase()
        val editor = preferences.edit()
        preferences.all.keys
            .filter { it.startsWith(BASELINE_PREFIX + accountToken + ".") || it.startsWith(TODAY_USAGE_PREFIX + accountToken + ".") }
            .forEach(editor::remove)
        return editor.commit()
    }

    override fun loadTodayUsageMB(accountID: UUID, dateKey: String): Double? {
        val normalized = normalizedDateKey(dateKey) ?: return null
        val bits = preferences.getLong(todayUsageKey(accountID, normalized), Long.MIN_VALUE)
        if (bits == Long.MIN_VALUE) return null
        return Double.fromBits(bits).takeIf { it.isFinite() && it >= 0.0 }
    }

    override fun recordTodayUsageMB(accountID: UUID, dateKey: String, usedMB: Double): Double {
        val normalized = normalizedDateKey(dateKey) ?: return 0.0
        val candidate = usedMB.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val previous = loadTodayUsageMB(accountID, normalized) ?: 0.0
        val resolved = max(previous, candidate)
        preferences.edit().putLong(todayUsageKey(accountID, normalized), resolved.toBits()).commit()
        return resolved
    }

    private fun encode(value: DailyUsageBaseline): String = JsonObject(
        linkedMapOf(
            "schemaVersion" to JsonPrimitive(value.schemaVersion),
            "accountID" to JsonPrimitive(value.accountID.toString()),
            "dateKey" to JsonPrimitive(value.dateKey),
            "capturedAt" to JsonPrimitive(value.capturedAt.toString()),
            "packages" to JsonArray(value.packages.map(::encodePackage)),
        ),
    ).toString()

    private fun decode(raw: String): DailyUsageBaseline? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val schema = integer(root["schemaVersion"]) ?: 1
        if (schema > DailyUsageBaseline.CURRENT_SCHEMA_VERSION) return@runCatching null
        val accountID = string(root["accountID"])?.let(UUID::fromString) ?: return@runCatching null
        val dateKey = normalizedDateKey(string(root["dateKey"]) ?: return@runCatching null) ?: return@runCatching null
        val capturedAt = string(root["capturedAt"])?.let(Instant::parse) ?: return@runCatching null
        val packages = (root["packages"] as? JsonArray).orEmpty().mapNotNull(::decodePackage)
        DailyUsageBaseline(
            accountID = accountID,
            dateKey = dateKey,
            capturedAt = capturedAt,
            packages = packages,
        )
    }.getOrNull()

    private fun encodePackage(value: FlowPackage): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "originalName" to JsonPrimitive(value.originalName),
            "totalMB" to nullableNumber(value.totalMB),
            "usedMB" to nullableNumber(value.usedMB),
            "remainingMB" to nullableNumber(value.remainingMB),
            "detectedQuotaType" to JsonPrimitive(value.detectedQuotaType.rawValue),
            "detectedCategory" to JsonPrimitive(value.detectedCategory.rawValue),
            "isShared" to JsonPrimitive(value.isShared),
            "shareScope" to nullableString(value.shareScope?.rawValue),
            "carryForwardScope" to nullableString(value.carryForwardScope?.rawValue),
            "currentMonthTotalMB" to nullableNumber(value.currentMonthTotalMB),
            "carryForwardTotalMB" to nullableNumber(value.carryForwardTotalMB),
            "endDateText" to nullableString(value.endDateText),
            "rawType" to nullableString(value.rawType),
            "rawCode" to nullableString(value.rawCode),
        ),
    )

    private fun decodePackage(element: JsonElement): FlowPackage? {
        val row = element as? JsonObject ?: return null
        val id = string(row["id"]) ?: return null
        val originalName = string(row["originalName"]) ?: return null
        val quotaType = string(row["detectedQuotaType"])
            ?.let { raw -> QuotaType.entries.firstOrNull { it.rawValue == raw } }
            ?: QuotaType.AUTOMATIC
        val category = string(row["detectedCategory"])
            ?.let { raw -> PackageCategory.entries.firstOrNull { it.rawValue == raw } }
            ?: PackageCategory.AUTOMATIC
        return FlowPackage(
            id = id,
            originalName = originalName,
            totalMB = number(row["totalMB"]),
            usedMB = number(row["usedMB"]),
            remainingMB = number(row["remainingMB"]),
            detectedQuotaType = quotaType,
            detectedCategory = category,
            isShared = boolean(row["isShared"]) ?: false,
            shareScope = string(row["shareScope"])?.let { raw -> ShareScope.entries.firstOrNull { it.rawValue == raw } },
            carryForwardScope = string(row["carryForwardScope"])?.let { raw -> CarryForwardScope.entries.firstOrNull { it.rawValue == raw } },
            currentMonthTotalMB = number(row["currentMonthTotalMB"]),
            carryForwardTotalMB = number(row["carryForwardTotalMB"]),
            endDateText = string(row["endDateText"]),
            rawType = string(row["rawType"]),
            rawCode = string(row["rawCode"]),
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "chinaunicom.dailyUsageBaseline.v1"
        private const val BASELINE_PREFIX = "chinaunicom.dailyUsageBaseline.v1."
        private const val TODAY_USAGE_PREFIX = "chinaunicom.dailyUsageBaseline.todayUsage.v1."

        fun todayDateKey(zoneID: ZoneId = ZoneId.systemDefault()): String = LocalDate.now(zoneID).toString()

        fun baselineKey(accountID: UUID, dateKey: String): String =
            BASELINE_PREFIX + accountID.toString().lowercase() + "." + dateKey

        fun todayUsageKey(accountID: UUID, dateKey: String): String =
            TODAY_USAGE_PREFIX + accountID.toString().lowercase() + "." + dateKey

        private fun normalizedDateKey(value: String): String? = runCatching { LocalDate.parse(value).toString() }.getOrNull()
        private fun nullableString(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
        private fun nullableNumber(value: Double?): JsonElement = value?.takeIf(Double::isFinite)?.let(::JsonPrimitive) ?: JsonNull
        private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }
        private fun integer(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull
        private fun number(value: JsonElement?): Double? = (value as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
        private fun boolean(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.booleanOrNull
    }
}
