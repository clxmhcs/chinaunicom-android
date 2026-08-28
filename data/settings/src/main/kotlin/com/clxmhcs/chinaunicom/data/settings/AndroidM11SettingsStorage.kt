package com.clxmhcs.chinaunicom.data.settings

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.PhoneCarrier
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.PhoneSegmentAttributionRecord
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationProfile
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationSlot
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationTemplateSettings
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualSlotKind
import com.clxmhcs.chinaunicom.core.model.WidgetQuotaResourceKind
import com.clxmhcs.chinaunicom.core.model.WidgetQuotaSlotConfiguration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

private const val M11_SETTINGS_PREFERENCES = "chinaunicom.m11.settings.v1"
private const val CARRIER_CORRECTIONS_KEY = "chinaunicom.carrierCorrections.v1"
private const val PHONE_ATTRIBUTION_CACHE_KEY = "chinaunicom.phoneAttributionCache.v1"
private const val SINGLE_WIDGET_CONFIGURATION_KEY = "chinaunicom.quota.widget.configuration.v1"
private const val DUAL_WIDGET_CONFIGURATION_KEY = "chinaunicom.quota.widget.dual.configuration.v1"
private const val SHORTCUT_NOTIFICATION_KEY = "chinaunicom.shortcut.notifications.v1"

private val m11Json = Json { ignoreUnknownKeys = true }

class SharedPreferencesPhoneAttributionSettingsStorage(
    context: Context,
) : PhoneAttributionSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(M11_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    override fun loadCorrections(): Map<String, PhoneCarrierCorrection> {
        val root = preferences.getString(CARRIER_CORRECTIONS_KEY, null)?.let(::jsonObject) ?: return emptyMap()
        return root.mapNotNull { (key, value) ->
            val raw = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val correction = PhoneCarrierCorrection.entries.firstOrNull { it.rawValue == raw } ?: return@mapNotNull null
            normalizedDigits(key)?.let { it to correction }
        }.toMap()
    }

    override fun saveCorrections(value: Map<String, PhoneCarrierCorrection>): Boolean {
        val root = JsonObject(
            value.mapNotNull { (key, correction) ->
                val normalized = normalizedDigits(key) ?: return@mapNotNull null
                if (correction == PhoneCarrierCorrection.AUTOMATIC) null
                else normalized to JsonPrimitive(correction.rawValue)
            }.toMap(),
        )
        return preferences.edit().putString(CARRIER_CORRECTIONS_KEY, root.toString()).commit()
    }

    override fun loadSegments(): Map<String, PhoneSegmentAttributionRecord> {
        val array = preferences.getString(PHONE_ATTRIBUTION_CACHE_KEY, null)?.let(::jsonArray) ?: return emptyMap()
        return array.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val prefix = string(row["prefix"])?.filter(Char::isDigit)?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val carrierRaw = string(row["carrier"]) ?: return@mapNotNull null
            val carrier = PhoneCarrier.entries.firstOrNull { it.rawValue == carrierRaw } ?: PhoneCarrier.UNKNOWN
            val updatedAt = string(row["updatedAt"])?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH
            prefix to PhoneSegmentAttributionRecord(
                prefix = prefix,
                location = string(row["location"])?.trim()?.takeIf(String::isNotEmpty),
                carrier = carrier,
                updatedAt = updatedAt,
            )
        }.toMap()
    }

    override fun saveSegments(value: Map<String, PhoneSegmentAttributionRecord>): Boolean {
        val array = JsonArray(value.values.sortedBy { it.prefix }.map { record ->
            JsonObject(
                linkedMapOf(
                    "prefix" to JsonPrimitive(record.prefix),
                    "location" to nullableString(record.location),
                    "carrier" to JsonPrimitive(record.carrier.rawValue),
                    "updatedAt" to JsonPrimitive(record.updatedAt.toString()),
                ),
            )
        })
        return preferences.edit().putString(PHONE_ATTRIBUTION_CACHE_KEY, array.toString()).commit()
    }
}

