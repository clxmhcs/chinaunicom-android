package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.data.account.AccountRepository
import com.clxmhcs.chinaunicom.model.BusinessOverview

/**
 * M6 production repository entry point.
 *
 * M6-A intentionally restores persisted account metadata only. Network refresh scheduling,
 * balance/shared-gate orchestration and AppState mutation are added in later M6 substages.
 */
class ProductionUnicomRepository(
    private val accounts: AccountRepository,
) : UnicomRepository {
    override fun loadOverview(): BusinessOverview {
        val restored = accounts.loadAccounts()
        val updatedAt = restored.mapNotNull { it.lastUpdatedAt }
            .maxOrNull()
            ?.toEpochMilli()
            ?: 0L
        return BusinessOverview(
            accounts = restored,
            updatedAt = updatedAt,
        )
    }
}
