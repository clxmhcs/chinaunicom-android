package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillMonthsFetchResult
import com.clxmhcs.chinaunicom.core.network.PhoneBillNetworkClient
import com.clxmhcs.chinaunicom.core.network.UnicomPhoneBillClient
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

interface PhoneBillCredentialValidator {
    fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult
    fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult
}

class UnicomPhoneBillCredentialValidator(
    private val client: PhoneBillNetworkClient = UnicomPhoneBillClient(),
) : PhoneBillCredentialValidator {
    override fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult = client.fetchMonths(credentials)
    override fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult =
        client.fetchDetail(credentials, month)
}

/** M5 credential authority bridge for M8-C phone-bill requests. */
class PhoneBillAccountCredentialLifecycle(
    private val validator: PhoneBillCredentialValidator,
    private val credentialStore: CredentialStore,
) {
    fun hasCredentials(accountID: UUID): Boolean = credentialStore.read(accountID) != null

    fun fetchMonthsValidated(accountID: UUID): PhoneBillMonthsFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchMonths(credentials)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }

    fun fetchDetailValidated(accountID: UUID, month: BillMonth): PhoneBillFetchResult {
        val credentials = credentialStore.read(accountID) ?: throw LoginAccountLifecycleException.MissingCredentials
        val result = validator.fetchDetail(credentials, month)
        result.updatedCredentials?.let { credentialStore.save(accountID, it) }
        return result.copy(updatedCredentials = null)
    }
}