interface WidgetConfigurationStorage {
    fun loadSingle(): WidgetDisplayConfiguration
    fun saveSingle(value: WidgetDisplayConfiguration): Boolean
    fun loadDual(): WidgetDualDisplayConfiguration
    fun saveDual(value: WidgetDualDisplayConfiguration): Boolean
}

data class WidgetConfigurationState(
    val single: WidgetDisplayConfiguration = WidgetDisplayConfiguration(),
    val dual: WidgetDualDisplayConfiguration = WidgetDualDisplayConfiguration(),
)

interface WidgetConfigurationRepository {
    val state: StateFlow<WidgetConfigurationState>
    fun reload()
    fun saveSingle(value: WidgetDisplayConfiguration): Boolean
    fun saveDual(value: WidgetDualDisplayConfiguration): Boolean
    fun reconcileAccounts(validAccountIDs: Set<UUID>)
}

class DefaultWidgetConfigurationRepository(
    private val storage: WidgetConfigurationStorage,
    private val refreshSettings: WidgetRefreshSettingsRepository,
) : WidgetConfigurationRepository {
    private val _state = MutableStateFlow(loadState())
    override val state: StateFlow<WidgetConfigurationState> = _state.asStateFlow()

    override fun reload() {
        _state.value = loadState()
    }

    override fun saveSingle(value: WidgetDisplayConfiguration): Boolean {
        val synchronized = synchronizeSingle(value)
        val persisted = storage.saveSingle(synchronized)
        if (persisted) _state.value = _state.value.copy(single = synchronized)
        return persisted
    }

    override fun saveDual(value: WidgetDualDisplayConfiguration): Boolean {
        val normalized = value.normalized()
        val persisted = storage.saveDual(normalized)
        if (persisted) _state.value = _state.value.copy(dual = normalized)
        return persisted
    }

    override fun reconcileAccounts(validAccountIDs: Set<UUID>) {
        val current = loadState()
        val single = current.single.copy(selectedAccountID = current.single.selectedAccountID?.takeIf(validAccountIDs::contains))
        val dual = current.dual.copy(
            leftAccountID = current.dual.leftAccountID?.takeIf(validAccountIDs::contains),
            rightAccountID = current.dual.rightAccountID?.takeIf(validAccountIDs::contains),
        ).normalized()
        storage.saveSingle(single)
        storage.saveDual(dual)
        _state.value = WidgetConfigurationState(single, dual)
    }

    private fun loadState(): WidgetConfigurationState = WidgetConfigurationState(
        single = synchronizeSingle(storage.loadSingle()),
        dual = storage.loadDual().normalized(),
    )

    private fun synchronizeSingle(value: WidgetDisplayConfiguration): WidgetDisplayConfiguration {
        val refresh = refreshSettings.loadWidgetRefreshPolicy()
        return value.copy(
            automaticRefreshEnabled = refresh.automaticRefreshEnabled,
            automaticRefreshMinutes = refresh.scheduledMinutes,
        ).normalized()
    }
}

