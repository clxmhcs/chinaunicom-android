package com.clxmhcs.chinaunicom.ui

import androidx.lifecycle.ViewModel
import com.clxmhcs.chinaunicom.model.AccountSummary
import com.clxmhcs.chinaunicom.model.BusinessOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M4-G2-C2
 * UI state bridge from BusinessOverview to Compose screens.
 */
class FlowViewModel : ViewModel() {

    private val _overview = MutableStateFlow(
        BusinessOverview(
            accounts = listOf(
                AccountSummary(
                    accountId = "mock-001",
                    maskedNumber = "186****9025",
                    balance = "896.87元"
                )
            )
        )
    )

    val overview: StateFlow<BusinessOverview> = _overview.asStateFlow()
}
