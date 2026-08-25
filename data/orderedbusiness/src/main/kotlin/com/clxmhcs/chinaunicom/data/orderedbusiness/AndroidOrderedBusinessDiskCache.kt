package com.clxmhcs.chinaunicom.data.orderedbusiness

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessItem
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSection
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
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

private const val ORDERED_BUSINESS_DIRECTORY = "ordered-business"
private const val ORDERED_BUSINESS_FILE = "ordered-business-snapshots.json"

interface OrderedBusinessDiskCache {
    fun load(): Map<UUID, OrderedBusinessSnapshot>
    @Throws(IOException::class)
    fun save(snapshots: Map<UUID, OrderedBusinessSnapshot>)
}

class AndroidOrderedBusinessDiskCache(
    context: Context,
    private val codec: OrderedBusinessSnapshotJsonCodec = OrderedBusinessSnapshotJsonCodec(),
) : OrderedBusinessDiskCache {
    private val directory = File(context.applicationContext.filesDir, ORDERED_BUSINESS_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, ORDERED_BUSINESS_FILE))

    override fun load(): Map<UUID, OrderedBusinessSnapshot> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyMap()
        codec.decode(atomicFile.readFully().decodeToString()) ?: emptyMap()
    }.getOrDefault(emptyMap())

    override fun save(snapshots: Map<UUID, OrderedBusinessSnapshot>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create ordered-business cache directory")
        }
        val bytes = codec.encode(snapshots).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save ordered-business cache", error)
        }
    }
}

internal class OrderedBusinessSnapshotJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(snapshots: Map<UUID, OrderedBusinessSnapshot>): String = JsonObject(
        snapshots.entries.associate { (id, snapshot) -> id.toString() to snapshotElement(snapshot) },
    ).toString()

    fun decode(raw: String): Map<UUID, OrderedBusinessSnapshot>? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        buildMap {
            for ((rawID, element) in root) {
                val accountID = runCatching { UUID.fromString(rawID) }.getOrNull() ?: continue
                val snapshot = snapshot(element as? JsonObject) ?: return@runCatching null
                put(accountID, snapshot)
            }
        }
    }.getOrNull()

    private fun snapshotElement(value: OrderedBusinessSnapshot) = JsonObject(
        linkedMapOf(
            "title" to nullable(value.title),
            "queryTime" to nullable(value.queryTime),
            "fetchedAt" to JsonPrimitive(value.fetchedAt.toString()),
            "sections" to JsonArray(value.sections.map(::sectionElement)),
        ),
    )

    private fun sectionElement(value: OrderedBusinessSection) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "title" to JsonPrimitive(value.title),
            "icon" to JsonPrimitive(value.icon),
            "items" to JsonArray(value.items.map(::itemElement)),
        ),
    )

    private fun itemElement(value: OrderedBusinessItem) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "name" to JsonPrimitive(value.name),
            "subtitle" to nullable(value.subtitle),
            "fee" to nullable(value.fee),
            "startDate" to nullable(value.startDate),
            "endDate" to nullable(value.endDate),
        ),
    )

    private fun snapshot(value: JsonObject?): OrderedBusinessSnapshot? {
        value ?: return null
        val fetchedAt = value.string("fetchedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val sections = (value["sections"] as? JsonArray)?.map { section(it as? JsonObject) ?: return null } ?: return null
        return OrderedBusinessSnapshot(
            title = value.string("title"),
            queryTime = value.string("queryTime"),
            fetchedAt = fetchedAt,
            sections = sections,
        )
    }

    private fun section(value: JsonObject?): OrderedBusinessSection? {
        value ?: return null
        val items = (value["items"] as? JsonArray)?.map { item(it as? JsonObject) ?: return null } ?: return null
        return OrderedBusinessSection(
            id = value.string("id") ?: return null,
            title = value.string("title") ?: return null,
            icon = value.string("icon") ?: return null,
            items = items,
        )
    }

    private fun item(value: JsonObject?): OrderedBusinessItem? {
        value ?: return null
        return OrderedBusinessItem(
            id = value.string("id") ?: return null,
            name = value.string("name") ?: return null,
            subtitle = value.string("subtitle"),
            fee = value.string("fee"),
            startDate = value.string("startDate"),
            endDate = value.string("endDate"),
        )
    }

    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull

    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive === JsonNull) return null
        return primitive.content
    }
}
