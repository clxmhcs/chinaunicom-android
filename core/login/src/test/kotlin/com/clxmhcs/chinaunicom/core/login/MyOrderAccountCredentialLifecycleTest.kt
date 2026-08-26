package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyOrderFetchResult
import com.clxmhcs.chinaunicom.core.model.MyOrderPage
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyOrderAccountCredentialLifecycleTest {
    @Test
    fun renewedCredentialsAreSavedAndStrippedBeforeReturningToM9State() {
        val accountID = UUID.randomUUID()
        val original = AccountCredentials("sid=old", "app", "token-old")
        val renewed = AccountCredentials("sid=new", "app", "token-new")
        val store = FakeCredentialStore(mutableMapOf(accountID to original))
        val validator = MyOrderCredentialValidator { _, page, pageSize, credentials ->
            assertEquals(original, credentials)
            assertEquals(2, page)
            assertEquals(15, pageSize)
            MyOrderFetchResult(MyOrderPage(emptyList(), "server-time", false), renewed)
        }
        val lifecycle = MyOrderAccountCredentialLifecycle(validator, store)

        val result = lifecycle.fetchValidated(accountID, "18612345678", 2, 15)

        assertEquals(renewed, store.values[accountID])
        assertNull(result.updatedCredentials)
        assertEquals("server-time", result.page.serverTime)
    }

    private class FakeCredentialStore(
        val values: MutableMap<UUID, AccountCredentials>,
    ) : CredentialStore {
        override fun read(accountID: UUID): AccountCredentials? = values[accountID]
        override fun save(accountID: UUID, credentials: AccountCredentials) {
            values[accountID] = credentials
        }
        override fun delete(accountID: UUID) {
            values.remove(accountID)
        }
        override fun deleteAll() {
            values.clear()
        }
    }
}
