package com.clxmhcs.chinaunicom.data.settings

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Keeps string extraction tolerant across kotlinx.serialization versions used by the Android build.
 * JSON null remains null instead of being treated as the literal string "null".
 */
internal val JsonPrimitive.contentOrNull: String?
    get() = if (this === JsonNull) null else content
