package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.network.UnicomAPIException
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyOrderDetailCredentialLifecycleTest {
    @Test fun normalizedCookieIsReturnedOnlyThroughSecureBridgeBoundary() {
        val accountID = UUID.randomUUID()
        val store = TestDetailCredentialStore().apply { save(accountID, AccountCredentials(" a=1; b=2 ", "app", "token")) }
        assertEquals("a=1; b=2", MyOrderDetailCredentialLifecycle(store).requireCookieHeader(accountID))
    }

    @Test fun missingAndEmptyCookieFailClosed() {
        val accountID = UUID.randomUUID()
        val store = TestDetailCredentialStore()
        val lifecycle = MyOrderDetailCredentialLifecycle(store)
        assertTrue(runCatching { lifecycle.requireCookieHeader(accountID) }.exceptionOrNull() is LoginAccountLifecycleException.MissingCredentials)
        store.save(accountID, AccountCredentials("   ", "app", "token"))
        assertTrue(runCatching { lifecycle.requireCookieHeader(accountID) }.exceptionOrNull() is UnicomAPIException.MissingCookie)
    }
}

private class TestDetailCredentialStore : CredentialStore {
    private val values = mutableMapOf<UUID, AccountCredentials>()
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
