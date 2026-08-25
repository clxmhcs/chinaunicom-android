package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedBusinessAccountCredentialLifecycleTest {
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @Test
    fun renewedCredentialsAreSavedAndStrippedFromBusinessResult() {
        val original = AccountCredentials("old=1", "app", "token")
        val renewed = AccountCredentials("old=1; renewed=2", "newApp", "newToken")
        val store = OrderedBusinessMemoryCredentialStore(mutableMapOf(accountID to original))
        val lifecycle = OrderedBusinessAccountCredentialLifecycle(
            validator = OrderedBusinessCredentialValidator { credentials ->
                assertEquals(original, credentials)
                OrderedBusinessFetchResult(snapshot(), renewed)
            },
            credentialStore = store,
        )

        val result = lifecycle.refreshValidated(accountID)

        assertEquals(renewed, store.read(accountID))
        assertNull(result.updatedCredentials)
        assertEquals("套餐", result.snapshot.title)
    }

    @Test
    fun missingCredentialFailsBeforeNetworkValidator() {
        var called = false
        val lifecycle = OrderedBusinessAccountCredentialLifecycle(
            validator = OrderedBusinessCredentialValidator {
                called = true
                OrderedBusinessFetchResult(snapshot(), null)
            },
            credentialStore = OrderedBusinessMemoryCredentialStore(),
        )

        val error = runCatching { lifecycle.refreshValidated(accountID) }.exceptionOrNull()

        assertTrue(error is LoginAccountLifecycleException.MissingCredentials)
        assertTrue(!called)
    }

    private fun snapshot() = OrderedBusinessSnapshot(
        title = "套餐",
        queryTime = null,
        fetchedAt = Instant.parse("2026-08-25T12:00:00Z"),
        sections = emptyList(),
    )
}

private class OrderedBusinessMemoryCredentialStore(
    private val values: MutableMap<UUID, AccountCredentials> = mutableMapOf(),
) : CredentialStore {
    override fun save(accountID: UUID, credentials: AccountCredentials) {
        values[accountID] = credentials
    }

    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
