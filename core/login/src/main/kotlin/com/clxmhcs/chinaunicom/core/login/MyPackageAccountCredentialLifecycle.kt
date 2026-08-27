package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyPackageFetchResult
import com.clxmhcs.chinaunicom.core.network.MyPackageNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomMyPackageClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

fun interface MyPackageCredentialValidator {
    fun fetch(credentials: AccountCredentials): MyPackageFetchResult
}

class UnicomMyPackageCredentialValidator(
    private val client: MyPackageNetworkClient = UnicomMyPackageClient(),
) : MyPackageCredentialValidator {
    override fun fetch(credentials: AccountCredentials): MyPackageFetchResult = client.fetch(credentials)
}

class MyPackageAccountCredentialLifecycle(
    private val validator: MyPackageCredentialValidator,
    private val credentialStore: CredentialStore,
) {
    fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    fun refreshValidated(accountID: UUID): MyPackageFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetch(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
