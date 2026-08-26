package com.clxmhcs.chinaunicom.data.myorder

import com.clxmhcs.chinaunicom.core.model.MyOrderDetailContent
import com.clxmhcs.chinaunicom.core.model.MyOrderDetailMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyOrderDetailParserTest {
    private val parser = MyOrderDetailParser()

    @Test fun parsesBusinessAndSubProducts() {
        val detail = """{"code":"0000","data":{"orderId":"OID-1","businessName":"营业厅订单","productName":"主套餐","mobile":"13800000000"}}"""
        val products = """{"code":"0000","data":{"rows":[{"productId":"P1","productName":"副产品","statusAttributeName":"正常","startTime":"2026-01-01","endTime":"2026-12-31"}]}}"""
        val bridge = """{"detail":${quoted(detail)},"products":${quoted(products)}}"""
        val content = parser.parse(bridge, MyOrderDetailMode.BUSINESS) as MyOrderDetailContent.Business
        assertEquals("OID-1", content.detail.orderID)
        assertEquals("主套餐", content.detail.productName)
        assertEquals(1, content.detail.subProducts.size)
        assertEquals("P1", content.detail.subProducts.single().id)
    }

    @Test fun parsesRenewalAmountAndAcceptancePriority() {
        val detail = """{"result":"0000","info":[{"orderNo":"R1","commName":"融合续约","serviceType":"29","createTime":"A","newActionStartTime":"B","payCompleteTime":"C","updateTime":"D","incomeTotalMoney":"1999"}]}"""
        val bridge = """{"detail":${quoted(detail)}}"""
        val content = parser.parse(bridge, MyOrderDetailMode.RENEWAL) as MyOrderDetailContent.Renewal
        assertEquals(1999, content.detail.amountFen)
        assertEquals("D", content.detail.acceptanceTime)
    }

    @Test fun invalidBusinessServerCodePreservesMessage() {
        val detail = """{"code":"9999","message":"订单不存在"}"""
        val bridge = """{"detail":${quoted(detail)}}"""
        val error = runCatching { parser.parse(bridge, MyOrderDetailMode.BUSINESS) }.exceptionOrNull()
        assertTrue(error is MyOrderDetailParsingException.InvalidServerResponse)
        assertEquals("订单不存在", error?.message)
    }

    private fun quoted(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
