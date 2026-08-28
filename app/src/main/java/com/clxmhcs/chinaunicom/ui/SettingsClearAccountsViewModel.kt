package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.AppSettings
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration
import com.clxmhcs.chinaunicom.core.model.WidgetDualDisplayConfiguration
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.AndroidQuotaRefreshRuntimeStore
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.balance.AndroidSharedBalanceCacheStores
import com.clxmhcs.chinaunicom.data.broadbandaccount.AndroidBroadbandAccountMetadataStore
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo
import com.clxmhcs.chinaunicom.data.broadbandaccount.DefaultBroadbandAccountRepository
import com.clxmhcs.chinaunicom.data.phonebill.AndroidPhoneBillDiskCache
import com.clxmhcs.chinaunicom.data.refresh.AndroidDailyUsageBaselineStore
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshIntervalSynchronizer
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.IntegralRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.MyPackageRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.OrderedBusinessRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.PhoneBillRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.RebateGiftRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.SharedPreferencesPhoneAttributionSettingsStorage
import com.clxmhcs.chinaunicom.data.settings.SharedPreferencesShortcutNotificationSettingsStorage
import com.clxmhcs.chinaunicom.data.settings.SharedPreferencesWidgetConfigurationStorage
import com.clxmhcs.chinaunicom.data.settings.VideoRingRefreshPolicy
import com.clxmhcs.chinaunicom.data.settings.WidgetRefreshPolicy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsClearAccountsUiState(
    val mobileAccounts: List<UnicomAccount> = emptyList(),
    val broadbandAccounts: List<BroadbandAccountInfo> = emptyList(),
    val selectedMobileIDs: Set<UUID> = emptySet(),
    val selectedBroadbandIDs: Set<UUID> = emptySet(),
    val requiresVerification: Boolean = false,
    val isVerified: Boolean = false,
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val operationSerial: Long = 0,
    val closeRequested: Boolean = false,
    val clearedAll: Boolean = false,
) {
    val totalAccountCount: Int get() = mobileAccounts.size + broadbandAccounts.size
    val selectedCount: Int get() = selectedMobileIDs.size + selectedBroadbandIDs.size
    val hasAccounts: Boolean get() = totalAccountCount > 0
    val allSelected: Boolean get() = hasAccounts && selectedCount == totalAccountCount
}

/**
 * M11-D destructive-maintenance coordinator derived from iOS SettingsSecurityViews.
 *
 * Android has no separate credential-management password authority yet, so the iOS compatibility
 * fallback is used: when at least one mobile account exists, the first sorted mobile number must be
 * entered before opening destructive maintenance. With broadband-only state, iOS also allows the
 * manager to open directly when no credential password exists.
 */
class SettingsClearAccountsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val accountRepository = DefaultAccountRepository(AndroidAccountMetadataStores.accounts(app))
    private val broadbandRepository = DefaultBroadbandAccountRepository(AndroidBroadbandAccountMetadataStore(app))
    private val credentialStore = CredentialStoreProvider.create(app)
    private val unicomRepository = UnicomRepositoryProvider.create(app)
    private val dailyBaselineStore = AndroidDailyUsageBaselineStore(app)
    private val phoneBillCache = AndroidPhoneBillDiskCache(app)

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<SettingsClearAccountsUiState> = _state.asStateFlow()

    fun verify(input: String) {
        if (!_state.value.requiresVerification) {
            _state.update { it.copy(isVerified = true, errorMessage = null) }
            return
        }
        val expected = _state.value.mobileAccounts.firstOrNull()?.mobile?.filter(Char::isDigit).orEmpty()
        val candidate = input.filter(Char::isDigit)
        if (expected.isNotEmpty() && candidate == expected) {
            _state.update { it.copy(isVerified = true, errorMessage = null, statusMessage = null) }
        } else {
            _state.update { it.copy(errorMessage = "身份验证失败", statusMessage = null) }
        }
    }

    fun toggleMobile(accountID: UUID) {
        if (!_state.value.isVerified || _state.value.isWorking) return
        _state.update { current ->
            val next = current.selectedMobileIDs.toMutableSet()
            if (!next.add(accountID)) next.remove(accountID)
            current.copy(selectedMobileIDs = next, errorMessage = null)
        }
    }

    fun toggleBroadband(accountID: UUID) {
        if (!_state.value.isVerified || _state.value.isWorking) return
        _state.update { current ->
            val next = current.selectedBroadbandIDs.toMutableSet()
            if (!next.add(accountID)) next.remove(accountID)
            current.copy(selectedBroadbandIDs = next, errorMessage = null)
        }
    }

    fun selectOnlyMobile(accountID: UUID) {
        if (!_state.value.isVerified || _state.value.isWorking) return
        _state.update { it.copy(selectedMobileIDs = setOf(accountID), selectedBroadbandIDs = emptySet()) }
    }

    fun selectOnlyBroadband(accountID: UUID) {
        if (!_state.value.isVerified || _state.value.isWorking) return
        _state.update { it.copy(selectedMobileIDs = emptySet(), selectedBroadbandIDs = setOf(accountID)) }
    }

    fun toggleSelectAll() {
        if (!_state.value.isVerified || _state.value.isWorking) return
        _state.update { current ->
            if (current.allSelected) {
                current.copy(selectedMobileIDs = emptySet(), selectedBroadbandIDs = emptySet())
            } else {
                current.copy(
                    selectedMobileIDs = current.mobileAccounts.mapTo(linkedSetOf()) { it.id },
                    selectedBroadbandIDs = current.broadbandAccounts.mapTo(linkedSetOf()) { it.id },
                )
            }
        }
    }

    fun deleteSelected() {
        val snapshot = _state.value
        if (!snapshot.isVerified || snapshot.isWorking || snapshot.selectedCount == 0) return
        val mobileIDs = snapshot.selectedMobileIDs
        val broadbandIDs = snapshot.selectedBroadbandIDs
        val deletingAll = snapshot.allSelected
        _state.update { it.copy(isWorking = true, statusMessage = "正在删除本机账户与凭据…", errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (deletingAll) clearAllLocalState(snapshot.mobileAccounts, snapshot.broadbandAccounts)
                else deleteSelectedAccounts(mobileIDs, broadbandIDs)
            }.onSuccess {
                val mobiles = accountRepository.loadAccounts()
                val broadband = broadbandRepository.loadAccounts()
                val empty = mobiles.isEmpty() && broadband.isEmpty()
                _state.value = SettingsClearAccountsUiState(
                    mobileAccounts = mobiles,
                    broadbandAccounts = broadband,
                    requiresVerification = mobiles.isNotEmpty(),
                    isVerified = true,
                    isWorking = false,
                    statusMessage = if (deletingAll) "已清空全部账户与凭据" else "已删除选中的账户与凭据",
                    operationSerial = snapshot.operationSerial + 1,
                    closeRequested = deletingAll || empty,
                    clearedAll = deletingAll,
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isWorking = false,
                        statusMessage = null,
                        errorMessage = error.message?.take(240) ?: "删除失败",
                        operationSerial = it.operationSerial + 1,
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    private suspend fun deleteSelectedAccounts(mobileIDs: Set<UUID>, broadbandIDs: Set<UUID>) {
        for (accountID in mobileIDs) {
            val previousAccounts = accountRepository.loadAccounts()
            if (previousAccounts.none { it.id == accountID }) continue
            val credentialBackup = runCatching { credentialStore.read(accountID) }.getOrNull()

            try {
                accountRepository.removeAccount(accountID)
                credentialStore.delete(accountID)
            } catch (error: Throwable) {
                runCatching { accountRepository.replaceAccounts(previousAccounts) }
                credentialBackup?.let { backup -> runCatching { credentialStore.save(accountID, backup) } }
                throw error
            }

            val balance = unicomRepository.balanceState.value
            if (balance.homeBalanceAccountID == accountID) unicomRepository.setHomeBalanceAccountID(null)
            balance.balanceAccountGroups
                .filter { accountID in it.memberAccountIDs }
                .forEach { group -> unicomRepository.toggleBalanceAccount(accountID, group.id) }
            dailyBaselineStore.deleteAccount(accountID)
        }

        if (mobileIDs.isNotEmpty()) {
            val remainingIDs = accountRepository.loadAccounts().mapTo(linkedSetOf()) { it.id }
            phoneBillCache.pruneAccounts(remainingIDs)
            unicomRepository.reloadAccountsFromPersistence()
        }

        for (accountID in broadbandIDs) {
            val previous = broadbandRepository.loadAccounts().firstOrNull { it.id == accountID } ?: continue
            val credentialBackup = runCatching { credentialStore.read(accountID) }.getOrNull()
            try {
                broadbandRepository.remove(accountID)
                credentialStore.delete(accountID)
            } catch (error: Throwable) {
                runCatching { broadbandRepository.upsert(previous) }
                credentialBackup?.let { backup -> runCatching { credentialStore.save(accountID, backup) } }
                throw error
            }
            dailyBaselineStore.deleteAccount(accountID)
        }
    }

    private suspend fun clearAllLocalState(
        mobileAccounts: List<UnicomAccount>,
        broadbandAccounts: List<BroadbandAccountInfo>,
    ) {
        // Clear the home representative before removing account metadata so reload cannot retain a dead UUID.
        unicomRepository.setHomeBalanceAccountID(null)
        accountRepository.clear()
        unicomRepository.reloadAccountsFromPersistence()

        // With an empty account state, deleting groups cannot trigger real balance network work.
        unicomRepository.balanceState.value.balanceAccountGroups.map { it.id }.forEach { groupID ->
            unicomRepository.deleteBalanceAccountGroup(groupID)
        }

        broadbandRepository.clear()
        credentialStore.deleteAll()
        mobileAccounts.forEach { dailyBaselineStore.deleteAccount(it.id) }
        broadbandAccounts.forEach { dailyBaselineStore.deleteAccount(it.id) }
        phoneBillCache.clear()
        check(AndroidQuotaRefreshRuntimeStore(app).clear()) { "无法清除刷新运行状态" }
        resetSettingsAuthorities()
    }

    private fun resetSettingsAuthorities() {
        val appSettings = AndroidSettingsRepositories.appSettings(app)
        check(appSettings.save(AppSettings())) { "无法恢复 App 设置" }

        val sharedBalanceCache = AndroidSharedBalanceCacheStores.create(app)
        val refresh = AndroidSettingsRepositories.refreshLogic(
            app,
            BalanceRefreshIntervalSynchronizer(sharedBalanceCache::setRefreshIntervalMinutes),
        )
        check(refresh.saveQuotaRefreshPolicy(QuotaRefreshPolicy()).persisted) { "无法恢复流量刷新设置" }
        check(refresh.saveBalanceRefreshPolicy(BalanceRefreshPolicy()).persisted) { "无法恢复余额刷新设置" }
        check(refresh.saveOrderedBusinessRefreshPolicy(OrderedBusinessRefreshPolicy()).persisted) { "无法恢复已订业务刷新设置" }
        check(refresh.saveMyPackageRefreshPolicy(MyPackageRefreshPolicy()).persisted) { "无法恢复套餐刷新设置" }
        check(refresh.savePhoneBillRefreshPolicy(PhoneBillRefreshPolicy()).persisted) { "无法恢复账单刷新设置" }
        check(refresh.saveIntegralRefreshPolicy(IntegralRefreshPolicy()).persisted) { "无法恢复积分刷新设置" }
        check(refresh.saveOrderRefreshPolicy(OrderRefreshPolicy()).persisted) { "无法恢复订单刷新设置" }
        check(refresh.saveRebateGiftRefreshPolicy(RebateGiftRefreshPolicy()).persisted) { "无法恢复返赠刷新设置" }
        check(refresh.saveVideoRingRefreshPolicy(VideoRingRefreshPolicy()).persisted) { "无法恢复视频彩铃刷新设置" }
        check(refresh.saveWidgetRefreshPolicy(WidgetRefreshPolicy()).persisted) { "无法恢复组件刷新设置" }

        val attribution = SharedPreferencesPhoneAttributionSettingsStorage(app)
        check(attribution.saveCorrections(emptyMap())) { "无法清除号码归属修正" }
        check(attribution.saveSegments(emptyMap())) { "无法清除号段缓存" }

        val widget = SharedPreferencesWidgetConfigurationStorage(app)
        check(widget.saveSingle(WidgetDisplayConfiguration())) { "无法恢复单号码组件设置" }
        check(widget.saveDual(WidgetDualDisplayConfiguration())) { "无法恢复双号码组件设置" }

        val shortcuts = SharedPreferencesShortcutNotificationSettingsStorage(app)
        check(shortcuts.save(emptyMap())) { "无法清除快捷通知设置" }
    }

    private fun loadInitialState(): SettingsClearAccountsUiState {
        val mobiles = accountRepository.loadAccounts()
        val broadband = broadbandRepository.loadAccounts()
        val requiresVerification = mobiles.isNotEmpty()
        return SettingsClearAccountsUiState(
            mobileAccounts = mobiles,
            broadbandAccounts = broadband,
            requiresVerification = requiresVerification,
            isVerified = !requiresVerification,
        )
    }
}
