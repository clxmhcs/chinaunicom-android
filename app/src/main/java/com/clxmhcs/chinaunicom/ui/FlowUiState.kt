package com.clxmhcs.chinaunicom.ui

import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.data.balance.BalanceRepositoryState
import com.clxmhcs.chinaunicom.data.refresh.UnicomAppState

/** Shared dashboard state for the flow and voice roots. */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    data class Content(
        val appState: UnicomAppState,
        val balanceState: BalanceRepositoryState,
    ) : FlowUiState {
        val accounts: List<UnicomAccount> get() = appState.accounts

        val homeBalanceAccount: UnicomAccount?
            get() = balanceState.homeBalanceAccountID?.let { homeID ->
                accounts.firstOrNull { it.id == homeID }
            }
    }

    data class Error(
        val message: String,
    ) : FlowUiState
}