class SharedPreferencesWidgetConfigurationStorage(
    context: Context,
) : WidgetConfigurationStorage {
    private val preferences = context.applicationContext.getSharedPreferences(M11_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    override fun loadSingle(): WidgetDisplayConfiguration {
        val root = preferences.getString(SINGLE_WIDGET_CONFIGURATION_KEY, null)?.let(::jsonObject)
            ?: return WidgetDisplayConfiguration()
        val defaults = WidgetDisplayConfiguration()
        return WidgetDisplayConfiguration(
            selectedAccountID = uuid(root["selectedAccountID"]),
            showsTodayUsage = boolean(root["showsTodayUsage"]) ?: defaults.showsTodayUsage,
            showsBalance = boolean(root["showsBalance"]) ?: defaults.showsBalance,
            automaticRefreshEnabled = boolean(root["automaticRefreshEnabled"]) ?: defaults.automaticRefreshEnabled,
            automaticRefreshMinutes = intList(root["automaticRefreshMinutes"]).ifEmpty { defaults.automaticRefreshMinutes },
            slots = slotArray(root["slots"]).ifEmpty { defaults.slots },
        ).normalized()
    }

    override fun saveSingle(value: WidgetDisplayConfiguration): Boolean {
        val normalized = value.normalized()
        val root = JsonObject(
            linkedMapOf(
                "selectedAccountID" to nullableString(normalized.selectedAccountID?.toString()),
                "showsTodayUsage" to JsonPrimitive(normalized.showsTodayUsage),
                "showsBalance" to JsonPrimitive(normalized.showsBalance),
                "automaticRefreshEnabled" to JsonPrimitive(normalized.automaticRefreshEnabled),
                "automaticRefreshMinutes" to JsonArray(normalized.automaticRefreshMinutes.map(::JsonPrimitive)),
                "slots" to JsonArray(normalized.slots.map(::singleSlotJson)),
            ),
        )
        return preferences.edit().putString(SINGLE_WIDGET_CONFIGURATION_KEY, root.toString()).commit()
    }

    override fun loadDual(): WidgetDualDisplayConfiguration {
        val root = preferences.getString(DUAL_WIDGET_CONFIGURATION_KEY, null)?.let(::jsonObject)
            ?: return WidgetDualDisplayConfiguration()
        return WidgetDualDisplayConfiguration(
            leftAccountID = uuid(root["leftAccountID"]),
            rightAccountID = uuid(root["rightAccountID"]),
            leftSlots = dualSlotArray(root["leftSlots"], WidgetDualSide.LEFT),
            rightSlots = dualSlotArray(root["rightSlots"], WidgetDualSide.RIGHT),
            sourceBindingVersion = integer(root["sourceBindingVersion"])
                ?: WidgetDualDisplayConfiguration.CURRENT_SOURCE_BINDING_VERSION,
        ).normalized()
    }

    override fun saveDual(value: WidgetDualDisplayConfiguration): Boolean {
        val normalized = value.normalized()
        val root = JsonObject(
            linkedMapOf(
                "leftAccountID" to nullableString(normalized.leftAccountID?.toString()),
                "rightAccountID" to nullableString(normalized.rightAccountID?.toString()),
                "leftSlots" to JsonArray(normalized.leftSlots.map(::dualSlotJson)),
                "rightSlots" to JsonArray(normalized.rightSlots.map(::dualSlotJson)),
                "sourceBindingVersion" to JsonPrimitive(normalized.sourceBindingVersion),
            ),
        )
        return preferences.edit().putString(DUAL_WIDGET_CONFIGURATION_KEY, root.toString()).commit()
    }

    private fun slotArray(element: JsonElement?): List<WidgetQuotaSlotConfiguration> = (element as? JsonArray).orEmpty().mapNotNull { item ->
        val row = item as? JsonObject ?: return@mapNotNull null
        val kindRaw = string(row["kind"]) ?: return@mapNotNull null
        val kind = WidgetQuotaResourceKind.entries.firstOrNull { it.rawValue == kindRaw } ?: return@mapNotNull null
        WidgetQuotaSlotConfiguration(
            id = string(row["id"]).orEmpty(),
            title = string(row["title"]).orEmpty(),
            kind = kind,
            packageIDs = stringList(row["packageIDs"]),
            isVisible = boolean(row["isVisible"]) ?: true,
        ).normalized()
    }

    private fun dualSlotArray(element: JsonElement?, side: WidgetDualSide): List<WidgetDualSlotConfiguration> {
        val defaults = WidgetDualSlotConfiguration.defaultSlots(side)
        val parsed = (element as? JsonArray).orEmpty().mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val kindRaw = string(row["kind"]) ?: return@mapNotNull null
            val kind = WidgetDualSlotKind.entries.firstOrNull { it.rawValue == kindRaw } ?: return@mapNotNull null
            WidgetDualSlotConfiguration(
                id = string(row["id"]).orEmpty(),
                title = string(row["title"]).orEmpty(),
                kind = kind,
                flowSummaryGroupID = string(row["flowSummaryGroupID"]),
                voiceSummaryGroupID = string(row["voiceSummaryGroupID"]),
                packageIDs = stringList(row["packageIDs"]),
                isVisible = boolean(row["isVisible"]) ?: true,
            )
        }
        val source = if (parsed.isEmpty()) defaults else parsed
        return source.mapIndexed { index, value -> value.normalized(defaults.getOrElse(index) { value }.id) }
    }

    private fun singleSlotJson(value: WidgetQuotaSlotConfiguration): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "title" to JsonPrimitive(value.title),
            "kind" to JsonPrimitive(value.kind.rawValue),
            "packageIDs" to JsonArray(value.packageIDs.map(::JsonPrimitive)),
            "isVisible" to JsonPrimitive(value.isVisible),
        ),
    )

    private fun dualSlotJson(value: WidgetDualSlotConfiguration): JsonObject = JsonObject(
        linkedMapOf(
            "id" to JsonPrimitive(value.id),
            "title" to JsonPrimitive(value.title),
            "kind" to JsonPrimitive(value.kind.rawValue),
            "flowSummaryGroupID" to nullableString(value.flowSummaryGroupID),
            "voiceSummaryGroupID" to nullableString(value.voiceSummaryGroupID),
            "packageIDs" to JsonArray(value.packageIDs.map(::JsonPrimitive)),
            "isVisible" to JsonPrimitive(value.isVisible),
        ),
    )
}

