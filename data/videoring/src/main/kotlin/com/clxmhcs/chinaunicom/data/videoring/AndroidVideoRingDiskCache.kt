package com.clxmhcs.chinaunicom.data.videoring

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.VideoRingMember
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val VIDEO_RING_DIRECTORY = "video-ring-member"
private const val VIDEO_RING_FILE = "video-ring-member-cache.json"

data class VideoRingCacheRecord(
    val memberState: VideoRingMemberState,
    val fetchedAt: Instant,
)

interface VideoRingDiskCache {
    fun load(accountID: UUID): VideoRingCacheRecord?
    @Throws(IOException::class)
    fun save(accountID: UUID, record: VideoRingCacheRecord)
}

class AndroidVideoRingDiskCache(
    context: Context,
    private val codec: VideoRingCacheJsonCodec = VideoRingCacheJsonCodec(),
) : VideoRingDiskCache {
    private val directory = File(context.applicationContext.filesDir, VIDEO_RING_DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, VIDEO_RING_FILE))
    private val lock = Any()

    override fun load(accountID: UUID): VideoRingCacheRecord? = synchronized(lock) {
        readAll()[accountID]
    }

    override fun save(accountID: UUID, record: VideoRingCacheRecord) = synchronized(lock) {
        val all = readAll().toMutableMap()
        all[accountID] = record
        writeAll(all)
    }

    private fun readAll(): Map<UUID, VideoRingCacheRecord> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyMap()
        codec.decode(atomicFile.readFully().decodeToString()) ?: emptyMap()
    }.getOrDefault(emptyMap())

    private fun writeAll(records: Map<UUID, VideoRingCacheRecord>) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create video-ring cache directory")
        val bytes = codec.encode(records).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save video-ring cache", error)
        }
    }
}

class VideoRingCacheJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(records: Map<UUID, VideoRingCacheRecord>): String {
        val accounts = JsonObject(records.entries.associate { (accountID, record) ->
            accountID.toString().lowercase() to encodeRecord(record)
        })
        return JsonObject(linkedMapOf("schemaVersion" to JsonPrimitive(1), "accounts" to accounts)).toString()
    }

    fun decode(raw: String): Map<UUID, VideoRingCacheRecord>? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val schema = (root["schemaVersion"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        if (schema != 1) return@runCatching emptyMap()
        val accounts = root["accounts"] as? JsonObject ?: return@runCatching emptyMap()
        accounts.mapNotNull { (rawID, element) ->
            val id = runCatching { UUID.fromString(rawID) }.getOrNull() ?: return@mapNotNull null
            decodeRecord(element as? JsonObject ?: return@mapNotNull null)?.let { id to it }
        }.toMap()
    }.getOrNull()

    private fun encodeRecord(record: VideoRingCacheRecord): JsonObject = JsonObject(
        linkedMapOf(
            "phoneNumber" to JsonPrimitive(record.memberState.phoneNumber),
            "members" to JsonArray(record.memberState.members.map(::encodeMember)),
            "fetchedAt" to JsonPrimitive(record.fetchedAt.toEpochMilli()),
        ),
    )

    private fun decodeRecord(root: JsonObject): VideoRingCacheRecord? {
        val phone = (root["phoneNumber"] as? JsonPrimitive)?.contentOrNull ?: return null
        val fetchedAt = (root["fetchedAt"] as? JsonPrimitive)?.longOrNull?.let(Instant::ofEpochMilli) ?: return null
        val members = (root["members"] as? JsonArray).orEmpty().mapNotNull { element ->
            decodeMember(element as? JsonObject ?: return@mapNotNull null)
        }
        return VideoRingCacheRecord(VideoRingMemberState(phone, members), fetchedAt)
    }

    private fun encodeMember(member: VideoRingMember): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(member.id),
            "name" to JsonPrimitive(member.name),
            "memberType" to JsonPrimitive(member.memberType),
            "isMember" to JsonPrimitive(member.isMember),
            "startTime" to JsonPrimitive(member.startTime.orEmpty()),
            "endTime" to JsonPrimitive(member.endTime.orEmpty()),
        ),
    )

    private fun decodeMember(root: JsonObject): VideoRingMember? {
        val type = (root["memberType"] as? JsonPrimitive)?.contentOrNull ?: return null
        return VideoRingMember(
            id = (root["id"] as? JsonPrimitive)?.contentOrNull ?: type,
            name = (root["name"] as? JsonPrimitive)?.contentOrNull ?: type,
            memberType = type,
            isMember = (root["isMember"] as? JsonPrimitive)?.booleanOrNull ?: false,
            startTime = (root["startTime"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty),
            endTime = (root["endTime"] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotEmpty),
        )
    }
}
