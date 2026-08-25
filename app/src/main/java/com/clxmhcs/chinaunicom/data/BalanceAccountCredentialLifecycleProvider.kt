package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.BalanceAccountCredentialLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomBalanceCredentialValidator

/** App composition root for the M6-D secure balance credential lifecycle. */
object BalanceAccountCredentialLifecycleProvider {
    fun create(context: Context): BalanceAccountCredentialLifecycle = BalanceAccountCredentialLifecycle(
        validator = UnicomBalanceCredentialValidator(),
        credentialStore = CredentialStoreProvider.create(context),
    )
}
