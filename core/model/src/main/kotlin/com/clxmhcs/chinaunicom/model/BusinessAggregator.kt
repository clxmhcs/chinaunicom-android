package com.clxmhcs.chinaunicom.model

import com.clxmhcs.chinaunicom.core.model.UnicomAccount

/**
 * Builds UI envelopes from authoritative M2 domain accounts.
 *
 * No network/session logic and no lossy quota projection belongs here.
 */
object BusinessAggregator {

    fun aggregate(
        account: UnicomAccount,
        updatedAt: Long = account.lastUpdatedAt?.toEpochMilli() ?: 0L,
    ): BusinessOverview = BusinessOverview(
        accounts = listOf(account),
        updatedAt = updatedAt,
    )

    fun aggregateAccounts(
        accounts: List<UnicomAccount>,
        updatedAt: Long = accounts.mapNotNull { it.lastUpdatedAt?.toEpochMilli() }.maxOrNull() ?: 0L,
    ): BusinessOverview = BusinessOverview(
        accounts = accounts,
        updatedAt = updatedAt,
    )
}
