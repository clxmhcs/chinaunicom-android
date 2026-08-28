package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.DailyUsageBaseline
import com.clxmhcs.chinaunicom.core.model.PhoneCarrierCorrection
import com.clxmhcs.chinaunicom.core.model.ShortcutNotificationProfile
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualDisplayConfiguration
import com.clxmhcs.chinaunicom.data.refresh.AndroidDailyUsageBaselineStore
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.PhoneAttributionSettingsState
import com.clxmhcs.chinaunicom.data.settings.WidgetConfigurationState
import com.clxmhcs.chinaunicom.data.settings.WidgetRefreshPolicy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsM11CViewModel(application: Application) : AndroidViewModel(application) {
    private val attributionRepository = AndroidSettingsRepositories.phoneAttribution(application)
    private val widgetRepository = AndroidSettingsRepositories.widgetConfiguration(application)
    private val refreshRepository = AndroidSettingsRepositories.refreshLogic(application)
    private val shortcutRepository = AndroidSettingsRepositories.shortcutNotifications(application)
    private val baselineStore = AndroidDailyUsageBaselineStore(application.applicationContext)

    val attributionState: StateFlow<PhoneAttributionSettingsState> = attributionRepository.state
    val widgetState: StateFlow<WidgetConfigurationState> = widgetRepository.state
    val widgetRefreshPolicy: StateFlow<WidgetRefreshPolicy> = refreshRepository.widgetRefreshPolicy
    val shortcutProfiles: StateFlow<Map<UUID, ShortcutNotificationProfile>> = shortcutRepository.profiles

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    fun reconcileMobileAccounts(accountIDs: Set<UUID>) {
        widgetRepository.reconcileAccounts(accountIDs)
        shortcutRepository.reconcileAccounts(accountIDs)
    }

    fun correction(number: String): PhoneCarrierCorrection = attributionRepository.correction(number)

    fun cycleCorrection(number: String) {
        val current = attributionRepository.correction(number)
        val values = PhoneCarrierCorrection.entries
        val next = values[(values.indexOf(current) + 1) % values.size]
        val saved = attributionRepository.setCorrection(number, next)
        _operationMessage.value = if (saved) "号码归属修正已保存" else "号码归属修正保存失败"
    }

    fun resetCorrections() {
        _operationMessage.value = if (attributionRepository.resetCorrections()) "已恢复自动识别" else "恢复失败"
    }

    fun updatePhoneSegments() {
        if (attributionState.value.isUpdatingSegments) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = attributionRepository.updateCachedSegments()
            _operationMessage.value = if (result.failedCount == 0) {
                "号段更新完成：${result.updatedCount} 个"
            } else {
                "号段更新完成：${result.updatedCount} 个成功，${result.failedCount} 个失败"
            }
        }
    }

    fun refreshLocation(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = attributionRepository.refreshLocation(number)
            _operationMessage.value = record?.location?.let { "归属地：$it" } ?: "未获取到归属地"
        }
    }

    fun automaticCarrierTitle(number: String): String = attributionRepository.automaticCarrier(number).displayName
    fun resolvedCarrierTitle(number: String): String = attributionRepository.carrier(number).displayName
    fun cachedLocation(number: String): String? = attributionRepository.cachedLocation(number)

    fun saveSingleWidget(configuration: WidgetDisplayConfiguration) {
        _operationMessage.value = if (widgetRepository.saveSingle(configuration)) "单号码组件配置已保存" else "组件配置保存失败"
    }

    fun saveDualWidget(configuration: WidgetDualDisplayConfiguration) {
        _operationMessage.value = if (widgetRepository.saveDual(configuration)) "双号码组件配置已保存" else "组件配置保存失败"
    }

    fun setWidgetAutomaticRefresh(enabled: Boolean) {
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(automaticRefreshEnabled = enabled))
    }

    fun shiftWidgetRefreshTime(index: Int, deltaMinutes: Int) {
        val current = widgetRefreshPolicy.value.scheduledMinutes.toMutableList()
        if (index !in current.indices) return
        current[index] = Math.floorMod(current[index] + deltaMinutes, 24 * 60)
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(scheduledMinutes = current))
    }

    fun addWidgetRefreshTime() {
        val current = widgetRefreshPolicy.value.scheduledMinutes.toMutableList()
        val next = ((current.lastOrNull() ?: 8 * 60) + 3 * 60) % (24 * 60)
        current += next
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(scheduledMinutes = current))
    }

    fun removeWidgetRefreshTime(index: Int) {
        val current = widgetRefreshPolicy.value.scheduledMinutes.toMutableList()
        if (index !in current.indices || current.size <= 1) return
        current.removeAt(index)
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(scheduledMinutes = current))
    }

    fun changeWidgetCompensation(deltaMinutes: Int) {
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(compensationMinutes = widgetRefreshPolicy.value.compensationMinutes + deltaMinutes))
    }

    fun changeWidgetFailureRetry(deltaSeconds: Int) {
        saveWidgetRefresh(widgetRefreshPolicy.value.copy(failureRetrySeconds = widgetRefreshPolicy.value.failureRetrySeconds + deltaSeconds))
    }

    fun shortcutProfile(accountID: UUID): ShortcutNotificationProfile = shortcutRepository.profile(accountID)

    fun saveShortcutProfile(profile: ShortcutNotificationProfile) {
        _operationMessage.value = if (shortcutRepository.save(profile)) "快捷通知配置已保存" else "快捷通知配置保存失败"
    }

    fun dailyUsageBaseline(accountID: UUID, dateKey: String): DailyUsageBaseline? = baselineStore.load(accountID, dateKey)

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    private fun saveWidgetRefresh(policy: WidgetRefreshPolicy) {
        val result = refreshRepository.saveWidgetRefreshPolicy(policy)
        if (result.persisted) {
            widgetRepository.reload()
            _operationMessage.value = "组件刷新策略已保存"
        } else {
            _operationMessage.value = "组件刷新策略保存失败"
        }
    }
}
