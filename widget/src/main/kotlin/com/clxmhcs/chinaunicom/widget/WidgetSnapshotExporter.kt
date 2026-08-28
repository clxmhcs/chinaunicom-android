package com.clxmhcs.chinaunicom.widget

import android.content.Context
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.model.WidgetDualSide
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.WidgetConfigurationRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID

fun interface WidgetUpdateNotifier {
    suspend fun requestAll()

    companion object {
        val NONE = WidgetUpdateNotifier { }
    }
}

fun interface WidgetAccountCommitObserver {
    suspend fun onAccountsCommitted(accounts: List<UnicomAccount>)

    companion object {
        val NONE = WidgetAccountCommitObserver { }
    }
}

class WidgetSnapshotExporter(
    private val configurationRepository: WidgetConfigurationRepository,
    private val dailyUsageCalculator: WidgetDailyUsageCalculator,
    private val store: WidgetSnapshotStore,
    private val notifier: WidgetUpdateNotifier = WidgetUpdateNotifier.NONE,
    private val clock: Clock = Clock.systemUTC(),
) : WidgetAccountCommitObserver {

    override suspend fun onAccountsCommitted(accounts: List<UnicomAccount>) {
        export(accounts)
    }

    suspend fun export(accounts: List<UnicomAccount>, now: Instant = Instant.now(clock)): Boolean {
        configurationRepository.reload()
        val state = configurationRepository.state.value
        val ordered = accounts.sortedBy { it.sortOrder }

        if (ordered.isEmpty()) {
            store.clear()
            notifier.requestAll()
            return false
        }

        val selectedSingle = state.single.selectedAccountID
            ?.let { selectedID -> ordered.firstOrNull { it.id == selectedID } }
            ?: ordered.firstOrNull { it.isEnabled }
            ?: ordered.firstOrNull()

        if (selectedSingle == null) {
            store.saveSingle(null)
        } else {
            store.saveSingle(makeSingleSnapshot(selectedSingle, state.single, now))
        }

        val dualConfiguration = state.dual.normalized()
        val left = dualConfiguration.leftAccountID
            ?.let { id -> ordered.firstOrNull { it.id == id } }
            ?.let { makeDualAccountSnapshot(it, dualConfiguration.slots(WidgetDualSide.LEFT), now) }
        val right = dualConfiguration.rightAccountID
            ?.let { id -> ordered.firstOrNull { it.id == id } }
            ?.let { makeDualAccountSnapshot(it, dualConfiguration.slots(WidgetDualSide.RIGHT), now) }

        store.saveDual(
            if (left == null && right == null) null
            else WidgetDualSnapshot(
                left = left,
                right = right,
                generatedAt = listOfNotNull(left?.updatedAt, right?.updatedAt).maxOrNull() ?: now,
            ),
        )
        notifier.requestAll()
        return selectedSingle != null || left != null || right != null
    }

    private fun makeSingleSnapshot(
        account: UnicomAccount,
        configuration: com.clxmhcs.chinaunicom.core.model.WidgetDisplayConfiguration,
        now: Instant,
    ): WidgetQuotaSnapshot {
        val quotaUpdatedAt = account.lastUpdatedAt ?: account.remainingQuerySnapshot?.updatedAt ?: now
        val todayUsageGB = ((dailyUsageCalculator.todayUsedMB(
            accountID = account.id,
            packages = account.packages,
            displayPreferences = account.displayPreferences,
            summaryGroups = account.summaryGroups,
            at = quotaUpdatedAt,
        ) ?: 0.0) / 1024.0).coerceAtLeast(0.0)

        return WidgetQuotaSnapshot(
            accountID = account.id,
            mobile = WidgetSnapshotBuilder.maskedMobile(account.mobile),
            displayName = account.displayName,
            packageName = account.packageName,
            todayUsageGB = todayUsageGB,
            balanceYuan = account.balanceYuan,
            updatedAt = account.lastUpdatedAt ?: account.balanceUpdatedAt ?: now,
            items = WidgetSnapshotBuilder.makeSingleItems(account, configuration),
        )
    }

    private fun makeDualAccountSnapshot(
        account: UnicomAccount,
        slots: List<com.clxmhcs.chinaunicom.core.model.WidgetDualSlotConfiguration>,
        now: Instant,
    ): WidgetDualAccountSnapshot {
        val quotaUpdatedAt = account.lastUpdatedAt ?: account.remainingQuerySnapshot?.updatedAt ?: now
        val todayUsageGB = ((dailyUsageCalculator.todayUsedMB(
            accountID = account.id,
            packages = account.packages,
            displayPreferences = account.displayPreferences,
            summaryGroups = account.summaryGroups,
            at = quotaUpdatedAt,
        ) ?: 0.0) / 1024.0).coerceAtLeast(0.0)
        val updatedAt = listOfNotNull(
            account.lastUpdatedAt,
            account.balanceUpdatedAt,
            account.remainingQuerySnapshot?.updatedAt,
        ).maxOrNull() ?: now

        return WidgetDualAccountSnapshot(
            accountID = account.id,
            mobileSuffix = WidgetSnapshotBuilder.mobileSuffix(account.mobile),
            todayUsageGB = todayUsageGB,
            balanceYuan = account.balanceYuan,
            updatedAt = updatedAt,
            items = WidgetSnapshotBuilder.makeDualItems(account, slots),
        )
    }

    companion object {
        fun android(
            context: Context,
            notifier: WidgetUpdateNotifier = WidgetUpdateNotifier.NONE,
        ): WidgetSnapshotExporter {
            val app = context.applicationContext
            return WidgetSnapshotExporter(
                configurationRepository = AndroidSettingsRepositories.widgetConfiguration(app),
                dailyUsageCalculator = WidgetDailyUsageCalculator.android(app),
                store = AndroidWidgetSnapshotStore(app),
                notifier = notifier,
            )
        }
    }
}
