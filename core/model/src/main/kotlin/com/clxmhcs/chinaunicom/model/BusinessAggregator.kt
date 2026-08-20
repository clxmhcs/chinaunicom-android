package com.clxmhcs.chinaunicom.model

/**
 * Aggregates raw migrated business models into the UI-facing overview model.
 *
 * The aggregation layer intentionally contains no network/session logic.
 * It only transforms already parsed business data into a stable presentation contract.
 */
object BusinessAggregator {

    fun aggregate(
        account: AccountSummary,
        quotas: List<QuotaItem> = emptyList(),
        voice: VoiceSummary? = null
    ): BusinessOverview {
        val mergedAccount = account.copy(
            remainingData = quotas,
            voice = voice
        )

        return BusinessOverview(
            accounts = listOf(mergedAccount)
        )
    }

    fun aggregateAccounts(
        accounts: List<AccountSummary>
    ): List<BusinessOverview> {
        return accounts.map { account ->
            aggregate(account = account)
        }
    }
}
