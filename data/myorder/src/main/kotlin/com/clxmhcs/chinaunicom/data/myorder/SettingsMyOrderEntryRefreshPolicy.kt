package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.data.settings.SettingsRepository

/** Adapts the existing single SettingsRepository order domain into the M9 list store entry policy. */
class SettingsMyOrderEntryRefreshPolicy(
    private val settingsRepository: SettingsRepository,
) : MyOrderEntryRefreshPolicy {
    override fun refreshOnEntry(): Boolean = settingsRepository.orderRefreshPolicy.value.refreshOnEntry
}
