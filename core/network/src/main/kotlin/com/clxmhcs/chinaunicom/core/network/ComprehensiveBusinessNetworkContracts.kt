package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralFetchResult
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillFetchResult
import com.clxmhcs.chinaunicom.core.model.PhoneBillMonthsFetchResult
import java.time.Instant

/**
 * M8 source-derived endpoint contract. These constants intentionally preserve the observable iOS
 * protocol instead of inventing a separate Android carrier protocol.
 */
object ComprehensiveBusinessEndpoints {
    const val ORDERED_BUSINESS_ROOT = "https://mxx.client.10010.com"
    const val ORDERED_BUSINESS_ONLINE = "https://loginxx.10010.com/mobileService/onLine.htm"
    const val ORDERED_BUSINESS_ALLOCATE = "/servicebusiness/newOrdered/provincialAlloc"
    const val ORDERED_BUSINESS_QUERY = "/servicebusiness/newOrdered/queryOrderRelationship"

    const val PHONE_BILL_ROOT = "https://m.client.10010.com"
    const val PHONE_BILL_ONLINE = UnicomModernSessionRenewalProfile.ONLINE_URL
    const val PHONE_BILL_MONTHS = "/serviceimportantbusiness/phoneBillNew/queryMonths"
    const val PHONE_BILL_DETAIL = "/serviceimportantbusiness/phoneBillNew/queryDetail"

    const val INTEGRAL_ROOT = "https://activity.10010.com"
    const val INTEGRAL_BALANCE = "/welfare-mall-front/mobile/show/bj2205/v2/1"
    const val INTEGRAL_MONTHS = "/welfare-mall-front/new/integral/queryMonthlyList/v1"
    const val INTEGRAL_DETAILS = "/welfare-mall-front/new/integral/querySummaryList/v1"
    const val INTEGRAL_SOURCE = "ZXGS97000017640,003"
}

/** M8-B will implement this with the frozen iOS ordered-business session and parsing behavior. */
fun interface OrderedBusinessNetworkClient {
    fun fetch(credentials: AccountCredentials): OrderedBusinessFetchResult
}

/** M8-C will implement this with the frozen iOS phone-bill session and parsing behavior. */
interface PhoneBillNetworkClient {
    fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult
    fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult
}

/** M8-D will implement this with the frozen iOS integral session and parsing behavior. */
interface IntegralNetworkClient {
    fun fetchOverview(
        credentials: AccountCredentials,
        mobile: String,
        fetchedAt: Instant = Instant.now(),
    ): IntegralFetchResult

    fun fetchDetails(
        query: IntegralDetailQuery,
        credentials: AccountCredentials,
        mobile: String,
    ): IntegralDetailsFetchResult
}
