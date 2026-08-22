package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.login.LoginAccountLifecycle
import com.clxmhcs.chinaunicom.core.login.UnicomQuotaCredentialValidator

/** App composition root for the M5-D validated login/account credential lifecycle. */
object LoginAccountLifecycleProvider {
    fun create(context: Context): LoginAccountLifecycle = LoginAccountLifecycle(
        validator = UnicomQuotaCredentialValidator(),
        credentialStore = CredentialStoreProvider.create(context),
    )
}
