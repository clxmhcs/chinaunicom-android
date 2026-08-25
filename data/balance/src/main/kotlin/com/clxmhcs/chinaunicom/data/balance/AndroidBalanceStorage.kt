package com.clxmhcs.chinaunicom.data.balance

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
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
import kotlinx.serialization.json.intOrNull

private const val BALANCE_DIRECTORY = "shared-balance"
private const val SHARED_STATE_FILE = "chinaunicom.balance.shared-cache.v1.json"
private const val SHARED_LOCK_FILE = "chinaunicom.balance.shared-cache.v1.lock"
private const val BALANCE_CONFIG_PREFERENCES = "chinaunicom.balance.runtime.v1"
private const val GROUPS_KEY = "chinaunicom.balanceAccountGroups.v1"
private const val HOME_ACCOUNT_KEY = "chinaunicom.homeBalanceAccountID.v1"
private const val LAST_ATTEMPT_KEY = "chinaunicom.balanceLastAttemptAt.v1"
private const val LEGACY_MIGRATION_KEY = "chinaunicom.balanceAccountGroups.legacyMigration.v1"

object AndroidSharedBalanceCacheStores {
    fun create(context: Context): SharedBalanceCacheStore = SharedBalanceCacheStore(
        storage = AndroidFileSharedBalanceStateStorage(context.applicationContext),
    )
}

internal class AndroidFileSharedBalanceStateStorage(
    context: Context,
    private val codec: SharedBalanceStateJsonCodec = SharedBalanceStateJsonCodec(),
) : SharedBalanceStateStorage {
    private val directory = File(context.filesDir, BALANCE_DIRECTORY)
    private val stateFile = AtomicFile(File(directory, SHARED_STATE_FILE))
    private val lockFile = File(directory, SHARED_LOCK_FILE)

    override fun <T> transaction(
        block: (SharedBalancePersistedState) -> SharedBalanceTransaction<T>,
    ): T? = runCatching {
        if (!directory.exists() && !directory.mkdirs()) return@runCatching null
        RandomAccessFile(lockFile, "rw").channel.use { channel ->
            channel.lock().use {
                val original = loadState()
                val transaction = block(original)
                if (transaction.state != original && !saveState(transaction.state)) {
                    return@use null
                }
                transaction.value
            }
        }
    }.getOrNull()

    private fun loadState(): SharedBalancePersistedState = runCatching {
        if (!stateFile.baseFile.exists()) return@runCatching SharedBalancePersistedState()
        codec.decode(stateFile.readFully().decodeToString()) ?: SharedBalancePersistedState()
    }.getOrDefault(SharedBalancePersistedState())

    private fun saveState(state: SharedBalancePersistedState): Boolean {
        val bytes = codec.encode(state).encodeToByteArray()
        var stream: FileOutputStream? = null
        return try {
            stream = stateFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            stateFile.finishWrite(stream)
            true
        } catch (_: Throwable) {
            stream?.let(stateFile::failWrite)
            false
        }
    }
}

internal class SharedBalanceStateJsonCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun encode(state: SharedBalancePersistedState): String = JsonObject(
        linkedMapOf(
            "schemaVersion" to JsonPrimitive(state.schemaVersion),
            "refreshIntervalMinutes" to JsonPrimitive(state.refreshIntervalMinutes),
            "scopes" to JsonObject(state.scopes.mapValues { scopeElement(it.value) }),
            "entries" to JsonObject(state.entries.mapValues { entryElement(it.value) }),
            "leases" to JsonObject(state.leases.mapValues { leaseElement(it.value) }),
        ),
    ).toString()

    fun decode(raw: String): SharedBalancePersistedState? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        SharedBalancePersistedState(
            schemaVersion = root.int("schemaVersion") ?: SharedBalanceCacheStore.CURRENT_SCHEMA_VERSION,
            refreshIntervalMinutes = root.int("refreshIntervalMinutes")
                ?: SharedBalanceCacheStore.DEFAULT_REFRESH_INTERVAL_MINUTES,
            scopes = root.objectValue("scopes")?.mapNotNull { (key, value) ->
                scope(value as? JsonObject)?.let { key to it }
            }?.toMap().orEmpty(),
            entries = root.objectValue("entries")?.mapNotNull { (key, value) ->
                entry(value as? JsonObject)?.let { key to it }
            }?.toMap().orEmpty(),
            leases = root.objectValue("leases")?.mapNotNull { (key, value) ->
                lease(value as? JsonObject)?.let { key to it }
            }?.toMap().orEmpty(),
        )
    }.getOrNull()

    private fun scopeElement(value: SharedBalanceScope) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "memberAccountIDs" to uuidArray(value.memberAccountIDs),
            "representativeAccountID" to nullableUUID(value.representativeAccountID),
        ),
    )

    private fun entryElement(value: SharedBalanceCacheEntry) = JsonObject(
        linkedMapOf(
            "scopeID" to JsonPrimitive(value.scopeID),
            "memberAccountIDs" to uuidArray(value.memberAccountIDs),
            "representativeAccountID" to nullableUUID(value.representativeAccountID),
            "balanceYuan" to JsonPrimitive(value.balanceYuan),
            "refreshedAt" to JsonPrimitive(value.refreshedAt.toString()),
            "source" to JsonPrimitive(value.source.rawValue),
        ),
    )

    private fun leaseElement(value: PersistedSharedBalanceLease) = JsonObject(
        linkedMapOf(
            "leaseID" to JsonPrimitive(value.leaseID.toString()),
            "scopeID" to JsonPrimitive(value.scopeID),
            "memberAccountIDs" to uuidArray(value.memberAccountIDs),
            "representativeAccountID" to nullableUUID(value.representativeAccountID),
            "source" to JsonPrimitive(value.source.rawValue),
            "startedAt" to JsonPrimitive(value.startedAt.toString()),
            "expiresAt" to JsonPrimitive(value.expiresAt.toString()),
        ),
    )

    private fun scope(value: JsonObject?): SharedBalanceScope? {
        value ?: return null
        val id = value.string("id") ?: return null
        val members = value.uuidList("memberAccountIDs")
        return SharedBalanceScope(id, members, value.uuid("representativeAccountID"))
    }

    private fun entry(value: JsonObject?): SharedBalanceCacheEntry? {
        value ?: return null
        val scopeID = value.string("scopeID") ?: return null
        val refreshedAt = value.instant("refreshedAt") ?: return null
        val source = value.source("source") ?: return null
        val balance = (value["balanceYuan"] as? JsonPrimitive)?.doubleOrNull ?: return null
        return SharedBalanceCacheEntry(
            scopeID = scopeID,
            memberAccountIDs = value.uuidList("memberAccountIDs"),
            representativeAccountID = value.uuid("representativeAccountID"),
            balanceYuan = balance,
            refreshedAt = refreshedAt,
            source = source,
        )
    }

    private fun lease(value: JsonObject?): PersistedSharedBalanceLease? {
        value ?: return null
        return PersistedSharedBalanceLease(
            leaseID = value.uuid("leaseID") ?: return null,
            scopeID = value.string("scopeID") ?: return null,
            memberAccountIDs = value.uuidList("memberAccountIDs"),
            representativeAccountID = value.uuid("representativeAccountID"),
            source = value.source("source") ?: return null,
            startedAt = value.instant("startedAt") ?: return null,
            expiresAt = value.instant("expiresAt") ?: return null,
        )
    }

    private fun uuidArray(values: List<UUID>) = JsonArray(values.map { JsonPrimitive(it.toString()) })
    private fun nullableUUID(value: UUID?): JsonElement = value?.let { JsonPrimitive(it.toString()) } ?: JsonNull

    private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.uuid(key: String): UUID? = string(key)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private fun JsonObject.uuidList(key: String): List<UUID> = (this[key] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonPrimitive)?.contentOrNull?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
    }
    private fun JsonObject.instant(key: String): Instant? = string(key)?.let { runCatching { Instant.parse(it) }.getOrNull() }
    private fun JsonObject.source(key: String): SharedBalanceRefreshSource? = string(key)?.let { raw ->
        SharedBalanceRefreshSource.entries.firstOrNull { it.rawValue == raw }
    }
}

class AndroidBalanceConfigurationStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : BalanceConfigurationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        BALANCE_CONFIG_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun loadGroups(): List<BalanceAccountGroup> = runCatching {
        val raw = preferences.getString(GROUPS_KEY, null) ?: return@runCatching emptyList()
        val array = json.parseToJsonElement(raw) as? JsonArray ?: return@runCatching emptyList()
        array.mapNotNull { groupFromJson(it as? JsonObject) }
    }.getOrDefault(emptyList())

    override fun saveGroups(groups: List<BalanceAccountGroup>): Boolean = preferences.edit()
        .putString(GROUPS_KEY, JsonArray(groups.map(::groupToJson)).toString())
        .commit()

    override fun loadHomeBalanceAccountID(): UUID? = preferences.getString(HOME_ACCOUNT_KEY, null)?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }

    override fun saveHomeBalanceAccountID(accountID: UUID?): Boolean {
        val editor = preferences.edit()
        if (accountID == null) editor.remove(HOME_ACCOUNT_KEY) else editor.putString(HOME_ACCOUNT_KEY, accountID.toString())
        return editor.commit()
    }

    override fun loadLastAutomaticAttemptAt(): Map<String, Instant> = runCatching {
        val raw = preferences.getString(LAST_ATTEMPT_KEY, null) ?: return@runCatching emptyMap()
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching emptyMap()
        root.mapNotNull { (key, value) ->
            val instant = (value as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }
            instant?.let { key to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    override fun saveLastAutomaticAttemptAt(value: Map<String, Instant>): Boolean = preferences.edit()
        .putString(LAST_ATTEMPT_KEY, JsonObject(value.mapValues { JsonPrimitive(it.value.toString()) }).toString())
        .commit()

    override fun legacySharedBalanceMigrationCompleted(): Boolean = preferences.getBoolean(LEGACY_MIGRATION_KEY, false)

    override fun markLegacySharedBalanceMigrationCompleted(): Boolean = preferences.edit()
        .putBoolean(LEGACY_MIGRATION_KEY, true)
        .commit()

    private fun groupToJson(group: BalanceAccountGroup) = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(group.id.toString()),
            "name" to JsonPrimitive(group.name),
            "memberAccountIDs" to JsonArray(group.memberAccountIDs.map { JsonPrimitive(it.toString()) }),
            "defaultAccountID" to (group.defaultAccountID?.let { JsonPrimitive(it.toString()) } ?: JsonNull),
        ),
    )

    private fun groupFromJson(value: JsonObject?): BalanceAccountGroup? {
        value ?: return null
        val id = (value["id"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val name = (value["name"] as? JsonPrimitive)?.contentOrNull ?: ""
        val members = (value["memberAccountIDs"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull?.let { raw -> runCatching { UUID.fromString(raw) }.getOrNull() }
        }
        val defaultID = (value["defaultAccountID"] as? JsonPrimitive)?.contentOrNull?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        return BalanceAccountGroup(id, name, members, defaultID)
    }
}
