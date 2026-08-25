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

/** Release wiring for M6 production repositories, shared AppState and shared balance gate. */
object UnicomRepositoryProvider {
    fun create(context: Context): UnicomRepository {
        val appContext = context.applicationContext
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
        val refreshCoordinator = QuotaRefreshCoordinator(
            accountRepository = accountRepository,
            refreshClient = LoginQuotaRefreshClient(
                lifecycle = LoginAccountLifecycleProvider.create(appContext),
            ),
            runtimeStore = AndroidQuotaRefreshRuntimeStore(appContext),
            policyProvider = QuotaRefreshPolicyProvider {
                settingsRepository.loadQuotaRefreshPolicy()
            },
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
        return ProductionUnicomRepository(quotaRepository, balanceRepository)
    }
}
