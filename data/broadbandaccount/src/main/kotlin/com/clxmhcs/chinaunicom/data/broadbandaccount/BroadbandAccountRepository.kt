package com.clxmhcs.chinaunicom.data.broadbandaccount

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DIRECTORY = "broadband-account"
private const val FILE_NAME = "broadband-accounts.json"
private const val SCHEMA_VERSION = 1

interface BroadbandAccountMetadataStore {
    fun load(): List<BroadbandAccountInfo>
    @Throws(IOException::class)
    fun save(accounts: List<BroadbandAccountInfo>)
}

interface BroadbandAccountRepository {
    fun loadAccounts(): List<BroadbandAccountInfo>
    fun upsert(account: BroadbandAccountInfo)
    fun remove(accountID: UUID)
    fun clear()
}

class DefaultBroadbandAccountRepository(
    private val store: BroadbandAccountMetadataStore,
) : BroadbandAccountRepository {
    override fun loadAccounts(): List<BroadbandAccountInfo> =
        store.load().sortedWith(
            compareByDescending<BroadbandAccountInfo> { it.updatedAt }
                .thenBy { it.serviceNumber },
        )

    override fun upsert(account: BroadbandAccountInfo) {
        val next = store.load().filterNot { it.id == account.id || it.serviceNumber == account.serviceNumber } + account
        store.save(next)
    }

    override fun remove(accountID: UUID) {
        store.save(store.load().filterNot { it.id == accountID })
    }

    override fun clear() {
        store.save(emptyList())
    }
}

class AndroidBroadbandAccountMetadataStore(
    context: Context,
    private val codec: BroadbandAccountJsonCodec = BroadbandAccountJsonCodec(),
) : BroadbandAccountMetadataStore {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val atomicFile = AtomicFile(File(directory, FILE_NAME))

    override fun load(): List<BroadbandAccountInfo> = runCatching {
        if (!atomicFile.baseFile.exists()) return@runCatching emptyList()
        codec.decode(atomicFile.readFully().decodeToString())
    }.getOrDefault(emptyList())

    override fun save(accounts: List<BroadbandAccountInfo>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Cannot create broadband-account directory")
        }
        val bytes = codec.encode(accounts).encodeToByteArray()
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            stream?.let(atomicFile::failWrite)
            throw IOException("Cannot save broadband accounts", error)
        }
    }
}

class BroadbandAccountJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(accounts: List<BroadbandAccountInfo>): String = JsonObject(
        linkedMapOf(
            "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
            "accounts" to JsonArray(accounts.map(::accountElement)),
        ),
    ).toString()

    fun decode(raw: String): List<BroadbandAccountInfo> {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val version = root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return emptyList()
        if (version != SCHEMA_VERSION) return emptyList()
        val array = runCatching { root["accounts"]?.jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> decodeAccount(runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null) }
    }

    private fun accountElement(value: BroadbandAccountInfo): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id.toString()),
            "serviceNumber" to JsonPrimitive(value.serviceNumber),
            "displayName" to JsonPrimitive(value.displayName),
            "idCardLastSix" to JsonPrimitive(value.idCardLastSix),
            "locationName" to JsonPrimitive(value.locationName),
            "provinceCode" to JsonPrimitive(value.provinceCode),
            "cityCode" to JsonPrimitive(value.cityCode),
            "areaCode" to JsonPrimitive(value.areaCode),
            "createdAt" to JsonPrimitive(value.createdAt.toString()),
            "updatedAt" to JsonPrimitive(value.updatedAt.toString()),
        ),
    )

    private fun decodeAccount(value: JsonObject): BroadbandAccountInfo? = runCatching {
        BroadbandAccountInfo(
            id = UUID.fromString(value.required("id")),
            serviceNumber = value.required("serviceNumber"),
            displayName = value.string("displayName"),
            idCardLastSix = value.string("idCardLastSix"),
            locationName = value.string("locationName"),
            provinceCode = value.string("provinceCode"),
            cityCode = value.string("cityCode"),
            areaCode = value.string("areaCode"),
            createdAt = Instant.parse(value.required("createdAt")),
            updatedAt = Instant.parse(value.required("updatedAt")),
        )
    }.getOrNull()

    private fun JsonObject.required(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: error("Missing $key")

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}
