package com.clxmhcs.chinaunicom.data.settings

import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.DisplayUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

interface AppSettingsStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

interface AppSettingsRepository {
    val settings: StateFlow<AppSettings>
    fun load(): AppSettings
    fun save(settings: AppSettings): Boolean
}

class DefaultAppSettingsRepository(
    private val storage: AppSettingsStorage,
    private val codec: AppSettingsCodec = AppSettingsCodec(),
) : AppSettingsRepository {
    private val _settings = MutableStateFlow(loadFromStorage())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun load(): AppSettings = loadFromStorage().also { _settings.value = it }

    override fun save(settings: AppSettings): Boolean {
        val persisted = storage.write(codec.encode(settings))
        if (persisted) _settings.value = settings
        return persisted
    }

    private fun loadFromStorage(): AppSettings =
        storage.read()?.let(codec::decode) ?: AppSettings()
}

/**
 * M11 display/settings authority. It deliberately has its own storage key and does not share the
 * schema-3 AppRefreshLogic JSON, so saving display preferences can never erase future refresh
 * domains.
 */
class AppSettingsCodec(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun decode(raw: String): AppSettings? = runCatching {
        val root = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        val defaults = AppSettings()
        val displayUnit = DisplayUnit.entries.firstOrNull {
            it.rawValue == (root[DISPLAY_UNIT] as? JsonPrimitive)?.contentOrNull
        } ?: defaults.displayUnit
        AppSettings(
            autoRefreshOnLaunch = (root[AUTO_REFRESH_ON_LAUNCH] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.autoRefreshOnLaunch,
            hideMobileMiddleDigits = (root[HIDE_MOBILE] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.hideMobileMiddleDigits,
            hideBroadbandMiddleDigits = (root[HIDE_BROADBAND] as? JsonPrimitive)?.booleanOrNull
                ?: defaults.hideBroadbandMiddleDigits,
            displayUnit = displayUnit,
        )
    }.getOrNull()

    fun encode(settings: AppSettings): String = JsonObject(
        linkedMapOf(
            SCHEMA_VERSION to JsonPrimitive(CURRENT_SCHEMA_VERSION),
            AUTO_REFRESH_ON_LAUNCH to JsonPrimitive(settings.autoRefreshOnLaunch),
            HIDE_MOBILE to JsonPrimitive(settings.hideMobileMiddleDigits),
            HIDE_BROADBAND to JsonPrimitive(settings.hideBroadbandMiddleDigits),
            DISPLAY_UNIT to JsonPrimitive(settings.displayUnit.rawValue),
        ),
    ).toString()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val SCHEMA_VERSION = "schemaVersion"
        private const val AUTO_REFRESH_ON_LAUNCH = "autoRefreshOnLaunch"
        private const val HIDE_MOBILE = "hideMobileMiddleDigits"
        private const val HIDE_BROADBAND = "hideBroadbandMiddleDigits"
        private const val DISPLAY_UNIT = "displayUnit"
    }
}
