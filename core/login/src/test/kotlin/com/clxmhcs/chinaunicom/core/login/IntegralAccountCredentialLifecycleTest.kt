package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegralAccountCredentialLifecycleTest {
    private val accountID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val fetchedAt = Instant.parse("2026-08-26T03:00:00Z")
    private val query = IntegralDetailQuery("1", "3", null, "奖励积分")

    @Test
    fun renewedCredentialsAreSavedAndStrippedBeforeOrdinaryIntegralState() {
        val original = AccountCredentials("old=1", "app", "token")
        val renewed = AccountCredentials("old=1; renewed=2", "app", "newToken")
        val store = IntegralMemoryCredentialStore(mutableMapOf(accountID to original))
        val lifecycle = IntegralAccountCredentialLifecycle(
            validator = object : IntegralCredentialValidator {
                override fun fetchOverview(
                    credentials: AccountCredentials,
                    mobile: String,
                    fetchedAt: Instant,
                ) = IntegralFetchResult(snapshot(fetchedAt), renewed)

                override fun fetchDetails(
                    query: IntegralDetailQuery,
                    credentials: AccountCredentials,
                    mobile: String,
                ) = IntegralDetailsFetchResult(listOf(detail()), renewed)
            },
            credentialStore = store,
        )

        val overview = lifecycle.fetchOverviewValidated(accountID, "13800138000", fetchedAt)
        assertEquals(renewed, store.read(accountID))
        assertNull(overview.updatedCredentials)
        assertEquals(1000, overview.snapshot.totalAvailable)

        val details = lifecycle.fetchDetailsValidated(accountID, "13800138000", query)
        assertEquals(renewed, store.read(accountID))
        assertNull(details.updatedCredentials)
        assertEquals("积分明细", details.items.single().title)
    }

    @Test
    fun missingCredentialsFailBeforeIntegralValidator() {
        var called = false
        val lifecycle = IntegralAccountCredentialLifecycle(
            validator = object : IntegralCredentialValidator {
                override fun fetchOverview(
                    credentials: AccountCredentials,
                    mobile: String,
                    fetchedAt: Instant,
                ): IntegralFetchResult {
                    called = true
                    return IntegralFetchResult(snapshot(fetchedAt), null)
                }

                override fun fetchDetails(
                    query: IntegralDetailQuery,
                    credentials: AccountCredentials,
                    mobile: String,
                ): IntegralDetailsFetchResult {
                    called = true
                    return IntegralDetailsFetchResult(emptyList(), null)
                }
            },
            credentialStore = IntegralMemoryCredentialStore(),
        )

        val error = runCatching {
            lifecycle.fetchOverviewValidated(accountID, "13800138000", fetchedAt)
        }.exceptionOrNull()
        assertTrue(error is LoginAccountLifecycleException.MissingCredentials)
        assertTrue(!called)
    }

    private fun snapshot(at: Instant) = IntegralSnapshot(
        totalAvailable = 1000,
        communication = 200,
        reward = 300,
        directional = null,
        expiredAndExpiringReward = 0,
        expiringThisMonth = 10,
        expiringCommunication = 0,
        expiringReward = 10,
        expirationDay = null,
        couponCount = 0,
        provinceCode = null,
        packageID = null,
        isUnicom = null,
        months = emptyList(),
        fetchedAt = at,
        parserVersion = IntegralSnapshot.CURRENT_PARSER_VERSION,
    )

    private fun detail() = IntegralDetailItem(
        typeChar = "3",
        scoreType = "1",
        title = "积分明细",
        scoreValue = "88",
        createTime = null,
        returnTime = null,
        endTime = null,
        orderTime = null,
        channelName = null,
        expireTime = null,
        expireTag = null,
    )
}

private class IntegralMemoryCredentialStore(
    private val values: MutableMap<UUID, AccountCredentials> = mutableMapOf(),
) : CredentialStore {
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
