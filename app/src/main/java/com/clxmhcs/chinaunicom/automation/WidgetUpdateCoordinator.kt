package com.clxmhcs.chinaunicom.automation

import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.widget.WidgetSnapshotExporter

/**
 * M13 App-side Widget publication boundary.
 *
 * Automatic work never writes Glance state directly. It commits through the production
 * repository, which invokes this coordinator after the authoritative account state is durable.
 */
fun interface WidgetUpdateCoordinator {
    suspend fun publish(accounts: List<UnicomAccount>)
}

class DefaultWidgetUpdateCoordinator(
    private val exporter: WidgetSnapshotExporter,
) : WidgetUpdateCoordinator {
    override suspend fun publish(accounts: List<UnicomAccount>) {
        exporter.onAccountsCommitted(accounts)
    }
}
