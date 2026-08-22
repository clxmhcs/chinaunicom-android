package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.model.BusinessOverview

/**
 * Repository contract exposed to the app layer.
 *
 * M4-R4 intentionally keeps this contract free of fake data and free of any
 * premature M5/M6 login, persistence, refresh, or network orchestration.
 * Build variants provide the temporary implementation until M6 installs the
 * production repository graph.
 */
fun interface UnicomRepository {
    fun loadOverview(): BusinessOverview
}
