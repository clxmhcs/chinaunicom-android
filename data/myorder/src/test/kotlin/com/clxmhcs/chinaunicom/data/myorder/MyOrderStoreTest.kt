package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.login.MyOrderRequestLifecycle
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderFetchResult
import com.clxmhcs.chinaunicom.core.model.MyOrderPage
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyOrderStoreTest {
    @Test
    fun entryPolicyCanRequireManualLoadButForceStillQueries() = runBlocking {
        val account = account("18600000001")
        val lifecycle = FakeLifecycle(mapOf(1 to result(listOf(order("o1")), hasMore = false)))
        val store = DefaultMyOrderStore(
            lifecycle = lifecycle,
            entryRefreshPolicy = MyOrderEntryRefreshPolicy { false },
            ioDispatcher = Dispatchers.Unconfined,
        )

        store.load(account)
        assertEquals(0, lifecycle.calls.size)
        assertTrue(store.state.value.orders.isEmpty())

        store.load(account, force = true)
        assertEquals(1, lifecycle.calls.size)
        assertEquals(listOf("o1"), store.state.value.orders.map { it.id })
        assertFalse(store.state.value.hasMore)
    }

    @Test
    fun paginationMergesByStableOrderIDAndAdvancesUntilServerStops() = runBlocking {
        val account = account("18600000001")
        val lifecycle = FakeLifecycle(
            mapOf(
                1 to result(listOf(order("o1"), order("o2")), hasMore = true, serverTime = "t1"),
                2 to result(listOf(order("o2"), order("o3")), hasMore = false, serverTime = "t2"),
            ),
        )
        val store = DefaultMyOrderStore(lifecycle, ioDispatcher = Dispatchers.Unconfined, pageSize = 2)

        store.load(account)
        store.loadMore(account)
        store.loadMore(account)

        assertEquals(listOf(1, 2), lifecycle.calls.map { it.page })
        assertEquals(listOf("o1", "o2", "o3"), store.state.value.orders.map { it.id })
        assertEquals("t2", store.state.value.serverTime)
        assertFalse(store.state.value.hasMore)
        assertNull(store.state.value.errorMessage)
    }

    @Test
    fun switchingAccountClearsPreviousPaginationStateBeforeLoadingNewAccount() = runBlocking {
        val first = account("18600000001")
        val second = account("18600000002")
        val lifecycle = AccountAwareLifecycle(
            mapOf(
                first.id to result(listOf(order("first")), hasMore = false),
                second.id to result(listOf(order("second")), hasMore = false),
            ),
        )
        val store = DefaultMyOrderStore(lifecycle, ioDispatcher = Dispatchers.Unconfined)

        store.load(first)
        store.load(second)

        assertEquals(second.id, store.state.value.activeAccountID)
        assertEquals(listOf("second"), store.state.value.orders.map { it.id })
        assertEquals(listOf(first.id, second.id), lifecycle.calls)
    }

    private data class Call(val accountID: UUID, val page: Int, val pageSize: Int)

    private class FakeLifecycle(
        private val pages: Map<Int, MyOrderFetchResult>,
    ) : MyOrderRequestLifecycle {
        val calls = mutableListOf<Call>()

        override fun fetchValidated(accountID: UUID, mobile: String, page: Int, pageSize: Int): MyOrderFetchResult {
            calls += Call(accountID, page, pageSize)
            return pages[page] ?: error("Missing fake page $page")
        }
    }

    private class AccountAwareLifecycle(
        private val results: Map<UUID, MyOrderFetchResult>,
    ) : MyOrderRequestLifecycle {
        val calls = mutableListOf<UUID>()

        override fun fetchValidated(accountID: UUID, mobile: String, page: Int, pageSize: Int): MyOrderFetchResult {
            calls += accountID
            return results.getValue(accountID)
        }
    }

    private fun account(mobile: String) = UnicomAccount(
        displayName = mobile,
        mobile = mobile,
    )

    private fun result(
        orders: List<MyOrder>,
        hasMore: Boolean,
        serverTime: String? = null,
    ) = MyOrderFetchResult(
        page = MyOrderPage(orders = orders, serverTime = serverTime, hasMore = hasMore),
        updatedCredentials = null,
    )

    private fun order(id: String) = MyOrder(
        id = id,
        orderID = id,
        encodedOrderID = null,
        sourceCode = null,
        sourceName = null,
        statusCode = null,
        statusName = "",
        nodeCode = null,
        nodeName = null,
        createdAtText = "",
        channelName = null,
        phoneNumber = null,
        maskedContactNumber = null,
        accountNumber = null,
        address = null,
        goodsName = null,
        tradeType = null,
        sceneType = null,
        originNodeName = null,
        members = emptyList(),
        actions = emptyList(),
        tradeTags = emptyList(),
    )
}
