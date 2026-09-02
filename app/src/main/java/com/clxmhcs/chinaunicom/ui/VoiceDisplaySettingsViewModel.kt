package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.ResourceDisplayKind
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.VoiceSummaryGroup
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI-09 persistence bridge for the iOS VoiceDisplaySettingsView route.
 *
 * Only already-migrated local presentation metadata is mutated here. Voice quota parsing, refresh,
 * credentials, session/cookie, shared cache and carrier networking remain owned by the existing
 * production repositories.
 */
class VoiceDisplaySettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val accountRepository = DefaultAccountRepository(
        store = AndroidAccountMetadataStores.accounts(app),
    )
    private val unicomRepository = UnicomRepositoryProvider.create(app)
    private val attributionRepository = AndroidSettingsRepositories.phoneAttribution(app)
    private val mutationMutex = Mutex()

    fun cachedLocation(number: String): String? = attributionRepository.cachedLocation(number)

    fun setResourceKind(accountID: UUID, packageKey: String, kind: ResourceDisplayKind?) {
        mutatePreference(accountID, packageKey) { preference ->
            preference.copy(resourceKindOverride = kind)
        }
    }

    fun setVoicePackageHidden(accountID: UUID, packageKey: String, hidden: Boolean) {
        mutatePreference(accountID, packageKey) { preference ->
            preference.copy(
                placement = if (hidden) DisplayPlacement.HIDDEN else DisplayPlacement.DETAIL_ONLY,
            )
        }
    }

    fun addVoiceSummaryGroup(accountID: UUID) {
        mutateAccount(accountID) { account ->
            val groups = sortedVoiceSummaryGroups(account)
            account.copy(
                voiceSummaryGroups = groups + VoiceSummaryGroup(
                    name = "新建语音分类",
                    sortOrder = groups.size,
                ),
            )
        }
    }

    fun updateVoiceSummaryGroup(accountID: UUID, group: VoiceSummaryGroup) {
        mutateAccount(accountID) { account ->
            val groups = sortedVoiceSummaryGroups(account)
                .map { if (it.id == group.id) group else it }
                .mapIndexed { index, value -> value.copy(sortOrder = index) }
            account.copy(voiceSummaryGroups = groups)
        }
    }

    fun deleteVoiceSummaryGroup(accountID: UUID, groupID: String) {
        mutateAccount(accountID) { account ->
            val groups = sortedVoiceSummaryGroups(account)
                .filterNot { it.id == groupID }
                .mapIndexed { index, value -> value.copy(sortOrder = index) }
            account.copy(voiceSummaryGroups = groups)
        }
    }

    fun moveVoiceSummaryGroup(accountID: UUID, groupID: String, delta: Int) {
        mutateAccount(accountID) { account ->
            val groups = sortedVoiceSummaryGroups(account).toMutableList()
            val from = groups.indexOfFirst { it.id == groupID }
            if (from < 0 || groups.isEmpty()) return@mutateAccount account
            val to = (from + delta).coerceIn(0, groups.lastIndex)
            if (to == from) return@mutateAccount account
            val moved = groups.removeAt(from)
            groups.add(to, moved)
            account.copy(
                voiceSummaryGroups = groups.mapIndexed { index, value -> value.copy(sortOrder = index) },
            )
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

    private fun sortedVoiceSummaryGroups(account: UnicomAccount): List<VoiceSummaryGroup> =
        account.voiceSummaryGroups.orEmpty().sortedWith(
            compareBy<VoiceSummaryGroup> { it.sortOrder }.thenBy { it.name },
        )
}
