package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.BalanceFetchResult
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.parser.QuotaParser
import com.clxmhcs.chinaunicom.core.parser.QuotaParserException
import com.clxmhcs.chinaunicom.core.parser.RemainingQueryParser
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UnicomAPIClient(
    private val http: UnicomHTTPClient = UnicomHTTPClient(),
    private val parser: QuotaParser = QuotaParser(),
    private val remainingQueryParser: RemainingQueryParser = RemainingQueryParser(),
    private val renewalDeviceContextProvider: UnicomSessionRenewalDeviceContextProvider = UnicomSessionRenewalEnvironment,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private data class ActivatedSession(
        val cookie: String,
        val appID: String,
        val tokenOnline: String,
    )

    private val balanceClient = UnicomBalanceClient(http)
    private val remainingNormalizer = RemainingUnlimitedFlowResponseNormalizer()

    companion object {
        const val BASE_URL = "https://m.client.10010.com"
        const val ONLINE_URL = UnicomModernSessionRenewalProfile.ONLINE_URL
        const val QUOTA_PATH = "/servicequerybusiness/operationservice/queryOcsPackageFlowLeftContentRevisedInJune"
        const val INFORMATION_PATH = "/servicequerybusiness/query/myInformation"
        const val ONLINE_VERSION = UnicomClientProfile.PROTOCOL_VERSION

        private val requestTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    fun activateSession(credentials: AccountCredentials): AccountCredentials {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        val appID = credentials.appID.trimmedOrNull() ?: throw UnicomAPIException.MissingCredentials
        val tokenOnline = credentials.tokenOnline.trimmedOrNull() ?: throw UnicomAPIException.MissingCredentials
        val activated = activateModernSession(cookie, appID, tokenOnline)
        return AccountCredentials(activated.cookie, activated.appID, activated.tokenOnline)
    }

    fun fetchQuota(credentials: AccountCredentials): QuotaFetchResult =
        fetchQuota(credentials, allowsInformationFallback = true, includesRemainingQuerySnapshot = true)

    fun fetchWidgetQuota(credentials: AccountCredentials): QuotaFetchResult =
        fetchQuota(credentials, allowsInformationFallback = false, includesRemainingQuerySnapshot = false)

    fun fetchBalance(credentials: AccountCredentials): BalanceFetchResult {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            balanceClient.fetchBalance(cookie).let { result ->
                BalanceFetchResult(result.balanceYuan, result.detail, null)
            }
        } catch (directError: Exception) {
            if (!shouldActivateSession(directError)) throw directError
            val appID = credentials.appID.trimmedOrNull() ?: throw directError
            val tokenOnline = credentials.tokenOnline.trimmedOrNull() ?: throw directError
            val activated = activateModernSession(cookie, appID, tokenOnline)
            val changed = activated.cookie != cookie || activated.appID != appID || activated.tokenOnline != tokenOnline
            val result = balanceClient.fetchBalance(activated.cookie)
            BalanceFetchResult(
                balanceYuan = result.balanceYuan,
                unavailableBalanceDetail = result.detail,
                updatedCredentials = if (changed) {
                    AccountCredentials(activated.cookie, activated.appID, activated.tokenOnline)
                } else null,
            )
        }
    }

    private fun fetchQuota(
        credentials: AccountCredentials,
        allowsInformationFallback: Boolean,
        includesRemainingQuerySnapshot: Boolean,
    ): QuotaFetchResult {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return try {
            fetchQuotaOnce(cookie, allowsInformationFallback, includesRemainingQuerySnapshot)
        } catch (directError: Exception) {
            if (!shouldActivateSession(directError)) throw directError
            val appID = credentials.appID.trimmedOrNull() ?: throw directError
            val tokenOnline = credentials.tokenOnline.trimmedOrNull() ?: throw directError
            val activated = try {
                activateModernSession(cookie, appID, tokenOnline)
            } catch (error: Exception) {
                throw UnicomAPIException.Server(
                    "Cookie 查询返回会话失效，使用 appId/token_online 自动登录失败：${error.message ?: error::class.java.simpleName}",
                )
            }
            val result = fetchQuotaOnce(activated.cookie, allowsInformationFallback, includesRemainingQuerySnapshot)
            val changed = activated.cookie != cookie || activated.appID != appID || activated.tokenOnline != tokenOnline
            if (changed) {
                result.copy(
                    updatedCredentials = AccountCredentials(
                        activated.cookie,
                        activated.appID,
                        activated.tokenOnline,
                    ),
                )
            } else result
        }
    }

    private fun fetchQuotaOnce(
        cookie: String,
        allowsInformationFallback: Boolean,
        includesRemainingQuerySnapshot: Boolean,
    ): QuotaFetchResult {
        val response = http.post(
            url = BASE_URL + QUOTA_PATH,
            headers = mapOf("Cookie" to cookie),
        )
        if (UnicomResponseStatus.responseLooksExpired(response.data)) throw UnicomAPIException.SessionExpired

        val parsed = try {
            parser.parse(response.data)
        } catch (_: QuotaParserException.SessionExpired) {
            throw UnicomAPIException.SessionExpired
        } catch (error: QuotaParserException.Server) {
            throw UnicomAPIException.Server(error.serverMessage)
        } catch (_: QuotaParserException.NoPackages) {
            throw UnicomAPIException.NoPackages
        } catch (_: Exception) {
            throw UnicomAPIException.InvalidResponse
        }

        val remaining = if (includesRemainingQuerySnapshot) {
            runCatching { remainingQueryParser.parse(response.data) }.getOrNull()?.let {
                remainingNormalizer.normalize(it, response.data)
            }
        } else null

        var packageName = parsed.packageName
        if (allowsInformationFallback && packageName.isEmpty()) {
            packageName = runCatching { fetchInformation(cookie) }.getOrNull().orEmpty()
        }
        return QuotaFetchResult(
            packageName = packageName,
            packages = parsed.packages,
            voicePackages = parsed.voicePackages,
            remainingQuerySnapshot = remaining,
            balanceYuan = null,
            unavailableBalanceDetail = null,
            quotaResourceStatus = parsed.quotaResourceStatus,
            updatedCredentials = null,
        )
    }

    private fun activateModernSession(
        originalCookie: String,
        appID: String,
        tokenOnline: String,
    ): ActivatedSession {
        val request = UnicomSessionRenewalRequestFactory.modern(
            originalCookie = originalCookie,
            appID = appID,
            tokenOnline = tokenOnline,
            device = renewalDeviceContextProvider.current(),
            requestTime = requestTimeFormatter.format(LocalDateTime.ofInstant(clock.instant(), clock.zone)),
        )
        val response = http.post(
            url = request.url,
            body = request.body,
            headers = request.headers,
        )
        val objectValue = parseNetworkJson(response.data)
        val code = UnicomResponseStatus.topLevelCode(response.data).orEmpty()
        if (!UnicomResponseStatus.isSuccess(code)) {
            val message = recursiveString(objectValue, setOf("dsc", "rsp_desc", "desc", "message"))
                ?: "联通在线状态维护失败（code: ${code.ifEmpty { "未知" }}）"
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            throw UnicomAPIException.Server(message)
        }
        val activatedCookie = if (response.cookieMutations.isEmpty()) originalCookie
        else UnicomCookieCodec.applying(response.cookieMutations, originalCookie)
        val activatedAppID = recursiveString(objectValue, setOf("appId", "appid", "appID")).trimmedOrNull() ?: appID
        val activatedToken = recursiveString(objectValue, setOf("token_online", "tokenOnline")).trimmedOrNull() ?: tokenOnline
        return ActivatedSession(activatedCookie, activatedAppID, activatedToken)
    }

    private fun fetchInformation(cookie: String): String {
        val response = http.post(
            url = BASE_URL + INFORMATION_PATH,
            headers = mapOf("Cookie" to cookie),
        )
        return recursiveString(parseNetworkJson(response.data), setOf("productname", "packageName")).orEmpty()
    }

    private fun shouldActivateSession(error: Exception): Boolean = when (error) {
        is UnicomAPIException.SessionExpired -> true
        is UnicomAPIException.Server -> {
            val message = error.serverMessage
            message.contains("cookie", ignoreCase = true) || message.contains("登录") || message.contains("在线")
        }
        else -> false
    }
}
