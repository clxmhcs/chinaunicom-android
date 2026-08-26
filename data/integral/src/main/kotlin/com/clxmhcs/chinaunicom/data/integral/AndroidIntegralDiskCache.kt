package com.clxmhcs.chinaunicom.data.integral

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralMonthSummary
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshCycleMode
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val INTEGRAL_DIRECTORY = "integral"
private const val INTEGRAL_FILE = "integral-snapshots.json"

data class IntegralCacheRecord(
    val snapshot: IntegralSnapshot,
    val details: Map<String, List<IntegralDetailItem>>,
    val refreshCycleKey: String,
)

interface IntegralDiskCache {
    fun load(accountID: UUID): IntegralCacheRecord?
    fun snapshots(accountIDs: Collection<UUID>): Map<UUID, IntegralSnapshot>
    @Throws(IOException::class)
    fun save(record: IntegralCacheRecord, accountID: UUID)
    @Throws(IOException::class)
    fun clear()
}

class AndroidIntegralDiskCache(
    context: Context,
    private val codec: IntegralCacheJsonCodec = IntegralCacheJsonCodec(),
) : IntegralDiskCache {
    private val directory = File(context.applicationContext.filesDir, INTEGRAL_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, INTEGRAL_FILE))
    private val lock = Any()

    override fun load(accountID: UUID): IntegralCacheRecord? = synchronized(lock) {
        readAll()[accountID]
    }

    override fun snapshots(accountIDs: Collection<UUID>): Map<UUID, IntegralSnapshot> = synchronized(lock) {
        val requested = accountIDs.toSet()
        readAll().filterKeys(requested::contains).mapValues { it.value.snapshot }
    }

    override fun save(record: IntegralCacheRecord, accountID: UUID) = synchronized(lock) {
        val all = readAll().toMutableMap()
        all[accountID] = record
        writeAll(all)
    }

    override fun clear() = synchronized(lock) { writeAll(emptyMap()) }

    private fun readAll(): Map<UUID, IntegralCacheRecord> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyMap()
        codec.decode(atomicFile.readFully().decodeToString()) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writeAll(records: Map<UUID, IntegralCacheRecord>) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create integral cache directory")
        val bytes = codec.encode(records).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save integral cache", error)
        }
    }
}

class IntegralCacheJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(records: Map<UUID, IntegralCacheRecord>): String {
        val accounts = JsonObject(
            records.entries.associate { (accountID, record) ->
                accountID.toString() to recordElement(record)
            },
        )
        return JsonObject(mapOf("accounts" to accounts)).toString()
    }

    fun decode(raw: String): Map<UUID, IntegralCacheRecord>? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val accounts = root["accounts"] as? JsonObject ?: return null
        val result = mutableMapOf<UUID, IntegralCacheRecord>()
        for ((rawID, element) in accounts) {
            val accountID = runCatching { UUID.fromString(rawID) }.getOrNull() ?: continue
            result[accountID] = record(element as? JsonObject) ?: return null
        }
        return result
    }

    private fun recordElement(record: IntegralCacheRecord) = JsonObject(
        linkedMapOf(
            "snapshot" to snapshotElement(record.snapshot),
            "details" to JsonObject(
                record.details.mapValues { (_, items) -> JsonArray(items.map(::detailElement)) },
            ),
            "refreshCycleKey" to JsonPrimitive(record.refreshCycleKey),
        ),
    )

    private fun snapshotElement(value: IntegralSnapshot) = JsonObject(
        linkedMapOf(
            "totalAvailable" to JsonPrimitive(value.totalAvailable),
            "communication" to JsonPrimitive(value.communication),
            "reward" to JsonPrimitive(value.reward),
            "directional" to nullable(value.directional),
            "expiredAndExpiringReward" to JsonPrimitive(value.expiredAndExpiringReward),
            "expiringThisMonth" to JsonPrimitive(value.expiringThisMonth),
            "expiringCommunication" to JsonPrimitive(value.expiringCommunication),
            "expiringReward" to JsonPrimitive(value.expiringReward),
            "expirationDay" to nullable(value.expirationDay),
            "couponCount" to JsonPrimitive(value.couponCount),
            "provinceCode" to nullable(value.provinceCode),
            "packageID" to nullable(value.packageID),
            "isUnicom" to nullable(value.isUnicom),
            "months" to JsonArray(value.months.map(::monthElement)),
            "fetchedAt" to JsonPrimitive(value.fetchedAt.toString()),
            "parserVersion" to JsonPrimitive(value.parserVersion),
        ),
    )

    private fun monthElement(value: IntegralMonthSummary) = JsonObject(
        linkedMapOf(
            "cycleID" to JsonPrimitive(value.cycleID),
            "addScore" to JsonPrimitive(value.addScore),
            "consumedScore" to JsonPrimitive(value.consumedScore),
            "expiredScore" to JsonPrimitive(value.expiredScore),
        ),
    )

    private fun detailElement(value: IntegralDetailItem) = JsonObject(
        linkedMapOf(
            "typeChar" to JsonPrimitive(value.typeChar),
            "scoreType" to JsonPrimitive(value.scoreType),
            "title" to JsonPrimitive(value.title),
            "scoreValue" to JsonPrimitive(value.scoreValue),
            "createTime" to nullable(value.createTime),
            "returnTime" to nullable(value.returnTime),
            "endTime" to nullable(value.endTime),
            "orderTime" to nullable(value.orderTime),
            "channelName" to nullable(value.channelName),
            "expireTime" to nullable(value.expireTime),
            "expireTag" to nullable(value.expireTag),
        ),
    )

    private fun record(value: JsonObject?): IntegralCacheRecord? {
        value ?: return null
        val snapshot = snapshot(value["snapshot"] as? JsonObject) ?: return null
        val detailsObject = value["details"] as? JsonObject ?: return null
        val details = mutableMapOf<String, List<IntegralDetailItem>>()
        for ((key, rawItems) in detailsObject) {
            val array = rawItems as? JsonArray ?: return null
            details[key] = array.map { detail(it as? JsonObject) ?: return null }
        }
        return IntegralCacheRecord(
            snapshot = snapshot,
            details = details,
            refreshCycleKey = value.string("refreshCycleKey") ?: return null,
        )
    }

    private fun snapshot(value: JsonObject?): IntegralSnapshot? {
        value ?: return null
        val months = (value["months"] as? JsonArray)?.map { month(it as? JsonObject) ?: return null } ?: return null
        return IntegralSnapshot(
            totalAvailable = value.int("totalAvailable") ?: return null,
            communication = value.int("communication") ?: return null,
            reward = value.int("reward") ?: return null,
            directional = value.int("directional"),
            expiredAndExpiringReward = value.int("expiredAndExpiringReward") ?: return null,
            expiringThisMonth = value.int("expiringThisMonth") ?: return null,
            expiringCommunication = value.int("expiringCommunication") ?: return null,
            expiringReward = value.int("expiringReward") ?: return null,
            expirationDay = value.int("expirationDay"),
            couponCount = value.int("couponCount") ?: return null,
            provinceCode = value.string("provinceCode"),
            packageID = value.string("packageID"),
            isUnicom = value.string("isUnicom"),
            months = months,
            fetchedAt = value.string("fetchedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null,
            parserVersion = value.int("parserVersion") ?: return null,
        )
    }

    private fun month(value: JsonObject?): IntegralMonthSummary? {
        value ?: return null
        return IntegralMonthSummary(
            cycleID = value.string("cycleID") ?: return null,
            addScore = value.int("addScore") ?: return null,
            consumedScore = value.int("consumedScore") ?: return null,
            expiredScore = value.int("expiredScore") ?: return null,
        )
    }

    private fun detail(value: JsonObject?): IntegralDetailItem? {
        value ?: return null
        return IntegralDetailItem(
            typeChar = value.string("typeChar") ?: return null,
            scoreType = value.string("scoreType") ?: return null,
            title = value.string("title") ?: return null,
            scoreValue = value.string("scoreValue") ?: return null,
            createTime = value.string("createTime"),
            returnTime = value.string("returnTime"),
            endTime = value.string("endTime"),
            orderTime = value.string("orderTime"),
            channelName = value.string("channelName"),
            expireTime = value.string("expireTime"),
            expireTag = value.string("expireTag"),
        )
    }

    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    private fun nullable(value: Int?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive === JsonNull) return null
        return primitive.content
    }

    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
}

