package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaResourceStatus
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAccountLifecycleTest {
    private val accountID = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val originalCredentials = AccountCredentials(
        cookie = "session=original",
        appID = "a".repeat(192),
        tokenOnline = "token-original",
    )
    private val renewedCredentials = AccountCredentials(
        cookie = "session=renewed",
        appID = "a".repeat(192),
        tokenOnline = "token-renewed",
    )

    @Test
    fun creationValidatesBeforeSaveAndCommitsOnlyAfterSecureCredentialBinding() {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events = events)
        val validator = QuotaCredentialValidator {
            events += "quota"
            quotaResult(updatedCredentials = renewedCredentials)
        }
        val lifecycle = lifecycle(validator, store)

        val seed = lifecycle.createValidatedAccount(
            mobile = "+86 138-0013-8000",
            credentials = originalCredentials,
        ) {
            events += "commit"
            assertEquals(renewedCredentials, store.read(it.accountID))
        }

        assertEquals(listOf("quota", "save", "commit"), events)
        assertEquals(accountID, seed.accountID)
        assertEquals("13800138000", seed.mobile)
        assertEquals("校园沃派", seed.quota.packageName)
        assertNull(seed.quota.updatedCredentials)
        assertEquals(renewedCredentials, store.read(accountID))
    }

    @Test
    fun failedQuotaValidationNeverAllocatesPersistedCredentialsOrCommitsMetadata() {
        var committed = false
        val store = FakeCredentialStore()
        val validator = QuotaCredentialValidator { throw IllegalStateException("quota rejected") }
        val lifecycle = lifecycle(validator, store)

        assertThrows(IllegalStateException::class.java) {
            lifecycle.createValidatedAccount("13800138000", originalCredentials) {
                committed = true
            }
        }

        assertFalse(committed)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun metadataCommitFailureRollsBackNewCredentialBinding() {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events = events)
        val lifecycle = lifecycle(
            validator = QuotaCredentialValidator { events += "quota"; quotaResult(null) },
            store = store,
        )

        assertThrows(IllegalStateException::class.java) {
            lifecycle.createValidatedAccount("13800138000", originalCredentials) {
                events += "commit"
                throw IllegalStateException("account persistence failed")
            }
        }

        assertEquals(listOf("quota", "save", "commit", "delete"), events)
        assertNull(store.read(accountID))
    }

    @Test
    fun restartStyleLifecycleInstanceRestoresCredentialsWithoutRelogin() {
        val store = FakeCredentialStore()
        lifecycle(QuotaCredentialValidator { quotaResult(null) }, store)
            .createValidatedAccount("13800138000", originalCredentials) { }

        val recreatedLifecycle = LoginAccountLifecycle(
            validator = QuotaCredentialValidator { throw AssertionError("restore must not hit network") },
            credentialStore = store,
        )

        assertEquals(originalCredentials, recreatedLifecycle.restoreCredentials(accountID))
    }

    @Test
    fun refreshUsesStoredCredentialsAndSecurelyOverwritesOnlyRenewedValues() {
        val store = FakeCredentialStore().apply { save(accountID, originalCredentials) }
        var validatorInput: AccountCredentials? = null
        val lifecycle = LoginAccountLifecycle(
            validator = QuotaCredentialValidator { input ->
                validatorInput = input
                quotaResult(updatedCredentials = renewedCredentials)
            },
            credentialStore = store,
        )

        val result = lifecycle.refreshValidatedQuota(accountID)

        assertEquals(originalCredentials, validatorInput)
        assertEquals(renewedCredentials, store.read(accountID))
        assertNull(result.updatedCredentials)
    }

    @Test
    fun refreshWithoutRenewalLeavesStoredCredentialsUnchanged() {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events = events).apply { save(accountID, originalCredentials) }
        events.clear()
        val lifecycle = LoginAccountLifecycle(
            validator = QuotaCredentialValidator { quotaResult(updatedCredentials = null) },
            credentialStore = store,
        )

        lifecycle.refreshValidatedQuota(accountID)

        assertTrue(events.isEmpty())
        assertEquals(originalCredentials, store.read(accountID))
    }

    @Test
    fun deletionRemovesCredentialsAndRestoresThemWhenMetadataDeletionFails() {
        val events = mutableListOf<String>()
        val store = FakeCredentialStore(events = events).apply { save(accountID, originalCredentials) }
        events.clear()
        val lifecycle = LoginAccountLifecycle(
            validator = QuotaCredentialValidator { quotaResult(null) },
            credentialStore = store,
        )

        assertThrows(IllegalStateException::class.java) {
            lifecycle.deleteAccount(accountID) {
                events += "metadata-delete"
                assertNull(store.read(accountID))
                throw IllegalStateException("metadata delete failed")
            }
        }

        assertEquals(listOf("delete", "metadata-delete", "save"), events)
        assertEquals(originalCredentials, store.read(accountID))

        events.clear()
        lifecycle.deleteAccount(accountID) {
            events += "metadata-delete"
            assertNull(store.read(accountID))
        }
        assertEquals(listOf("delete", "metadata-delete"), events)
        assertNull(store.read(accountID))
    }

    @Test
    fun invalidMobileFailsBeforeQuotaOrCredentialMutation() {
        var validationCalled = false
        val store = FakeCredentialStore()
        val lifecycle = lifecycle(
            validator = QuotaCredentialValidator {
                validationCalled = true
                quotaResult(null)
            },
            store = store,
        )

        assertThrows(LoginAccountLifecycleException.InvalidMobile::class.java) {
            lifecycle.createValidatedAccount("10086", originalCredentials) { }
        }
        assertFalse(validationCalled)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun missingStoredCredentialsFailClosedBeforeRefreshNetworkCall() {
        var validationCalled = false
        val lifecycle = LoginAccountLifecycle(
            validator = QuotaCredentialValidator {
                validationCalled = true
                quotaResult(null)
            },
            credentialStore = FakeCredentialStore(),
        )

        assertThrows(LoginAccountLifecycleException.MissingCredentials::class.java) {
            lifecycle.refreshValidatedQuota(accountID)
        }
        assertFalse(validationCalled)
    }

    private fun lifecycle(
        validator: QuotaCredentialValidator,
        store: FakeCredentialStore,
    ) = LoginAccountLifecycle(
        validator = validator,
        credentialStore = store,
        accountIDProvider = { accountID },
    )

    private fun quotaResult(updatedCredentials: AccountCredentials?): QuotaFetchResult = QuotaFetchResult(
        packageName = "校园沃派",
        packages = emptyList(),
        voicePackages = emptyList(),
        remainingQuerySnapshot = null,
        balanceYuan = null,
        unavailableBalanceDetail = null,
        quotaResourceStatus = QuotaResourceStatus.AVAILABLE,
        updatedCredentials = updatedCredentials,
    )
}

private class FakeCredentialStore(
    private val events: MutableList<String>? = null,
) : CredentialStore {
    val values = linkedMapOf<UUID, AccountCredentials>()

    override fun save(accountID: UUID, credentials: AccountCredentials) {
        events?.add("save")
        values[accountID] = credentials
    }

    override fun read(accountID: UUID): AccountCredentials? = values[accountID]

    override fun delete(accountID: UUID) {
        events?.add("delete")
        values.remove(accountID)
    }

    override fun deleteAll() {
        events?.add("delete-all")
        values.clear()
    }
}
