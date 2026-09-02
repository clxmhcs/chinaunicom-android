package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.FlowSummaryGroup
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

/**
 * UI-07 persistence bridge for iOS PackageDisplaySettingsView parity.
 *
 * This class only mutates already-migrated local account presentation metadata. Carrier quota,
 * refresh, credential, cookie/session and parser authority remain in the production repository.
 */
class FlowDisplaySettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val accountRepository = DefaultAccountRepository(
        store = AndroidAccountMetadataStores.accounts(application),
    )
    private val unicomRepository = UnicomRepositoryProvider.create(application)
    private val mutationMutex = Mutex()

    fun materializeSummaryGroups(accountID: UUID) {
        mutateAccount(accountID) { account ->
            if (account.summaryGroups == null) account.copy(summaryGroups = account.automaticSummaryGroups) else account
        }
    }

    fun addSummaryGroup(accountID: UUID) {
        mutateAccount(accountID) { account ->
            val groups = account.configuredSummaryGroups
            account.copy(
                summaryGroups = groups + FlowSummaryGroup(
                    name = "新建统计分类",
                    sortOrder = groups.size,
                ),
            )
        }
    }

    fun updateSummaryGroup(accountID: UUID, group: FlowSummaryGroup) {
        mutateAccount(accountID) { account ->
            val groups = account.configuredSummaryGroups
            val updated = groups.map { if (it.id == group.id) group else it }
                .mapIndexed { index, value -> value.copy(sortOrder = index) }
            account.copy(summaryGroups = updated)
        }
    }

    fun deleteSummaryGroup(accountID: UUID, groupID: String) {
        mutateAccount(accountID) { account ->
            val groups = account.configuredSummaryGroups
                .filterNot { it.id == groupID }
                .mapIndexed { index, value -> value.copy(sortOrder = index) }
            account.copy(summaryGroups = groups)
        }
    }

    fun moveSummaryGroup(accountID: UUID, groupID: String, delta: Int) {
        mutateAccount(accountID) { account ->
            val groups = account.configuredSummaryGroups.toMutableList()
            val from = groups.indexOfFirst { it.id == groupID }
            if (from < 0) return@mutateAccount account
            val to = (from + delta).coerceIn(0, groups.lastIndex)
            if (to == from) return@mutateAccount account
            val moved = groups.removeAt(from)
            groups.add(to, moved)
            account.copy(summaryGroups = groups.mapIndexed { index, value -> value.copy(sortOrder = index) })
        }
    }

    fun setPackageHidden(accountID: UUID, packageKey: String, hidden: Boolean) {
        mutatePreference(accountID, packageKey) { preference ->
            preference.copy(
                placement = if (hidden) DisplayPlacement.HIDDEN else DisplayPlacement.DETAIL_ONLY,
            )
        }
    }

    fun setResourceKind(accountID: UUID, packageKey: String, kind: ResourceDisplayKind?) {
        mutatePreference(accountID, packageKey) { preference ->
            preference.copy(resourceKindOverride = kind)
        }
    }

    fun moveVisiblePackage(accountID: UUID, packageKey: String, delta: Int) {
        mutateAccount(accountID) { account ->
            val visibleIDs = account.visibleDetailPackages.map { it.id }.toMutableList()
            val from = visibleIDs.indexOf(packageKey)
            if (from < 0) return@mutateAccount account
            val to = (from + delta).coerceIn(0, visibleIDs.lastIndex)
            if (to == from) return@mutateAccount account
            val moved = visibleIDs.removeAt(from)
            visibleIDs.add(to, moved)

            val hiddenIDs = account.hiddenPackages.map { it.id }
            val voiceIDs = account.resolvedVoicePackages.map { it.id }.filterNot { it in visibleIDs || it in hiddenIDs }
            val orderedIDs = visibleIDs + hiddenIDs + voiceIDs
            val order = orderedIDs.withIndex().associate { it.value to it.index }
            val existing = account.displayPreferences.associateBy { it.packageKey }
            val knownIDs = (account.displayPreferences.map { it.packageKey } + orderedIDs).distinct()
            val preferences = knownIDs.mapIndexed { fallbackIndex, key ->
                val base = existing[key] ?: PackageDisplayPreference(packageKey = key)
                base.copy(sortOrder = order[key] ?: (orderedIDs.size + fallbackIndex))
            }
            account.copy(displayPreferences = preferences)
        }
    }

    fun updateAccountDisplayName(accountID: UUID, displayName: String) {
        mutateAccount(accountID) { account ->
            account.copy(displayName = displayName.trim())
        }
    }

    private fun mutatePreference(
        accountID: UUID,
        packageKey: String,
        transform: (PackageDisplayPreference) -> PackageDisplayPreference,
    ) {
        mutateAccount(accountID) { account ->
            val existing = account.displayPreferences.firstOrNull { it.packageKey == packageKey }
                ?: PackageDisplayPreference(
                    packageKey = packageKey,
                    sortOrder = account.displayPreferences.size,
                )
            val replacement = transform(existing)
            val preferences = buildList {
                var replaced = false
                account.displayPreferences.forEach { value ->
                    if (value.packageKey == packageKey) {
                        add(replacement)
                        replaced = true
                    } else {
                        add(value)
                    }
                }
                if (!replaced) add(replacement)
            }
            account.copy(displayPreferences = preferences)
        }
    }

    private fun mutateAccount(
        accountID: UUID,
        transform: (UnicomAccount) -> UnicomAccount,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            mutationMutex.withLock {
                val accounts = accountRepository.loadAccounts()
                val current = accounts.firstOrNull { it.id == accountID } ?: return@withLock
                val replacement = transform(current)
                if (replacement == current) return@withLock
                accountRepository.replaceAccounts(
                    accounts.map { if (it.id == accountID) replacement else it },
                )
                unicomRepository.reloadAccountsFromPersistence()
            }
        }
    }
}
