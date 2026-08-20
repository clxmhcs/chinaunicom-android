package com.clxmhcs.chinaunicom.core.model

internal fun String?.trimmedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