interface ShortcutNotificationSettingsStorage {
    fun load(): Map<UUID, ShortcutNotificationProfile>
    fun save(value: Map<UUID, ShortcutNotificationProfile>): Boolean
}

interface ShortcutNotificationSettingsRepository {
    val profiles: StateFlow<Map<UUID, ShortcutNotificationProfile>>
    fun profile(accountID: UUID): ShortcutNotificationProfile
    fun save(profile: ShortcutNotificationProfile): Boolean
    fun delete(accountID: UUID): Boolean
    fun reconcileAccounts(validAccountIDs: Set<UUID>)
}

class DefaultShortcutNotificationSettingsRepository(
    private val storage: ShortcutNotificationSettingsStorage,
) : ShortcutNotificationSettingsRepository {
    private val _profiles = MutableStateFlow(storage.load())
    override val profiles: StateFlow<Map<UUID, ShortcutNotificationProfile>> = _profiles.asStateFlow()

    override fun profile(accountID: UUID): ShortcutNotificationProfile =
        storage.load()[accountID] ?: ShortcutNotificationProfile(accountID = accountID)

    override fun save(profile: ShortcutNotificationProfile): Boolean {
        val values = storage.load().toMutableMap()
        if (profile.slot != ShortcutNotificationSlot.NONE) {
            values.entries.forEach { (id, existing) ->
                if (id != profile.accountID && existing.slot == profile.slot) {
                    values[id] = existing.copy(slot = ShortcutNotificationSlot.NONE)
                }
            }
        }
        values[profile.accountID] = profile.copy(updatedAt = Instant.now())
        val persisted = storage.save(values)
        if (persisted) _profiles.value = values
        return persisted
    }

    override fun delete(accountID: UUID): Boolean {
        val values = storage.load().toMutableMap().apply { remove(accountID) }
        val persisted = storage.save(values)
        if (persisted) _profiles.value = values
        return persisted
    }

    override fun reconcileAccounts(validAccountIDs: Set<UUID>) {
        val values = storage.load().filterKeys(validAccountIDs::contains)
        if (storage.save(values)) _profiles.value = values
    }
}

