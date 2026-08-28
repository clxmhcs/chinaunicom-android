package com.clxmhcs.chinaunicom.data

import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetManualRefreshWorkerTest {
    private val accountA = account("00000000-0000-0000-0000-000000000001", "A", enabled = true, sortOrder = 2)
    private val accountB = account("00000000-0000-0000-0000-000000000002", "B", enabled = true, sortOrder = 1)
    private val accountC = account("00000000-0000-0000-0000-000000000003", "C", enabled = false, sortOrder = 0)

    @Test
    fun selectedConfiguredAccountWinsWhenValid() {
        val resolved = WidgetManualRefreshWorker.resolveSingleAccountID(
            selectedAccountID = accountA.id,
            snapshotAccountID = accountB.id,
            accounts = listOf(accountA, accountB, accountC),
        )

        assertEquals(accountA.id, resolved)
    }

    @Test
    fun staleSelectionFallsBackToValidSnapshotAccount() {
        val resolved = WidgetManualRefreshWorker.resolveSingleAccountID(
            selectedAccountID = UUID.fromString("00000000-0000-0000-0000-000000000099"),
            snapshotAccountID = accountA.id,
            accounts = listOf(accountA, accountB, accountC),
        )

        assertEquals(accountA.id, resolved)
    }

    @Test
    fun missingSelectionAndSnapshotUseFirstEnabledSortOrder() {
        val resolved = WidgetManualRefreshWorker.resolveSingleAccountID(
            selectedAccountID = null,
            snapshotAccountID = null,
            accounts = listOf(accountA, accountC, accountB),
        )

        assertEquals(accountB.id, resolved)
    }

    @Test
    fun noEnabledAccountsFallsBackToFirstSortOrder() {
        val disabledA = accountA.copy(isEnabled = false, sortOrder = 5)
        val disabledB = accountB.copy(isEnabled = false, sortOrder = 3)
        val disabledC = accountC.copy(isEnabled = false, sortOrder = 1)
        val resolved = WidgetManualRefreshWorker.resolveSingleAccountID(
            selectedAccountID = null,
            snapshotAccountID = null,
            accounts = listOf(disabledA, disabledB, disabledC),
        )

        assertEquals(disabledC.id, resolved)
    }

    private fun account(
        id: String,
        name: String,
        enabled: Boolean,
        sortOrder: Int,
    ) = UnicomAccount(
        id = UUID.fromString(id),
        displayName = name,
        mobile = "10000000000",
        isEnabled = enabled,
        sortOrder = sortOrder,
    )
}
