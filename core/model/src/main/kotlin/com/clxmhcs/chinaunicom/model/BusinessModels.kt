package com.clxmhcs.chinaunicom.model

import com.clxmhcs.chinaunicom.core.model.UnicomAccount

/**
 * UI-facing envelope only.
 *
 * Business truth stays in the M2 domain models under
 * `com.clxmhcs.chinaunicom.core.model`. This wrapper must never replace,
 * truncate, or reinterpret [UnicomAccount] / FlowPackage / VoicePackage.
 */
data class BusinessOverview(
    val accounts: List<UnicomAccount> = emptyList(),
    val updatedAt: Long = 0L,
)
