package com.clxmhcs.chinaunicom.ui

import androidx.lifecycle.ViewModel
import com.clxmhcs.chinaunicom.model.AccountSummary
import com.clxmhcs.chinaunicom.model.BusinessOverview
import com.clxmhcs.chinaunicom.model.QuotaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M4-G2-C4
 * Mock business data bridge for Flow screen.
 */
class FlowViewModel : ViewModel() {

    private val _overview = MutableStateFlow(
        BusinessOverview(
            accounts = listOf(
                AccountSummary(
                    accountId = "mock-001",
                    maskedNumber = "186****9025",
                    balance = "896.87元",
                    remainingData = listOf(
                        QuotaItem(
                            title = "国内通用流量",
                            used = 60650,
                            total = 455000,
                            unit = "MB",
                            expiredAt = "2026-09-01"
                        ),
                        QuotaItem(
                            title = "省内流量",
                            used = 0,
                            total = 110000,
                            unit = "MB"
                        )
                    )
                )
            )
        )
    )

    val overview: StateFlow<BusinessOverview> = _overview.asStateFlow()
}
