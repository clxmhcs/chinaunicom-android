package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.model.AccountSummary
import com.clxmhcs.chinaunicom.model.BusinessOverview
import com.clxmhcs.chinaunicom.model.QuotaItem

/**
 * Repository boundary between UI and future China Unicom data sources.
 *
 * Current implementation provides local mock data. Future network/session
 * layers can replace this without changing FlowHomeScreen.
 */
class UnicomRepository {

    fun loadOverview(): BusinessOverview {
        return BusinessOverview(
            accounts = listOf(
                AccountSummary(
                    accountId = "mock-001",
                    maskedNumber = "186****9025",
                    balance = "896.87元",
                    location = "山东济南",
                    planName = "校园沃派38元套餐",
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
            ),
            updatedAt = System.currentTimeMillis()
        )
    }
}
