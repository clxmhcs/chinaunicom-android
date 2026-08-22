package com.clxmhcs.chinaunicom.data.account

import com.clxmhcs.chinaunicom.core.login.ValidatedLoginAccountSeed
import com.clxmhcs.chinaunicom.core.model.DisplayPlacement
import com.clxmhcs.chinaunicom.core.model.PackageDisplayPreference
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.storage.AccountMetadataStore
import java.time.Clock
import java.time.Instant
import java.util.UUID

interface AccountRepository {
    fun loadAccounts(): List<UnicomAccount>
    fun createValidatedAccount(displayName: String, seed: ValidatedLoginAccountSeed): UnicomAccount
    fun replaceAccounts(accounts: List<UnicomAccount>)
    fun removeAccount(accountID: UUID)
    fun clear()
}

/**
 * M6 account-metadata repository. Credentials are intentionally absent from this module.
 *
 * M5 LoginAccountLifecycle invokes [createValidatedAccount] only after quota validation and after
 * securely binding credentials to the same account UUID. This repository persists ordinary
 * UnicomAccount metadata only.
 */
class DefaultAccountRepository(
    private val store: AccountMetadataStore,
    private val clock: Clock = Clock.systemUTC(),
) : AccountRepository {

    override fun loadAccounts(): List<UnicomAccount> =
        store.loadAccounts().sortedBy { it.sortOrder }

    override fun createValidatedAccount(
        displayName: String,
        seed: ValidatedLoginAccountSeed,
    ): UnicomAccount {
        val existing = loadAccounts()
        require(existing.none { it.id == seed.accountID }) { "Account UUID already exists: ${seed.accountID}" }

        val quota = seed.quota
        val completedAt = Instant.now(clock)
        val preferences = buildList {
            quota.packages.forEachIndexed { index, packageValue ->
                add(
                    PackageDisplayPreference(
                        packageKey = packageValue.id,
                        placement = when (index) {
                            0 -> DisplayPlacement.PRIMARY
                            1, 2 -> DisplayPlacement.SECONDARY
                            else -> DisplayPlacement.DETAIL_ONLY
                        },
                        sortOrder = index,
                    ),
                )
            }
            quota.voicePackages.forEachIndexed { index, packageValue ->
                add(
                    PackageDisplayPreference(
                        packageKey = packageValue.id,
                        placement = DisplayPlacement.DETAIL_ONLY,
                        sortOrder = quota.packages.size + index,
                    ),
                )
            }
        }

        val baseAccount = UnicomAccount(
            id = seed.accountID,
            displayName = displayName.trim().ifEmpty { "联通号码" },
            mobile = seed.mobile,
            packageName = quota.packageName,
            packages = quota.packages,
            voicePackages = quota.voicePackages,
            remainingQuerySnapshot = quota.remainingQuerySnapshot?.copy(updatedAt = completedAt),
            balanceYuan = null,
            balanceUpdatedAt = null,
            unavailableBalanceDetail = null,
            displayPreferences = preferences,
            summaryGroups = null,
            voiceSummaryGroups = null,
            quotaResourceStatus = quota.quotaResourceStatus,
            lastUpdatedAt = completedAt,
            lastErrorMessage = null,
            isEnabled = true,
            sortOrder = existing.size,
        )
        val account = baseAccount.copy(summaryGroups = baseAccount.automaticSummaryGroups)
        store.saveAccounts(existing + account)
        return account
    }

    override fun replaceAccounts(accounts: List<UnicomAccount>) {
        store.saveAccounts(accounts.sortedBy { it.sortOrder })
    }

    override fun removeAccount(accountID: UUID) {
        val remaining = loadAccounts()
            .filterNot { it.id == accountID }
            .mapIndexed { index, account -> account.copy(sortOrder = index) }
        store.saveAccounts(remaining)
    }

    override fun clear() {
        store.clear()
    }
}
