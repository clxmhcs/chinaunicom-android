package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository

/** Release wiring for the M6 production metadata repository graph. */
object UnicomRepositoryProvider {
    fun create(context: Context): UnicomRepository {
        val accountRepository = DefaultAccountRepository(
            store = AndroidAccountMetadataStores.accounts(context),
        )
        return ProductionUnicomRepository(accountRepository)
    }
}
