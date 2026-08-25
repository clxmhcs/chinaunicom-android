package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BillMonth
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import com.clxmhcs.chinaunicom.core.model.OrderedBusinessSnapshot
import com.clxmhcs.chinaunicom.core.model.PhoneBillSnapshot
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
    const val PHONE_BILL_ONLINE = "$PHONE_BILL_ROOT/mobileService/onLine.htm"
    const val PHONE_BILL_MONTHS = "/serviceimportantbusiness/phoneBillNew/queryMonths"
    const val PHONE_BILL_DETAIL = "/serviceimportantbusiness/phoneBillNew/queryDetail"

    const val INTEGRAL_ROOT = "https://activity.10010.com"
    const val INTEGRAL_BALANCE = "/welfare-mall-front/mobile/show/bj2205/v2/1"
    const val INTEGRAL_MONTHS = "/welfare-mall-front/new/integral/queryMonthlyList/v1"
    const val INTEGRAL_DETAILS = "/welfare-mall-front/new/integral/querySummaryList/v1"
    const val INTEGRAL_SOURCE = "ZXGS97000017640,003"
}

data class OrderedBusinessFetchResult(
    val snapshot: OrderedBusinessSnapshot,
    val updatedCredentials: AccountCredentials?,
)

fun interface OrderedBusinessNetworkClient {
    fun fetch(credentials: AccountCredentials): OrderedBusinessFetchResult
}

data class PhoneBillFetchResult(
    val snapshot: PhoneBillSnapshot,
    val updatedCredentials: AccountCredentials?,
)

data class PhoneBillMonthsFetchResult(
    val months: List<BillMonth>,
    val updatedCredentials: AccountCredentials?,
)

interface PhoneBillNetworkClient {
    fun fetchMonths(credentials: AccountCredentials): PhoneBillMonthsFetchResult
    fun fetchDetail(credentials: AccountCredentials, month: BillMonth): PhoneBillFetchResult
}

data class IntegralFetchResult(
    val snapshot: IntegralSnapshot,
    val updatedCredentials: AccountCredentials?,
)

data class IntegralDetailsFetchResult(
    val items: List<IntegralDetailItem>,
    val updatedCredentials: AccountCredentials?,
)

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

sealed class IntegralNetworkException(message: String) : Exception(message) {
    data object AccountMismatch : IntegralNetworkException("integralAccountMismatch")
    data object MissingTotalScore : IntegralNetworkException("integralMissingTotalScore")
}
