package com.clxmhcs.chinaunicom.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ComprehensiveBusinessNetworkContractsTest {
    @Test
    fun orderedBusinessEndpointsMatchFrozenIosSource() {
        assertEquals("https://mxx.client.10010.com", ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ROOT)
        assertEquals("https://loginxx.10010.com/mobileService/onLine.htm", ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ONLINE)
        assertEquals("/servicebusiness/newOrdered/provincialAlloc", ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_ALLOCATE)
        assertEquals("/servicebusiness/newOrdered/queryOrderRelationship", ComprehensiveBusinessEndpoints.ORDERED_BUSINESS_QUERY)
    }

    @Test
    fun phoneBillEndpointsMatchFrozenIosSource() {
        assertEquals("https://m.client.10010.com", ComprehensiveBusinessEndpoints.PHONE_BILL_ROOT)
        assertEquals("https://m.client.10010.com/mobileService/onLine.htm", ComprehensiveBusinessEndpoints.PHONE_BILL_ONLINE)
        assertEquals("/serviceimportantbusiness/phoneBillNew/queryMonths", ComprehensiveBusinessEndpoints.PHONE_BILL_MONTHS)
        assertEquals("/serviceimportantbusiness/phoneBillNew/queryDetail", ComprehensiveBusinessEndpoints.PHONE_BILL_DETAIL)
    }

    @Test
    fun integralEndpointsAndSourceMatchFrozenIosSource() {
        assertEquals("https://activity.10010.com", ComprehensiveBusinessEndpoints.INTEGRAL_ROOT)
        assertEquals("/welfare-mall-front/mobile/show/bj2205/v2/1", ComprehensiveBusinessEndpoints.INTEGRAL_BALANCE)
        assertEquals("/welfare-mall-front/new/integral/queryMonthlyList/v1", ComprehensiveBusinessEndpoints.INTEGRAL_MONTHS)
        assertEquals("/welfare-mall-front/new/integral/querySummaryList/v1", ComprehensiveBusinessEndpoints.INTEGRAL_DETAILS)
        assertEquals("ZXGS97000017640,003", ComprehensiveBusinessEndpoints.INTEGRAL_SOURCE)
    }
}
