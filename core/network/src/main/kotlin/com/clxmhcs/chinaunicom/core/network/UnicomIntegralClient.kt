package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.IntegralDetailItem
import com.clxmhcs.chinaunicom.core.model.IntegralDetailQuery
import com.clxmhcs.chinaunicom.core.model.IntegralDetailsFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralError
import com.clxmhcs.chinaunicom.core.model.IntegralFetchResult
import com.clxmhcs.chinaunicom.core.model.IntegralMonthSummary
import com.clxmhcs.chinaunicom.core.model.IntegralSnapshot
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Source-equivalent implementation of iOS IntegralClient.swift. */
class UnicomIntegralClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(OkHttpUnicomTransport(20_000L)),
    private val sessionClient: UnicomAPIClient = UnicomAPIClient(),
    private val systemVersionProvider: () -> String = {
        UnicomSessionRenewalEnvironment.current().userAgentSystemVersion
    },
) : IntegralNetworkClient {
    private data class BalancePayload(
        val values: Map<Int, Int>,
        val provinceCode: String?,
        val packageID: String?,
        val isUnicom: String?,
        val cookieMutations: List<UnicomCookieMutation>,
    )

    private data class MonthsPayload(
        val months: List<IntegralMonthSummary>,
        val cookieMutations: List<UnicomCookieMutation>,
    )

    private data class DetailsPayload(
        val items: List<IntegralDetailItem>,
        val cookieMutations: List<UnicomCookieMutation>,
    )

    private data class OverviewPayload(
        val snapshot: IntegralSnapshot,
        val cookie: String,
    )

    private data class SessionOperationResult<T>(
        val value: T,
        val cookie: String,
    )

    override fun fetchOverview(
        credentials: AccountCredentials,
        mobile: String,
        fetchedAt: Instant,
    ): IntegralFetchResult {
        val result = withSessionRecovery(credentials, mobile) { cookie ->
            val payload = fetchOverviewOnce(cookie, fetchedAt)
            SessionOperationResult(payload.snapshot, payload.cookie)
        }
        return IntegralFetchResult(result.first, result.second)
    }

    override fun fetchDetails(
        query: IntegralDetailQuery,
        credentials: AccountCredentials,
        mobile: String,
    ): IntegralDetailsFetchResult {
        val result = withSessionRecovery(credentials, mobile) { cookie ->
            val payload = fetchDetailsOnce(query, cookie)
            val updatedCookie = UnicomCookieCodec.applying(payload.cookieMutations, cookie)
            SessionOperationResult(payload.items, updatedCookie)
        }
        return IntegralDetailsFetchResult(result.first, result.second)
    }

    private fun <T> withSessionRecovery(
        credentials: AccountCredentials,
        mobile: String,
        operation: (String) -> SessionOperationResult<T>,
    ): Pair<T, AccountCredentials?> {
        val originalCookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (originalCookie.isEmpty()) throw UnicomAPIException.MissingCookie
        validateAccount(mobile, originalCookie)

        return try {
            val result = operation(originalCookie)
            val updated = if (result.cookie == originalCookie) null else {
                AccountCredentials(result.cookie, credentials.appID, credentials.tokenOnline)
            }
            result.value to updated
        } catch (directError: Exception) {
            if (!shouldActivateSession(directError)) throw directError

            val activated = try {
                sessionClient.activateSession(credentials)
            } catch (error: Exception) {
                throw UnicomAPIException.Server(
                    "积分 Cookie 已失效，使用 appId/token_online 自动登录失败：${error.message ?: error::class.java.simpleName}",
                )
            }

            val renewedCookie = UnicomCookieCodec.normalize(activated.cookie)
            validateAccount(mobile, renewedCookie)
            val result = operation(renewedCookie)
            val updated = AccountCredentials(result.cookie, activated.appID, activated.tokenOnline)
            result.value to if (updated == credentials) null else updated
        }
    }

    private fun fetchOverviewOnce(cookie: String, fetchedAt: Instant): OverviewPayload {
        // iOS starts these two requests concurrently with the same starting Cookie. They are
        // intentionally both evaluated from the original Cookie here; only their returned
        // Set-Cookie mutations are folded afterward in the source-defined order.
        val balance = fetchBalance(cookie)
        val months = fetchMonths(cookie)
        val totalAvailable = balance.values[1]
            ?: throw IllegalStateException(IntegralError.MISSING_TOTAL_SCORE.errorDescription)

        val snapshot = IntegralSnapshot(
            totalAvailable = totalAvailable,
            communication = balance.values[2] ?: 0,
            reward = balance.values[3] ?: 0,
            directional = balance.values[9],
            expiredAndExpiringReward = balance.values[4] ?: 0,
            expiringThisMonth = balance.values[5] ?: 0,
            expiringCommunication = balance.values[8] ?: 0,
            expiringReward = balance.values[7] ?: 0,
            expirationDay = balance.values[10],
            couponCount = balance.values[6] ?: 0,
            provinceCode = balance.provinceCode,
            packageID = balance.packageID,
            isUnicom = balance.isUnicom,
            months = months.months,
            fetchedAt = fetchedAt,
            parserVersion = IntegralSnapshot.CURRENT_PARSER_VERSION,
        )

        var updatedCookie = UnicomCookieCodec.applying(balance.cookieMutations, cookie)
        updatedCookie = UnicomCookieCodec.applying(months.cookieMutations, updatedCookie)
        return OverviewPayload(snapshot, updatedCookie)
    }

    private fun fetchBalance(cookie: String): BalancePayload {
        val response = post(
            path = ComprehensiveBusinessEndpoints.INTEGRAL_BALANCE,
            values = mapOf("position" to "123", "isTermShow" to "1"),
            cookie = cookie,
        )
        val root = decodeRoot(response.data)
        validateSuccess(root, "积分余额查询失败")
        val resdata = root["resdata"] as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        if (resdata["data"] !is JsonArray) throw UnicomAPIException.InvalidResponse

        val values = mutableMapOf<Int, Int>()
        resdata["data"].objectList().forEach { item ->
            val type = integer(item["type"]) ?: return@forEach
            val number = integer(item["number"]) ?: return@forEach
            values[type] = number
        }
        return BalancePayload(
            values = values,
            provinceCode = string(resdata["provinceCode"]),
            packageID = string(resdata["packageId"]),
            isUnicom = string(resdata["isUnicom"]),
            cookieMutations = response.cookieMutations,
        )
    }

    private fun fetchMonths(cookie: String): MonthsPayload {
        val response = post(
            path = ComprehensiveBusinessEndpoints.INTEGRAL_MONTHS,
            values = mapOf("from" to ComprehensiveBusinessEndpoints.INTEGRAL_SOURCE),
            cookie = cookie,
        )
        val root = decodeRoot(response.data)
        validateSuccess(root, "最近六个月积分查询失败")
        if (root["resdata"] !is JsonArray) throw UnicomAPIException.InvalidResponse
        val months = root["resdata"].objectList().mapNotNull { item ->
            val cycleID = string(item["cycleId"])?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            IntegralMonthSummary(
                cycleID = cycleID,
                addScore = integer(item["addScore"]) ?: 0,
                consumedScore = integer(item["xfScore"]) ?: 0,
                expiredScore = integer(item["expireScore"]) ?: 0,
            )
        }
        return MonthsPayload(months, response.cookieMutations)
    }

    private fun fetchDetailsOnce(query: IntegralDetailQuery, cookie: String): DetailsPayload {
        val values = linkedMapOf(
            "scoreType" to query.scoreType,
            "typeChar" to query.typeChar,
            "from" to ComprehensiveBusinessEndpoints.INTEGRAL_SOURCE,
        )
        query.yearMonth?.let { values["yearMonth"] = it }

        val response = post(
            path = ComprehensiveBusinessEndpoints.INTEGRAL_DETAILS,
            values = values,
            cookie = cookie,
        )
        val root = decodeRoot(response.data)
        validateSuccess(root, "积分明细查询失败")
        if (root["resdata"] !is JsonArray) throw UnicomAPIException.InvalidResponse
        val items = root["resdata"].objectList().map { item ->
            IntegralDetailItem(
                typeChar = string(item["typeChar"]) ?: query.typeChar,
                scoreType = string(item["scoreType"]) ?: "积分",
                title = string(item["title"]) ?: "积分明细",
                scoreValue = string(item["scoreValue"]) ?: "0",
                createTime = string(item["createTime"]),
                returnTime = string(item["returnTime"]),
                endTime = string(item["endTime"]),
                orderTime = string(item["orderTime"]),
                channelName = string(item["channelName"]),
                expireTime = string(item["expireTime"]),
                expireTag = string(item["expireTag"]),
            )
        }
        return DetailsPayload(items, response.cookieMutations)
    }

    private fun post(
        path: String,
        values: Map<String, String>,
        cookie: String,
    ): UnicomHTTPResponse {
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        val response = http.post(
            url = ComprehensiveBusinessEndpoints.INTEGRAL_ROOT + path,
            body = unicomFormEncoded(values),
            headers = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
                "Origin" to "https://img.client.10010.com",
                "Accept-Language" to "zh-CN,zh-Hans;q=0.9",
                "User-Agent" to UnicomClientProfile.h5UserAgent(systemVersion),
            ),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) {
            throw UnicomAPIException.SessionExpired
        }
        return response
    }

    private fun validateAccount(mobile: String, cookie: String) {
        val expected = mobile.filter(Char::isDigit)
        if (expected.isEmpty()) return
        val cookieMobile = UnicomCookieCodec.value("c_mobile", cookie)
            ?: UnicomCookieCodec.value("u_account", cookie)
            ?: return
        val actual = cookieMobile.filter(Char::isDigit)
        if (actual.isNotEmpty() && actual != expected) {
            throw IllegalStateException(IntegralError.ACCOUNT_MISMATCH.errorDescription)
        }
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> error.serverMessage.contains("cookie", ignoreCase = true) ||
            error.serverMessage.contains("登录") || error.serverMessage.contains("在线")
        else -> false
    }

    private fun decodeRoot(data: ByteArray): JsonObject {
        return try {
            parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse
        } catch (error: UnicomAPIException) {
            throw error
        } catch (_: Exception) {
            if (UnicomResponseStatus.responseLooksExpired(data)) throw UnicomAPIException.SessionExpired
            throw UnicomAPIException.InvalidResponse
        }
    }

    private fun validateSuccess(root: JsonObject, fallback: String) {
        val code = listOf("code", "rsp_code", "status")
            .asSequence()
            .mapNotNull { root[it].stringValue()?.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
        if (!UnicomResponseStatus.isSuccess(code)) {
            throw UnicomAPIException.Server(string(root["msg"]) ?: fallback)
        }
    }

    private fun string(value: kotlinx.serialization.json.JsonElement?): String? =
        value.stringValue()?.trim()

    private fun integer(value: kotlinx.serialization.json.JsonElement?): Int? {
        val raw = string(value)?.takeIf(String::isNotEmpty) ?: return null
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    }
}