class SharedPreferencesShortcutNotificationSettingsStorage(
    context: Context,
) : ShortcutNotificationSettingsStorage {
    private val preferences = context.applicationContext.getSharedPreferences(M11_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)

    override fun load(): Map<UUID, ShortcutNotificationProfile> {
        val array = preferences.getString(SHORTCUT_NOTIFICATION_KEY, null)?.let(::jsonArray) ?: return emptyMap()
        return array.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val accountID = uuid(row["accountID"]) ?: return@mapNotNull null
            val slot = string(row["slot"])?.let { raw -> ShortcutNotificationSlot.entries.firstOrNull { it.rawValue == raw } }
                ?: ShortcutNotificationSlot.NONE
            val settingsRow = row["settings"] as? JsonObject
            val defaults = ShortcutNotificationTemplateSettings()
            accountID to ShortcutNotificationProfile(
                accountID = accountID,
                slot = slot,
                settings = ShortcutNotificationTemplateSettings(
                    notifyTraffic = boolean(settingsRow?.get("notifyTraffic")) ?: defaults.notifyTraffic,
                    notifyVoice = boolean(settingsRow?.get("notifyVoice")) ?: defaults.notifyVoice,
                    notifyBalance = boolean(settingsRow?.get("notifyBalance")) ?: defaults.notifyBalance,
                    notifyOnFailure = boolean(settingsRow?.get("notifyOnFailure")) ?: defaults.notifyOnFailure,
                    titleTemplate = string(settingsRow?.get("titleTemplate")) ?: defaults.titleTemplate,
                    subtitleTemplate = string(settingsRow?.get("subtitleTemplate")) ?: defaults.subtitleTemplate,
                    bodyTemplate = string(settingsRow?.get("bodyTemplate")) ?: defaults.bodyTemplate,
                ),
                updatedAt = string(row["updatedAt"])?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.EPOCH,
            )
        }.toMap()
    }

    override fun save(value: Map<UUID, ShortcutNotificationProfile>): Boolean {
        val array = JsonArray(value.values.sortedBy { it.accountID.toString() }.map { profile ->
            JsonObject(
                linkedMapOf(
                    "accountID" to JsonPrimitive(profile.accountID.toString()),
                    "slot" to JsonPrimitive(profile.slot.rawValue),
                    "settings" to JsonObject(
                        linkedMapOf(
                            "notifyTraffic" to JsonPrimitive(profile.settings.notifyTraffic),
                            "notifyVoice" to JsonPrimitive(profile.settings.notifyVoice),
                            "notifyBalance" to JsonPrimitive(profile.settings.notifyBalance),
                            "notifyOnFailure" to JsonPrimitive(profile.settings.notifyOnFailure),
                            "titleTemplate" to JsonPrimitive(profile.settings.titleTemplate),
                            "subtitleTemplate" to JsonPrimitive(profile.settings.subtitleTemplate),
                            "bodyTemplate" to JsonPrimitive(profile.settings.bodyTemplate),
                        ),
                    ),
                    "updatedAt" to JsonPrimitive(profile.updatedAt.toString()),
                ),
            )
        })
        return preferences.edit().putString(SHORTCUT_NOTIFICATION_KEY, array.toString()).commit()
    }
}

private fun jsonObject(raw: String): JsonObject? = runCatching { m11Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
private fun jsonArray(raw: String): JsonArray? = runCatching { m11Json.parseToJsonElement(raw) as? JsonArray }.getOrNull()
private fun normalizedDigits(value: String): String? = value.filter(Char::isDigit).takeIf(String::isNotEmpty)
private fun nullableString(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.contentOrNull?.takeUnless { it == "null" }
private fun boolean(value: JsonElement?): Boolean? = (value as? JsonPrimitive)?.booleanOrNull
private fun integer(value: JsonElement?): Int? = (value as? JsonPrimitive)?.intOrNull
private fun uuid(value: JsonElement?): UUID? = string(value)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
private fun stringList(value: JsonElement?): List<String> = (value as? JsonArray).orEmpty().mapNotNull(::string)
private fun intList(value: JsonElement?): List<Int> = (value as? JsonArray).orEmpty().mapNotNull(::integer)