fun interface IntegralRefreshPolicyProvider {
    fun current(): IntegralRefreshPolicy
}

class SettingsIntegralRefreshPolicyProvider(
    private val settingsRepository: SettingsRepository,
) : IntegralRefreshPolicyProvider {
    override fun current(): IntegralRefreshPolicy = settingsRepository.loadIntegralRefreshPolicy()
}

class IntegralCachePolicy(
    private val policyProvider: IntegralRefreshPolicyProvider,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    fun needsAutomaticRefresh(record: IntegralCacheRecord?, at: Instant): Boolean {
        val policy = policyProvider.current()
        if (!policy.automaticRefreshEnabled || policy.cycleMode == IntegralRefreshCycleMode.MANUAL_ONLY) return false
        if (record == null || record.snapshot.parserVersion != IntegralSnapshot.CURRENT_PARSER_VERSION) return true

        return when (policy.cycleMode) {
            IntegralRefreshCycleMode.MONTHLY -> record.refreshCycleKey != refreshCycleKey(at)
            IntegralRefreshCycleMode.FIXED_INTERVAL -> {
                val hours = policy.fixedIntervalHours.coerceAtLeast(1).toLong()
                val elapsed = ChronoUnit.SECONDS.between(record.snapshot.fetchedAt, at)
                elapsed < 0 || elapsed >= hours * 60L * 60L
            }
            IntegralRefreshCycleMode.MANUAL_ONLY -> false
        }
    }

    fun refreshCycleKey(at: Instant): String {
        val policy = policyProvider.current()
        return when (policy.cycleMode) {
            IntegralRefreshCycleMode.FIXED_INTERVAL -> "fixed-${policy.fixedIntervalHours.coerceAtLeast(1)}h"
            IntegralRefreshCycleMode.MANUAL_ONLY -> "manual"
            IntegralRefreshCycleMode.MONTHLY -> monthlyCycleKey(at, policy)
        }
    }

    fun shouldAutomaticallyQueryWithoutCache(): Boolean {
        val policy = policyProvider.current()
        return policy.automaticRefreshEnabled &&
            policy.cycleMode != IntegralRefreshCycleMode.MANUAL_ONLY &&
            policy.checkOnEntry
    }

    fun shouldCheckOnEntry(): Boolean = policyProvider.current().checkOnEntry

    private fun monthlyCycleKey(at: Instant, policy: IntegralRefreshPolicy): String {
        val current = at.atZone(zoneId)
        val reset = current.withDayOfMonth(policy.monthlyRefreshDay.coerceIn(1, 28))
            .withHour(policy.monthlyRefreshHour.coerceIn(0, 23))
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        val cycleDate: ZonedDateTime = if (!current.isBefore(reset)) reset else reset.minusMonths(1)
        return "%04d%02d".format(cycleDate.year, cycleDate.monthValue)
    }
}
