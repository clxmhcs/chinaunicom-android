package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.PhoneBillFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillMonthsFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSummary
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneBillAccountCredentialLifecycleTest {
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val month = BillMonth("2026", "08")

    @Test
    fun renewedCredentialsAreSavedAndStrippedBeforeStoreBoundary() {
        val original = AccountCredentials("old=1", "app", "token")
        val renewed = AccountCredentials("old=1; renewed=2", "newApp", "newToken")
        val store = PhoneBillMemoryCredentialStore(mutableMapOf(accountID to original))
        val lifecycle = PhoneBillAccountCredentialLifecycle(
            validator = object : PhoneBillCredentialValidator {
                override fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult =
                    PhoneBillMonthsFetchResult(listOf(month), renewed)
                override fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult =
                    PhoneBillFetchResult(snapshot(month), renewed)
            },
            credentialStore = store,
        )

        val months = lifecycle.fetchMonthsValidated(accountID)
        assertEquals(renewed, store.read(accountID))
        assertNull(months.updatedCredentials)

        val detail = lifecycle.fetchDetailValidated(accountID, month)
        assertEquals(renewed, store.read(accountID))
        assertNull(detail.updatedCredentials)
        assertEquals("202608", detail.snapshot.month.key)
    }

    @Test
    fun missingCredentialsFailBeforeValidator() {
        var called = false
        val lifecycle = PhoneBillAccountCredentialLifecycle(
            validator = object : PhoneBillCredentialValidator {
                override fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult {
                    called = true
                    return PhoneBillMonthsFetchResult(listOf(month), null)
                }
                override fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult {
                    called = true
                    return PhoneBillFetchResult(snapshot(month), null)
                }
            },
            credentialStore = PhoneBillMemoryCredentialStore(),
        )

        val error = runCatching { lifecycle.fetchMonthsValidated(accountID) }.exceptionOrNull()
        assertTrue(error is LoginAccountLifecycleException.MissingCredentials)
        assertTrue(!called)
    }

    private fun snapshot(month: BillMonth) = PhoneBillSnapshot(
        month = month,
        queryTime = null,
        summary = PhoneBillSummary("0.00", "0.00", "0.00", "0.00", "0.00", "0.00", null, null, null, null),
        userBills = emptyList(),
        accountSections = emptyList(),
        fetchedAt = Instant.parse("2026-08-25T12:00:00Z"),
        parserVersion = PhoneBillSnapshot.CURRENT_PARSER_VERSION,
    )
}

private class PhoneBillMemoryCredentialStore(
    private val values: MutableMap<UUID, AccountCredentials> = mutableMapOf(),
) : CredentialStore {
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
