package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.PackageCategory
import com.clxmhcs.chinaunicom.core.model.QuotaType
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.model.BusinessAggregator
import com.clxmhcs.chinaunicom.model.BusinessOverview
import java.time.Instant

/**
 * Repository boundary between UI and future China Unicom data sources.
 *
 * Current implementation provides local mock data, but the mock is expressed
 * with the same authoritative M2 domain models that real network/parser data
 * will use. M4-R4 will split fake and production repository implementations.
 */
class UnicomRepository {

    fun loadOverview(): BusinessOverview {
        val updatedAt = Instant.now()
        val account = UnicomAccount(
            displayName = "测试号码",
            mobile = "18600009025",
            packageName = "校园沃派38元套餐",
            packages = listOf(
                FlowPackage(
                    id = "mock-flow-domestic",
                    originalName = "国内通用流量",
                    totalMB = 455000.0,
                    usedMB = 60650.0,
                    remainingMB = 394350.0,
                    detectedQuotaType = QuotaType.LIMITED,
                    detectedCategory = PackageCategory.GENERAL,
                    isShared = false,
                    endDateText = "2026-09-01",
                ),
                FlowPackage(
                    id = "mock-flow-province",
                    originalName = "省内流量",
                    totalMB = 110000.0,
                    usedMB = 0.0,
                    remainingMB = 110000.0,
                    detectedQuotaType = QuotaType.LIMITED,
                    detectedCategory = PackageCategory.GENERAL,
                    isShared = false,
                ),
            ),
            balanceYuan = 896.87,
            lastUpdatedAt = updatedAt,
        )

        return BusinessAggregator.aggregate(account)
    }
}
