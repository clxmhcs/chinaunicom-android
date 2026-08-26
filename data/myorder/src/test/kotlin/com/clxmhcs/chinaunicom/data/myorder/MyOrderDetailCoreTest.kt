package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.login.MyOrderDetailCredentialLifecycle
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.MyOrder
import com.clxmhcs.chinaunicom.core.model.MyOrderAction
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailContent
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailMode
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.net.URI
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyOrderDetailCoreTest {
    @Test fun requestFactoryUsesSourceModeAndEncodedQueryRules() {
        val accountID = UUID.randomUUID()
        val renewal = order("r", "fallback", "D18", "https://upayxx.10010.com/npfwap/broadOrdersDetailInit?redirect=x%26orderNo%3DR-100%26serviceType%3D29")
        val request = MyOrderDetailRequestFactory.create(accountID, renewal)
        assertEquals(MyOrderDetailMode.RENEWAL, request.mode)
        assertEquals("R-100", request.orderID)
        assertEquals("29", request.serviceType)

        val business = order("b", "fallback-b", "D13", "https://omo.10010.com/dbh-evaluate-fe/index?orderId=B-200")
        val businessRequest = MyOrderDetailRequestFactory.create(accountID, business)
        assertEquals(MyOrderDetailMode.BUSINESS, businessRequest.mode)
        assertEquals("B-200", businessRequest.orderID)
    }

    @Test fun unsupportedOrderDoesNotInventPaymentFallback() {
        val payment = order("p", "P1", "D10", "https://example.com/detail?orderId=P1")
        assertTrue(runCatching { MyOrderDetailRequestFactory.create(UUID.randomUUID(), payment) }.exceptionOrNull() is MyOrderDetailPreparationException.Unsupported)
    }

    @Test fun bridgeContractFreezesReadyUrlsAndEndpoints() {
        assertTrue(MyOrderDetailWebBridgeContract.isReadyURL(URI("https://omo.10010.com/dbh-evaluate-fe/page"), MyOrderDetailMode.BUSINESS))
        assertTrue(MyOrderDetailWebBridgeContract.isReadyURL(URI("https://upayxx.10010.com/npfwap/broadOrdersDetail/result"), MyOrderDetailMode.RENEWAL))
        assertFalse(MyOrderDetailWebBridgeContract.isReadyURL(URI("https://upayxx.10010.com/npfwap/broadOrdersDetailInit"), MyOrderDetailMode.RENEWAL))

        val business = MyOrderDetailRequestFactory.create(UUID.randomUUID(), order("b", "B1", "D13", "https://omo.10010.com/dbh-evaluate-fe/page?orderId=B1"))
        val businessScript = MyOrderDetailWebBridgeContract.javaScript(business)
        assertTrue(businessScript.contains("/udbh/rest/portal/qryEvaluateOrderInfoByOrderId"))
        assertTrue(businessScript.contains("sourcePage: 'CJ_SOU_20000'"))
        assertTrue(businessScript.contains("/udbh/rest/portal/querySubProducts"))
        assertTrue(businessScript.contains("pageSize: 100"))

        val renewal = MyOrderDetailRequestFactory.create(UUID.randomUUID(), order("r", "R1", "D18", "https://upayxx.10010.com/npfwap/broadOrdersDetail?orderNo=R1"))
        val renewalScript = MyOrderDetailWebBridgeContract.javaScript(renewal)
        assertTrue(renewalScript.contains("broaRenewalInfo"))
        assertTrue(renewalScript.contains("serviceType: \"29\""))
    }

    @Test fun storeKeepsCookieOutOfStateAndIgnoresStaleBridgeResult() {
        val account = UnicomAccount(displayName = "A", mobile = "13800000000")
        val credentials = TestStoreCredentialStore().apply { save(account.id, AccountCredentials("a=1", "app", "token")) }
        val store = MyOrderDetailStore(MyOrderDetailCredentialLifecycle(credentials))
        val order = order("b", "B1", "D13", "https://omo.10010.com/dbh-evaluate-fe/page?orderId=B1")
        store.prepare(account, order)
        val request = store.state.value.request!!
        assertTrue(store.state.value.isLoading)
        assertEquals(account.id, request.accountID)

        val bridge = Result.success("{\"detail\":\"{\\\"code\\\":\\\"0000\\\",\\\"data\\\":{\\\"productName\\\":\\\"套餐\\\"}}\"}")
        store.receiveBridgeText(bridge, UUID.randomUUID())
        assertTrue(store.state.value.isLoading)
        assertNull(store.state.value.content)

        store.receiveBridgeText(bridge, request.id)
        assertFalse(store.state.value.isLoading)
        val content = store.state.value.content as MyOrderDetailContent.Business
        assertEquals("套餐", content.detail.productName)
        assertEquals("业务订单详情", store.headerTitle)
    }

    private fun order(id: String, orderID: String, sourceCode: String?, url: String): MyOrder = MyOrder(
        id = id, orderID = orderID, encodedOrderID = null, sourceCode = sourceCode, sourceName = null,
        statusCode = null, statusName = "已完成", nodeCode = null, nodeName = null, createdAtText = "2026-08-26",
        channelName = null, phoneNumber = null, maskedContactNumber = null, accountNumber = null, address = null,
        goodsName = null, tradeType = null, sceneType = null, originNodeName = null, members = emptyList(),
        actions = listOf(MyOrderAction(id = "a", name = "查看详情", type = null, url = url, postParameter = null)), tradeTags = emptyList(),
    )
}

private class TestStoreCredentialStore : CredentialStore {
    private val values = mutableMapOf<UUID, AccountCredentials>()
    override fun save(accountID: UUID, credentials: AccountCredentials) { values[accountID] = credentials }
    override fun read(accountID: UUID): AccountCredentials? = values[accountID]
    override fun delete(accountID: UUID) { values.remove(accountID) }
    override fun deleteAll() { values.clear() }
}
