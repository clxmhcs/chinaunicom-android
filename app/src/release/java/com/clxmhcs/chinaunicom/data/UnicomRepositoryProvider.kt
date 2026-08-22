package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.model.BusinessOverview

/**
 * Release wiring deliberately contains no fake data.
 * M6 will replace this placeholder with the production repository graph.
 */
object UnicomRepositoryProvider {
    fun create(): UnicomRepository = PendingProductionUnicomRepository
}

private object PendingProductionUnicomRepository : UnicomRepository {
    override fun loadOverview(): BusinessOverview {
        error("Production China Unicom repository is not wired until M6")
    }
}
