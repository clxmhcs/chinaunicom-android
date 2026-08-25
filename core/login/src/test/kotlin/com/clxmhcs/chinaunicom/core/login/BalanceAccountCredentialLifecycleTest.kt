package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BalanceFetchResult
import com.clxmhcs.chinaunicom.core.model.UnavailableBalanceDetail
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceAccountCredentialLifecycleTest {
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun refreshUsesSecureCredentialsPersistsRenewalAndStripsItFromResult() {
        val original = AccountCredentials("cookie-old", "app", "token-old")
        val renewed = AccountCredentials("cookie-new", "app", "token-new")
        val store = BalanceLifecycleCredentialStore(mutableMapOf(accountID to original))
        var seen: AccountCredentials? = null
        val lifecycle = BalanceAccountCredentialLifecycle(
            validator = BalanceCredentialValidator { credentials ->
                seen = credentials
                BalanceFetchResult(
                    balanceYuan = 88.66,
                    unavailableBalanceDetail = emptyDetail(),
                    updatedCredentials = renewed,
                )
            },
            credentialStore = store,
        )

        assertTrue(lifecycle.hasCredentials(accountID))
        val result = lifecycle.refreshValidatedBalance(accountID)

        assertEquals(original, seen)
        assertEquals(renewed, store.values[accountID])
        assertEquals(88.66, result.balanceYuan!!, 0.0)
        assertNull(result.updatedCredentials)
    }

    @Test
    fun missingCredentialIsReportedBeforeNetwork() {
        val store = BalanceLifecycleCredentialStore()
        var calls = 0
        val lifecycle = BalanceAccountCredentialLifecycle(
            validator = BalanceCredentialValidator {
                calls += 1
                error("must not call")
            },
            credentialStore = store,
        )

        assertFalse(lifecycle.hasCredentials(accountID))
        val error = runCatching { lifecycle.refreshValidatedBalance(accountID) }.exceptionOrNull()
        assertTrue(error is LoginAccountLifecycleException.MissingCredentials)
        assertEquals(0, calls)
    }

    private fun emptyDetail() = UnavailableBalanceDetail(
        currentBalance = "88.66",
        unavailableLimitFee = null,
        frozenFee = null,
        totalUnavailable = null,
        limitItems = emptyList(),
        frozenItems = emptyList(),
    )
}

private class BalanceLifecycleCredentialStore(
    val values: MutableMap<UUID, AccountCredentials> = mutableMapOf(),
) : CredentialStore {
    override fun save(accountID: UUID, credentials: AccountCredentials) {
        values[accountID] = credentials
    }

    override fun read(accountID: UUID): AccountCredentials? = values[accountID]

    override fun delete(accountID: UUID) {
        values.remove(accountID)
    }

    override fun deleteAll() {
        values.clear()
    }
}
