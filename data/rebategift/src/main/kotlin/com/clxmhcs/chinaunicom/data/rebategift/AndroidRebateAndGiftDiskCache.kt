package com.clxmhcs.chinaunicom.data.rebategift

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.GiftRecord
import com.clxmhcs.chinaunicom.core.model.RebateContract
import com.clxmhcs.chinaunicom.core.model.RebateReturnDetail
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val REBATE_GIFT_DIRECTORY = "rebate-gift"
private const val REBATE_GIFT_FILE = "rebate-gift-cache.json"

data class RebateAndGiftCacheRecord(
    val contractsByScope: Map<String, List<RebateContract>> = emptyMap(),
    val gifts: List<GiftRecord> = emptyList(),
    val queryTimesByScope: Map<String, Instant> = emptyMap(),
    val giftQueryTime: Instant? = null,
    val lastManualRefreshAt: Instant? = null,
    val automaticRefreshMonth: String? = null,
)

interface RebateAndGiftDiskCache {
    fun load(accountID: UUID): RebateAndGiftCacheRecord?
    @Throws(IOException::class)
    fun save(accountID: UUID, record: RebateAndGiftCacheRecord)
    @Throws(IOException::class)
    fun clear()
}

class AndroidRebateAndGiftDiskCache(
    context: Context,
    private val codec: RebateAndGiftCacheJsonCodec = RebateAndGiftCacheJsonCodec(),
) : RebateAndGiftDiskCache {
    private val directory = File(context.applicationContext.filesDir, REBATE_GIFT_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, REBATE_GIFT_FILE))
    private val lock = Any()

    override fun load(accountID: UUID): RebateAndGiftCacheRecord? = synchronized(lock) {
        readAll()[accountID]
    }

    override fun save(accountID: UUID, record: RebateAndGiftCacheRecord) = synchronized(lock) {
        val all = readAll().toMutableMap()
        all[accountID] = record
        writeAll(all)
    }

    override fun clear() = synchronized(lock) { writeAll(emptyMap()) }

    private fun readAll(): Map<UUID, RebateAndGiftCacheRecord> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyMap()
        codec.decode(atomicFile.readFully().decodeToString()) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writeAll(records: Map<UUID, RebateAndGiftCacheRecord>) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create rebate-gift cache directory")
        val bytes = codec.encode(records).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save rebate-gift cache", error)
        }
    }
}

class RebateAndGiftCacheJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(records: Map<UUID, RebateAndGiftCacheRecord>): String {
        val accounts = JsonObject(records.entries.associate { (accountID, record) ->
            accountID.toString().lowercase() to encodeRecord(record)
        })
        return JsonObject(
            linkedMapOf(
                "schemaVersion" to JsonPrimitive(1),
                "accounts" to accounts,
            ),
        ).toString()
    }

    fun decode(raw: String): Map<UUID, RebateAndGiftCacheRecord>? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val accounts = root["accounts"] as? JsonObject ?: return@runCatching emptyMap()
        accounts.mapNotNull { (rawID, element) ->
            val accountID = runCatching { UUID.fromString(rawID) }.getOrNull() ?: return@mapNotNull null
            val record = decodeRecord(element as? JsonObject ?: return@mapNotNull null)
            accountID to record
        }.toMap()
    }.getOrNull()

    private fun encodeRecord(record: RebateAndGiftCacheRecord): JsonObject = JsonObject(
        linkedMapOf(
            "contractsByScope" to JsonObject(record.contractsByScope.mapValues { (_, items) -> JsonArray(items.map(::encodeContract)) }),
            "gifts" to JsonArray(record.gifts.map(::encodeGift)),
            "queryTimesByScope" to JsonObject(record.queryTimesByScope.mapValues { (_, value) -> JsonPrimitive(value.toEpochMilli()) }),
            "giftQueryTime" to nullableInstant(record.giftQueryTime),
            "lastManualRefreshAt" to nullableInstant(record.lastManualRefreshAt),
            "automaticRefreshMonth" to JsonPrimitive(record.automaticRefreshMonth.orEmpty()),
        ),
    )

    private fun decodeRecord(root: JsonObject): RebateAndGiftCacheRecord {
        val contracts = (root["contractsByScope"] as? JsonObject).orEmpty().mapValues { (_, value) ->
            (value as? JsonArray).orEmpty().mapNotNull { decodeContract(it as? JsonObject ?: return@mapNotNull null) }
        }
        val gifts = (root["gifts"] as? JsonArray).orEmpty().mapNotNull { decodeGift(it as? JsonObject ?: return@mapNotNull null) }
        val queryTimes = (root["queryTimesByScope"] as? JsonObject).orEmpty().mapNotNull { (scope, value) ->
            instant(value)?.let { scope to it }
        }.toMap()
        return RebateAndGiftCacheRecord(
            contractsByScope = contracts,
            gifts = gifts,
            queryTimesByScope = queryTimes,
            giftQueryTime = instant(root["giftQueryTime"]),
            lastManualRefreshAt = instant(root["lastManualRefreshAt"]),
            automaticRefreshMonth = primitive(root["automaticRefreshMonth"])?.takeIf(String::isNotEmpty),
        )
    }

    private fun encodeContract(value: RebateContract): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "activityName" to JsonPrimitive(value.activityName),
            "returnedAmount" to JsonPrimitive(value.returnedAmount),
            "totalAmount" to JsonPrimitive(value.totalAmount),
            "frozenAmount" to JsonPrimitive(value.frozenAmount),
            "mobile" to JsonPrimitive(value.mobile),
            "startDate" to JsonPrimitive(value.startDate),
            "endDate" to JsonPrimitive(value.endDate),
            "detail" to JsonArray(value.detail.map(::encodeDetail)),
        ),
    )

    private fun decodeContract(root: JsonObject): RebateContract? {
        val id = primitive(root["id"]) ?: return null
        return RebateContract(
            id = id,
            activityName = primitive(root["activityName"]).orEmpty(),
            returnedAmount = primitive(root["returnedAmount"]) ?: "0.00",
            totalAmount = primitive(root["totalAmount"]) ?: "0.00",
            frozenAmount = primitive(root["frozenAmount"]) ?: "0.00",
            mobile = primitive(root["mobile"]).orEmpty(),
            startDate = primitive(root["startDate"]).orEmpty(),
            endDate = primitive(root["endDate"]).orEmpty(),
            detail = (root["detail"] as? JsonArray).orEmpty().mapNotNull { decodeDetail(it as? JsonObject ?: return@mapNotNull null) },
        )
    }

    private fun encodeDetail(value: RebateReturnDetail): JsonObject = JsonObject(
        linkedMapOf(
            "freeMoney" to JsonPrimitive(value.freeMoney),
            "giftMoney" to JsonPrimitive(value.giftMoney),
            "date" to JsonPrimitive(value.date),
        ),
    )

    private fun decodeDetail(root: JsonObject): RebateReturnDetail = RebateReturnDetail(
        freeMoney = primitive(root["freeMoney"]) ?: "0.00",
        giftMoney = primitive(root["giftMoney"]) ?: "0.00",
        date = primitive(root["date"]).orEmpty(),
    )

    private fun encodeGift(value: GiftRecord): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "name" to JsonPrimitive(value.name),
            "amount" to JsonPrimitive(value.amount),
            "mobile" to JsonPrimitive(value.mobile),
            "date" to JsonPrimitive(value.date),
            "description" to JsonPrimitive(value.description),
        ),
    )

    private fun decodeGift(root: JsonObject): GiftRecord? {
        val id = primitive(root["id"]) ?: return null
        return GiftRecord(
            id = id,
            name = primitive(root["name"]).orEmpty(),
            amount = primitive(root["amount"]) ?: "0.00",
            mobile = primitive(root["mobile"]).orEmpty(),
            date = primitive(root["date"]).orEmpty(),
            description = primitive(root["description"]).orEmpty(),
        )
    }

    private fun nullableInstant(value: Instant?): JsonElement = JsonPrimitive(value?.toEpochMilli()?.toString().orEmpty())

    private fun instant(value: JsonElement?): Instant? = primitive(value)?.toLongOrNull()?.let(Instant::ofEpochMilli)

    private fun primitive(value: JsonElement?): String? = (value as? JsonPrimitive)?.content?.trim()
}
