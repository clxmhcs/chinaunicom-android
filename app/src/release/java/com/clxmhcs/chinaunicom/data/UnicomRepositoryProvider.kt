package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.refresh.LoginQuotaRefreshClient
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshCoordinator
import com.clxmhcs.chinaunicom.data.refresh.QuotaRefreshPolicyProvider
import com.clxmhcs.chinaunicom.data.settings.AndroidSettingsRepositories

/** Release wiring for M6 production persistence + quota refresh + persisted refresh settings. */
object UnicomRepositoryProvider {
    fun create(context: Context): UnicomRepository {
        val appContext = context.applicationContext
        val accountRepository = DefaultAccountRepository(
            store = AndroidAccountMetadataStores.accounts(appContext),
        )
        val settingsRepository = AndroidSettingsRepositories.refreshLogic(appContext)
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
        return ProductionUnicomRepository(refreshCoordinator)
    }
}
