package com.clxmhcs.chinaunicom.data.broadbandaccount

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadbandAccountCoreTest {
    @Test
    fun codec_roundTripsOrdinaryMetadataWithoutCredentialFields() {
        val account = BroadbandAccountInfo(
            id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            serviceNumber = "053100001234",
            displayName = "家庭宽带",
            idCardLastSix = "123456",
            locationName = "山东",
            provinceCode = "017",
            cityCode = "170",
            areaCode = "0531",
            createdAt = Instant.parse("2026-08-27T00:00:00Z"),
            updatedAt = Instant.parse("2026-08-27T01:00:00Z"),
        )
        val codec = BroadbandAccountJsonCodec()
        val encoded = codec.encode(listOf(account))
        assertFalse(encoded.contains("cookie", ignoreCase = true))
        assertFalse(encoded.contains("token_online", ignoreCase = true))
        assertFalse(encoded.contains("appId", ignoreCase = true))
        assertEquals(listOf(account), codec.decode(encoded))
    }

    @Test
    fun adapter_preservesIdentityAndUsesBroadbandBusinessShape() {
        val account = sampleAccount()
        val adapted = account.toUnicomAccount()
        assertEquals(account.id, adapted.id)
        assertEquals(account.serviceNumber, adapted.mobile)
        assertEquals("宽带账号", adapted.packageName)
        assertTrue(adapted.isEnabled)
        assertEquals("家庭宽带", adapted.displayName)
    }

    @Test
    fun lifecycle_validatesBeforeSavingAndPersistsRenewedCredentialSeparately() {
        val repository = FakeRepository()
        val credentialStore = FakeCredentialStore()
        val renewed = AccountCredentials("renewed-cookie", "app", "renewed-token")
        val lifecycle = BroadbandAccountLifecycle(
            repository = repository,
            credentialStore = credentialStore,
            validator = BroadbandCredentialValidator { renewed },
            clock = Clock.fixed(Instant.parse("2026-08-27T02:00:00Z"), ZoneOffset.UTC),
        )
        val record = lifecycle.validateAndSave(
            BroadbandAccountDraft(
                serviceNumber = " 053100001234 ",
                displayName = " 家庭宽带 ",
                idCardLastSix = "12a3456",
                locationName = " 山东 ",
                provinceCode = "017",
                cityCode = "170",
                areaCode = "0531",
            ),
            AccountCredentials(" entered-cookie ", " app ", " token "),
        )
        assertEquals("053100001234", record.serviceNumber)
        assertEquals("123456", record.idCardLastSix)
        assertEquals(renewed, credentialStore.read(record.id))
        assertEquals(record, repository.loadAccounts().single())
    }

    @Test
    fun lifecycle_preservesExistingIDAndCreatedAtWhenSameServiceNumberIsRevalidated() {
        val existing = sampleAccount()
        val repository = FakeRepository(mutableListOf(existing))
        val credentialStore = FakeCredentialStore()
        val lifecycle = BroadbandAccountLifecycle(
            repository,
            credentialStore,
            BroadbandCredentialValidator { it },
            Clock.fixed(Instant.parse("2026-08-28T00:00:00Z"), ZoneOffset.UTC),
        )
        val saved = lifecycle.validateAndSave(
            BroadbandAccountDraft(existing.serviceNumber, "新名称", "123456"),
            AccountCredentials("cookie", null, null),
        )
        assertEquals(existing.id, saved.id)
        assertEquals(existing.createdAt, saved.createdAt)
        assertNotEquals(existing.updatedAt, saved.updatedAt)
        assertEquals("新名称", saved.displayName)
    }

    @Test
    fun lifecycle_rollsCredentialBackWhenMetadataWriteFails() {
        val existing = sampleAccount()
        val repository = FakeRepository(mutableListOf(existing)).apply { failWrites = true }
        val credentialStore = FakeCredentialStore().apply {
            save(existing.id, AccountCredentials("old-cookie", "old-app", "old-token"))
        }
        val lifecycle = BroadbandAccountLifecycle(
            repository,
            credentialStore,
            BroadbandCredentialValidator { AccountCredentials("new-cookie", "new-app", "new-token") },
        )
        runCatching {
            lifecycle.validateAndSave(
                BroadbandAccountDraft(existing.serviceNumber, "changed", "123456"),
                AccountCredentials("entered", null, null),
            )
        }
        assertEquals(AccountCredentials("old-cookie", "old-app", "old-token"), credentialStore.read(existing.id))
        assertEquals(existing, repository.loadAccounts().single())
    }

    @Test
    fun remove_deletesMetadataAndCredentialForSameUUID() {
        val existing = sampleAccount()
        val repository = FakeRepository(mutableListOf(existing))
        val credentials = FakeCredentialStore().apply { save(existing.id, AccountCredentials("c", null, null)) }
        BroadbandAccountLifecycle(repository, credentials, BroadbandCredentialValidator { it }).remove(existing.id)
        assertTrue(repository.loadAccounts().isEmpty())
        assertNull(credentials.read(existing.id))
    }

    private fun sampleAccount() = BroadbandAccountInfo(
        id = UUID.fromString("22222222-2222-2222-2222-222222222222"),
        serviceNumber = "053100001234",
        displayName = "家庭宽带",
        idCardLastSix = "123456",
        locationName = "山东",
        provinceCode = "017",
        cityCode = "170",
        areaCode = "0531",
        createdAt = Instant.parse("2026-08-26T00:00:00Z"),
        updatedAt = Instant.parse("2026-08-26T01:00:00Z"),
    )
}

private class FakeRepository(
    private val items: MutableList<BroadbandAccountInfo> = mutableListOf(),
) : BroadbandAccountRepository {
    var failWrites = false

    override fun loadAccounts(): List<BroadbandAccountInfo> = items.toList()

    override fun upsert(account: BroadbandAccountInfo) {
        if (failWrites) error("metadata-write-failed")
        items.removeAll { it.id == account.id || it.serviceNumber == account.serviceNumber }
        items += account
    }

    override fun remove(accountID: UUID) {
        if (failWrites) error("metadata-write-failed")
        items.removeAll { it.id == accountID }
    }

    override fun clear() {
        if (failWrites) error("metadata-write-failed")
        items.clear()
    }
}

private class FakeCredentialStore : CredentialStore {
    private val values = mutableMapOf<UUID, AccountCredentials>()
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
