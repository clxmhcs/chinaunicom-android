package com.clxmhcs.chinaunicom.widget

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

interface WidgetSnapshotStore {
    fun loadSingle(): WidgetQuotaSnapshot?
    fun loadDual(): WidgetDualSnapshot?
    @Throws(IOException::class) fun saveSingle(snapshot: WidgetQuotaSnapshot?)
    @Throws(IOException::class) fun saveDual(snapshot: WidgetDualSnapshot?)
    @Throws(IOException::class) fun clear()
}

class AndroidWidgetSnapshotStore(
    context: Context,
    private val codec: WidgetSnapshotJsonCodec = WidgetSnapshotJsonCodec(),
) : WidgetSnapshotStore {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val file = AtomicFile(File(directory, FILE_NAME))
    private val lock = Any()

    override fun loadSingle(): WidgetQuotaSnapshot? = synchronized(lock) { read().single }
    override fun loadDual(): WidgetDualSnapshot? = synchronized(lock) { read().dual }

    override fun saveSingle(snapshot: WidgetQuotaSnapshot?) = synchronized(lock) {
        val current = read()
        write(current.copy(single = snapshot))
    }

    override fun saveDual(snapshot: WidgetDualSnapshot?) = synchronized(lock) {
        val current = read()
        write(current.copy(dual = snapshot))
    }

    override fun clear() = synchronized(lock) { write(Archive()) }

    private fun read(): Archive = runCatching {
        if (!file.baseFile.exists()) return@runCatching Archive()
        codec.decode(file.readFully().decodeToString()) ?: Archive()
    }.getOrDefault(Archive())

    private fun write(archive: Archive) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create Widget snapshot directory")
        var stream: FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(codec.encode(archive).encodeToByteArray())
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(file::failWrite)
            throw IOException("Cannot persist Widget snapshot", error)
        }
    }

    data class Archive(
        val single: WidgetQuotaSnapshot? = null,
        val dual: WidgetDualSnapshot? = null,
    )

    companion object {
        private const val DIRECTORY = "widget"
        private const val FILE_NAME = "widget-snapshots-v1.json"
    }
}

class WidgetSnapshotJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(archive: AndroidWidgetSnapshotStore.Archive): String = JsonObject(
        linkedMapOf(
            "schemaVersion" to JsonPrimitive(1),
            "single" to (archive.single?.let(::singleElement) ?: JsonNull),
            "dual" to (archive.dual?.let(::dualElement) ?: JsonNull),
        ),
    ).toString()

    fun decode(raw: String): AndroidWidgetSnapshotStore.Archive? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        AndroidWidgetSnapshotStore.Archive(
            single = single(root["single"] as? JsonObject),
            dual = dual(root["dual"] as? JsonObject),
        )
    }.getOrNull()

    private fun singleElement(value: WidgetQuotaSnapshot) = JsonObject(
        linkedMapOf(
            "accountID" to nullable(value.accountID?.toString()),
            "mobile" to JsonPrimitive(value.mobile),
            "displayName" to JsonPrimitive(value.displayName),
            "packageName" to JsonPrimitive(value.packageName),
            "todayUsageGB" to JsonPrimitive(value.todayUsageGB),
            "balanceYuan" to nullable(value.balanceYuan),
            "updatedAt" to JsonPrimitive(value.updatedAt.toString()),
            "items" to JsonArray(value.items.map(::singleItemElement)),
        ),
    )

    private fun single(value: JsonObject?): WidgetQuotaSnapshot? {
        value ?: return null
        return WidgetQuotaSnapshot(
            accountID = string(value["accountID"])?.let(::uuid),
            mobile = string(value["mobile"]) ?: return null,
            displayName = string(value["displayName"]).orEmpty(),
            packageName = string(value["packageName"]).orEmpty(),
            todayUsageGB = number(value["todayUsageGB"]) ?: 0.0,
            balanceYuan = number(value["balanceYuan"]),
            updatedAt = string(value["updatedAt"])?.let(::instant) ?: return null,
            items = (value["items"] as? JsonArray).orEmpty().mapNotNull { singleItem(it as? JsonObject) },
        )
    }

    private fun singleItemElement(value: WidgetQuotaSnapshotItem) = JsonObject(
        linkedMapOf(
            "titleTop" to JsonPrimitive(value.titleTop),
            "titleBottom" to JsonPrimitive(value.titleBottom),
            "remaining" to JsonPrimitive(value.remaining),
            "total" to JsonPrimitive(value.total),
            "used" to JsonPrimitive(value.used),
            "unit" to JsonPrimitive(value.unit.rawValue),
        ),
    )

    private fun singleItem(value: JsonObject?): WidgetQuotaSnapshotItem? {
        value ?: return null
        val unitRaw = string(value["unit"]) ?: return null
        val unit = WidgetSnapshotUnit.entries.firstOrNull { it.rawValue == unitRaw } ?: return null
        return WidgetQuotaSnapshotItem(
            titleTop = string(value["titleTop"]).orEmpty(),
            titleBottom = string(value["titleBottom"]).orEmpty(),
            remaining = number(value["remaining"]) ?: 0.0,
            total = number(value["total"]) ?: 0.0,
            used = number(value["used"]) ?: 0.0,
            unit = unit,
        )
    }

    private fun dualElement(value: WidgetDualSnapshot) = JsonObject(
        linkedMapOf(
            "left" to (value.left?.let(::dualAccountElement) ?: JsonNull),
            "right" to (value.right?.let(::dualAccountElement) ?: JsonNull),
            "generatedAt" to JsonPrimitive(value.generatedAt.toString()),
        ),
    )

    private fun dual(value: JsonObject?): WidgetDualSnapshot? {
        value ?: return null
        return WidgetDualSnapshot(
            left = dualAccount(value["left"] as? JsonObject),
            right = dualAccount(value["right"] as? JsonObject),
            generatedAt = string(value["generatedAt"])?.let(::instant) ?: return null,
        )
    }

    private fun dualAccountElement(value: WidgetDualAccountSnapshot) = JsonObject(
        linkedMapOf(
            "accountID" to JsonPrimitive(value.accountID.toString()),
            "mobileSuffix" to JsonPrimitive(value.mobileSuffix),
            "todayUsageGB" to JsonPrimitive(value.todayUsageGB),
            "balanceYuan" to nullable(value.balanceYuan),
            "updatedAt" to JsonPrimitive(value.updatedAt.toString()),
            "items" to JsonArray(value.items.map(::dualItemElement)),
        ),
    )

    private fun dualAccount(value: JsonObject?): WidgetDualAccountSnapshot? {
        value ?: return null
        return WidgetDualAccountSnapshot(
            accountID = string(value["accountID"])?.let(::uuid) ?: return null,
            mobileSuffix = string(value["mobileSuffix"]).orEmpty(),
            todayUsageGB = number(value["todayUsageGB"]) ?: 0.0,
            balanceYuan = number(value["balanceYuan"]),
            updatedAt = string(value["updatedAt"])?.let(::instant) ?: return null,
            items = (value["items"] as? JsonArray).orEmpty().mapNotNull { dualItem(it as? JsonObject) },
        )
    }

    private fun dualItemElement(value: WidgetDualDashboardItem) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "title" to JsonPrimitive(value.title),
            "kind" to JsonPrimitive(value.kind.rawValue),
            "remaining" to nullable(value.remaining),
            "total" to nullable(value.total),
            "used" to nullable(value.used),
            "isUnlimited" to JsonPrimitive(value.isUnlimited),
        ),
    )

    private fun dualItem(value: JsonObject?): WidgetDualDashboardItem? {
        value ?: return null
        val kindRaw = string(value["kind"]) ?: return null
        val kind = WidgetDualSlotKind.entries.firstOrNull { it.rawValue == kindRaw } ?: return null
        return WidgetDualDashboardItem(
            id = string(value["id"]).orEmpty(),
            title = string(value["title"]).orEmpty(),
            kind = kind,
            remaining = number(value["remaining"]),
            total = number(value["total"]),
            used = number(value["used"]),
            isUnlimited = (value["isUnlimited"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false,
        )
    }

    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    private fun nullable(value: Double?): JsonElement = value?.takeIf(Double::isFinite)?.let(::JsonPrimitive) ?: JsonNull
    private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }
    private fun number(value: JsonElement?): Double? = (value as? JsonPrimitive)?.doubleOrNull?.takeIf(Double::isFinite)
    private fun uuid(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()
    private fun instant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
}
