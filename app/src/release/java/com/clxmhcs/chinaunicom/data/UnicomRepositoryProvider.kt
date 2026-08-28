package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.balance.AndroidBalanceConfigurationStore
import com.clxmhcs.chinaunicom.data.balance.AndroidSharedBalanceCacheStores
import com.clxmhcs.chinaunicom.data.balance.DefaultBalanceRepository
import com.clxmhcs.chinaunicom.data.balance.LoginBalanceRefreshClient
import com.clxmhcs.chinaunicom.data.refresh.DefaultQuotaRepository
import com.clxmhcs.chinaunicom.data.refresh.LoginQuotaRefreshClient
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshCoordinator
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicyProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories
import com.clxmhcs.chinaunicom.data.settings.BalanceRefreshIntervalSynchronizer
import com.clxmhcs.chinaunicom.widget.GlanceWidgetUpdateNotifier
import com.clxmhcs.chinaunicom.widget.WidgetSnapshotExporter

/** Release wiring uses one process-wide production Repository/AppState authority. */
object UnicomRepositoryProvider {
    @Volatile
    private var instance: UnicomRepository? = null

    fun create(context: Context): UnicomRepository = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext).also { instance = it }
    }

    private fun build(appContext: Context): UnicomRepository {
        val accountRepository = DefaultAccountRepository(
            store = AndroidAccountMetadataStores.accounts(appContext),
        )
        val sharedBalanceCache = AndroidSharedBalanceCacheStores.create(appContext)
        val settingsRepository = AndroidSettingsRepositories.refreshLogic(
            appContext,
            balanceIntervalSynchronizer = BalanceRefreshIntervalSynchronizer { minutes ->
                sharedBalanceCache.setRefreshIntervalMinutes(minutes)
            },
        )
        val widgetSnapshotExporter = WidgetSnapshotExporter.android(
            context = appContext,
            notifier = GlanceWidgetUpdateNotifier(appContext),
        )
        val refreshCoordinator = QuotaRefreshCoordinator(
            accountRepository = accountRepository,
            refreshClient = LoginQuotaRefreshClient(
                lifecycle = LoginAccountLifecycleProvider.create(appContext),
            ),
            runtimeStore = AndroidQuotaRefreshRuntimeStore(appContext),
            policyProvider = QuotaRefreshPolicyProvider {
                settingsRepository.loadQuotaRefreshPolicy()
            },
            accountsCommittedObserver = widgetSnapshotExporter::onAccountsCommitted,
        )
        val quotaRepository = DefaultQuotaRepository(refreshCoordinator)
        val balanceRepository = DefaultBalanceRepository(
            accountState = refreshCoordinator,
            refreshClient = LoginBalanceRefreshClient(
                lifecycle = BalanceAccountCredentialLifecycleProvider.create(appContext),
            ),
            sharedCache = sharedBalanceCache,
            configurationStore = AndroidBalanceConfigurationStore(appContext),
            settingsRepository = settingsRepository,
        )
        return ProductionUnicomRepository(
            quotaRepository = quotaRepository,
            balanceRepository = balanceRepository,
            reloadAccountsFromPersistenceAction = {
                val persisted = accountRepository.loadAccounts()
                refreshCoordinator.updateAccountsFromBalance { persisted }
                if (balanceRepository.state.value.homeBalanceAccountID == null) {
                    balanceRepository.setHomeBalanceAccountID(
                        persisted.firstOrNull { it.isEnabled }?.id,
                    )
                }
                widgetSnapshotExporter.export(persisted)
            },
        )
    }
}
