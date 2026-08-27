package com.clxmhcs.chinaunicom.data.mypackage

import android.content.Context
import android.util.AtomicFile
import com.clxmhcs.chinaunicom.core.model.MyPackageActivity
import com.clxmhcs.chinaunicom.core.model.MyPackageBroadbandResource
import com.clxmhcs.chinaunicom.core.model.MyPackageChargeRule
import com.clxmhcs.chinaunicom.core.model.MyPackageMember
import com.clxmhcs.chinaunicom.core.model.MyPackageMemberGroup
import com.clxmhcs.chinaunicom.core.model.MyPackageSnapshot
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DIRECTORY = "my-package"
private const val FILE_NAME = "my-package-cache.json"
private const val SCHEMA_VERSION = 1

data class MyPackageCacheRecord(
    val schemaVersion: Int = SCHEMA_VERSION,
    val snapshot: MyPackageSnapshot,
    val fetchedAt: Instant,
) {
    val isCompatible: Boolean get() = schemaVersion == SCHEMA_VERSION
}

interface MyPackageDiskCache {
    fun load(accountID: UUID): MyPackageCacheRecord?
    @Throws(IOException::class)
    fun save(record: MyPackageCacheRecord, accountID: UUID)
}

class AndroidMyPackageDiskCache(
    context: Context,
    private val codec: MyPackageCacheJsonCodec = MyPackageCacheJsonCodec(),
) : MyPackageDiskCache {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, FILE_NAME))

    override fun load(accountID: UUID): MyPackageCacheRecord? = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching null
        codec.decode(atomicFile.readFully().decodeToString())[accountID]?.takeIf { it.isCompatible }
    }.getOrNull()

    override fun save(record: MyPackageCacheRecord, accountID: UUID) {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create my-package cache directory")
        val records = if (atomicFile.baseFile.exists()) {
            runCatching { codec.decode(atomicFile.readFully().decodeToString()) }.getOrDefault(emptyMap())
        } else emptyMap()
        val bytes = codec.encode(records + (accountID to record)).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save my-package cache", error)
        }
    }
}

class MyPackageCacheJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(records: Map<UUID, MyPackageCacheRecord>): String = JsonObject(
        records.entries.associate { (id, record) -> id.toString() to recordElement(record) },
    ).toString()

    fun decode(raw: String): Map<UUID, MyPackageCacheRecord> {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyMap()
        return root.mapNotNull { (key, value) ->
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: return@mapNotNull null
            val record = record(value as? JsonObject) ?: return@mapNotNull null
            id to record
        }.toMap()
    }

    private fun recordElement(value: MyPackageCacheRecord) = JsonObject(linkedMapOf(
        "schemaVersion" to JsonPrimitive(value.schemaVersion),
        "fetchedAt" to JsonPrimitive(value.fetchedAt.toString()),
        "snapshot" to snapshotElement(value.snapshot),
    ))

    private fun snapshotElement(v: MyPackageSnapshot) = JsonObject(linkedMapOf(
        "productName" to JsonPrimitive(v.productName),
        "productStartDate" to JsonPrimitive(v.productStartDate),
        "packageResourceType" to JsonPrimitive(v.packageResourceType),
        "monthFee" to JsonPrimitive(v.monthFee),
        "packageDescription" to JsonPrimitive(v.packageDescription),
        "businessRules" to JsonPrimitive(v.businessRules),
        "monthFeeDescription" to JsonPrimitive(v.monthFeeDescription),
        "contractTips" to JsonPrimitive(v.contractTips),
        "cannotCancelPrompt" to JsonPrimitive(v.cannotCancelPrompt),
        "promotionURL" to nullable(v.promotionURL?.toString()),
        "promotionImageURL" to nullable(v.promotionImageURL?.toString()),
        "promotionText" to JsonPrimitive(v.promotionText),
        "activities" to JsonArray(v.activities.map { activity -> JsonObject(linkedMapOf(
            "id" to JsonPrimitive(activity.id), "name" to JsonPrimitive(activity.name),
            "startDate" to JsonPrimitive(activity.startDate), "endDate" to JsonPrimitive(activity.endDate),
            "remainingDays" to JsonPrimitive(activity.remainingDays),
        )) }),
        "mobileRules" to JsonArray(v.mobileRules.map { rule -> JsonObject(linkedMapOf(
            "id" to JsonPrimitive(rule.id), "title" to JsonPrimitive(rule.title), "value" to JsonPrimitive(rule.value),
        )) }),
        "broadbandResources" to JsonArray(v.broadbandResources.map { item -> JsonObject(linkedMapOf(
            "id" to JsonPrimitive(item.id), "mobile" to JsonPrimitive(item.mobile),
            "packageSpeed" to JsonPrimitive(item.packageSpeed), "actualSpeed" to JsonPrimitive(item.actualSpeed),
            "startDate" to JsonPrimitive(item.startDate), "endDate" to JsonPrimitive(item.endDate),
        )) }),
        "broadbandTips" to JsonPrimitive(v.broadbandTips),
        "memberGroups" to JsonArray(v.memberGroups.map(::groupElement)),
        "isPrettyNumber" to JsonPrimitive(v.isPrettyNumber),
    ))

    private fun groupElement(v: MyPackageMemberGroup) = JsonObject(linkedMapOf(
        "id" to JsonPrimitive(v.id), "name" to JsonPrimitive(v.name), "groupType" to JsonPrimitive(v.groupType),
        "primaryMembers" to JsonArray(v.primaryMembers.map(::memberElement)),
        "members" to JsonArray(v.members.map(::memberElement)),
    ))

    private fun memberElement(v: MyPackageMember) = JsonObject(linkedMapOf(
        "id" to JsonPrimitive(v.id), "role" to JsonPrimitive(v.role), "serviceType" to JsonPrimitive(v.serviceType),
        "maskedNumber" to JsonPrimitive(v.maskedNumber), "userName" to JsonPrimitive(v.userName),
        "isPrimary" to JsonPrimitive(v.isPrimary),
    ))

    private fun record(o: JsonObject?): MyPackageCacheRecord? {
        o ?: return null
        val snapshot = snapshot(o["snapshot"] as? JsonObject) ?: return null
        val fetchedAt = o.string("fetchedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        return MyPackageCacheRecord(o.int("schemaVersion") ?: return null, snapshot, fetchedAt)
    }

    private fun snapshot(o: JsonObject?): MyPackageSnapshot? {
        o ?: return null
        return MyPackageSnapshot(
            productName = o.string("productName") ?: return null,
            productStartDate = o.string("productStartDate") ?: "",
            packageResourceType = o.string("packageResourceType") ?: "",
            monthFee = o.string("monthFee") ?: "",
            packageDescription = o.string("packageDescription") ?: "",
            businessRules = o.string("businessRules") ?: "",
            monthFeeDescription = o.string("monthFeeDescription") ?: "",
            contractTips = o.string("contractTips") ?: "",
            cannotCancelPrompt = o.string("cannotCancelPrompt") ?: "",
            promotionURL = o.string("promotionURL")?.let { runCatching { URI(it) }.getOrNull() },
            promotionImageURL = o.string("promotionImageURL")?.let { runCatching { URI(it) }.getOrNull() },
            promotionText = o.string("promotionText") ?: "",
            activities = o.array("activities").mapNotNull { element ->
                val value = element as? JsonObject ?: return@mapNotNull null
                MyPackageActivity(
                    value.string("id") ?: return@mapNotNull null,
                    value.string("name") ?: return@mapNotNull null,
                    value.string("startDate") ?: "",
                    value.string("endDate") ?: "",
                    value.string("remainingDays") ?: "",
                )
            },
            mobileRules = o.array("mobileRules").mapNotNull { element ->
                val value = element as? JsonObject ?: return@mapNotNull null
                MyPackageChargeRule(
                    value.string("id") ?: return@mapNotNull null,
                    value.string("title") ?: return@mapNotNull null,
                    value.string("value") ?: return@mapNotNull null,
                )
            },
            broadbandResources = o.array("broadbandResources").mapNotNull { element ->
                val value = element as? JsonObject ?: return@mapNotNull null
                MyPackageBroadbandResource(
                    value.string("id") ?: return@mapNotNull null,
                    value.string("mobile") ?: return@mapNotNull null,
                    value.string("packageSpeed") ?: return@mapNotNull null,
                    value.string("actualSpeed") ?: return@mapNotNull null,
                    value.string("startDate") ?: "",
                    value.string("endDate") ?: "",
                )
            },
            broadbandTips = o.string("broadbandTips") ?: "",
            memberGroups = o.array("memberGroups").mapNotNull { group(it as? JsonObject) },
            isPrettyNumber = o.bool("isPrettyNumber") ?: false,
        )
    }

    private fun group(o: JsonObject?): MyPackageMemberGroup? {
        o ?: return null
        return MyPackageMemberGroup(
            id = o.string("id") ?: return null,
            name = o.string("name") ?: return null,
            groupType = o.string("groupType") ?: "",
            primaryMembers = o.array("primaryMembers").mapNotNull { member(it as? JsonObject) },
            members = o.array("members").mapNotNull { member(it as? JsonObject) },
        )
    }

    private fun member(o: JsonObject?): MyPackageMember? {
        o ?: return null
        return MyPackageMember(
            o.string("id") ?: return null,
            o.string("role") ?: "",
            o.string("serviceType") ?: "",
            o.string("maskedNumber") ?: "",
            o.string("userName") ?: "",
            o.bool("isPrimary") ?: false,
        )
    }

    private fun nullable(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull
    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive === JsonNull) return null
        return primitive.content
    }
    private fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()
    private fun JsonObject.bool(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()
    private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
}
