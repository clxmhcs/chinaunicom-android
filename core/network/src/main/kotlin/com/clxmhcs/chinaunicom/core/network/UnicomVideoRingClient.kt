package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.VideoRingMember
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface VideoRingNetworkClient {
    fun fetchMemberState(credentials: AccountCredentials, expectedPhoneNumber: String): VideoRingMemberFetchResult
}

internal data class VideoRingTransportRequest(
    val method: String,
    val url: String,
    val body: ByteArray = byteArrayOf(),
    val headers: Map<String, String> = emptyMap(),
)

internal fun interface VideoRingTransport {
    fun execute(request: VideoRingTransportRequest): UnicomRawResponse
}

/** One ephemeral 10155 cookie session per business refresh. Native-ticket traffic never enters this jar. */
private class EphemeralVideoRingCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != UnicomVideoRingClient.ROOT_HOST) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            this.cookies.removeAll { existing ->
                existing.expiresAt <= now || cookies.any { incoming ->
                    existing.name == incoming.name && existing.domain == incoming.domain && existing.path == incoming.path
                }
            }
            this.cookies += cookies.filter { it.expiresAt > now }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host != UnicomVideoRingClient.ROOT_HOST) return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(lock) {
            cookies.removeAll { it.expiresAt <= now }
            cookies.filter { it.matches(url) }
        }
    }
}

internal class OkHttpVideoRingTransport(timeoutMillis: Long = 20_000L) : VideoRingTransport {
    private val client = OkHttpClient.Builder()
        .cookieJar(EphemeralVideoRingCookieJar())
        .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override fun execute(request: VideoRingTransportRequest): UnicomRawResponse {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        when (request.method) {
            "GET" -> builder.get()
            "POST" -> {
                val mediaType = request.headers["Content-Type"]?.toMediaType()
                builder.post(request.body.toRequestBody(mediaType))
            }
            else -> error("Unsupported method: ${request.method}")
        }
        client.newCall(builder.build()).execute().use { response ->
            val headers = response.headers.names().associateWith(response.headers::values)
            return UnicomRawResponse(
                statusCode = response.code,
                body = response.body?.bytes() ?: byteArrayOf(),
                headers = headers,
            )
        }
    }
}

/** Source-equivalent native client for the active iOS VideoRingInlineMemberService. */
class UnicomVideoRingClient private constructor(
    private val transportFactory: () -> VideoRingTransport,
    private val activateSession: (AccountCredentials) -> AccountCredentials,
    private val clientUID: String,
    private val systemVersionProvider: () -> String,
    private val clockMillis: () -> Long,
    private val nonceProvider: () -> String,
) : VideoRingNetworkClient {
    constructor(clientUID: String) : this(
        transportFactory = { OkHttpVideoRingTransport() },
        activateSession = UnicomAPIClient()::activateSession,
        clientUID = normalizeClientUID(clientUID),
        systemVersionProvider = { UnicomSessionRenewalEnvironment.current().userAgentSystemVersion },
        clockMillis = System::currentTimeMillis,
        nonceProvider = ::newNonce,
    )

    constructor() : this(defaultClientUID())

    internal constructor(
        transport: VideoRingTransport,
        activateSession: (AccountCredentials) -> AccountCredentials,
        clientUID: String,
        systemVersionProvider: () -> String,
        clockMillis: () -> Long,
        nonceProvider: () -> String,
        testOnly: Unit,
    ) : this(
        transportFactory = { transport },
        activateSession = activateSession,
        clientUID = normalizeClientUID(clientUID),
        systemVersionProvider = systemVersionProvider,
        clockMillis = clockMillis,
        nonceProvider = nonceProvider,
    )

    companion object {
        const val NATIVE_APP_ID = "edop_unicom_c43eac06"
        const val NATIVE_TICKET_ROOT = "https://m.client.10010.com/edop_ng/getTicketByNative"
        const val ROOT = "https://m.10155.com"
        const val ROOT_HOST = "m.10155.com"
        const val CLIENT_APP_ID = "3000013947"
        const val SIGN_SALT = "VNEU8G4V"
        const val OS_WO_VERSION = "1018"
        const val LOGIN = "/woapp/login/ecsAppletLogin"
        const val MEMBER_INFO = "/woapp/h5/woMember/getClientMemberInfosByUserId"
        const val MEMBER_STATE = "/woapp/uc/getmemberinfo"

        private val DEFAULT_TABS = listOf(
            VideoRingMember("87", "AI彩铃视听剧场会员", "87", false),
            VideoRingMember("15", "铂金会员", "15", false),
            VideoRingMember("76", "AI彩铃升级版", "76", false),
        )

        private fun normalizeClientUID(value: String): String {
            val normalized = value.trim().lowercase(Locale.ROOT)
            if (normalized.length != 36 || normalized.any { !it.isLetterOrDigit() }) {
                throw VideoRingAPIException.InvalidClientUID
            }
            return normalized
        }

        private fun defaultClientUID(): String {
            val first = java.util.UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
            val second = java.util.UUID.randomUUID().toString().replace("-", "").lowercase(Locale.ROOT)
            return first + second.take(4)
        }

        private fun newNonce(): String {
            val digits = Random.nextLong(0L, 10_000_000_000_000_000L)
            return String.format(Locale.US, "0.%016d", digits)
        }
    }

    override fun fetchMemberState(
        credentials: AccountCredentials,
        expectedPhoneNumber: String,
    ): VideoRingMemberFetchResult {
        val expected = normalizePhone(expectedPhoneNumber)
        if (expected.length != 11 || !expected.startsWith("1")) throw VideoRingAPIException.InvalidPhoneNumber

        val transport = transportFactory()
        var activeCredentials = credentials
        val ticket = try {
            getNativeTicket(transport, activeCredentials)
        } catch (firstError: Exception) {
            if (activeCredentials.appID.isNullOrBlank() || activeCredentials.tokenOnline.isNullOrBlank()) throw firstError
            activeCredentials = activateSession(activeCredentials)
            getNativeTicket(transport, activeCredentials)
        }

        val login = ecsAppletLogin(transport, ticket)
        val normalizedCaller = normalizePhone(login.caller)
        if (normalizedCaller != expected) {
            throw VideoRingAPIException.AccountMismatch(expected, normalizedCaller)
        }

        val configuredTabs = getMemberInfo(transport, login.accessToken)
        val memberStates = getMemberState(transport, login.accessToken)
        val configuredByType = configuredTabs.associateBy(VideoRingMember::memberType)
        val stateByType = memberStates.associateBy(VideoRingMember::memberType)
        val merged = DEFAULT_TABS.map { fallback ->
            val configured = configuredByType[fallback.memberType] ?: fallback
            val state = stateByType[fallback.memberType]
            VideoRingMember(
                id = configured.memberType,
                name = configured.name,
                memberType = configured.memberType,
                isMember = state?.isMember == true,
                startTime = state?.startTime,
                endTime = state?.endTime,
            )
        }

        return VideoRingMemberFetchResult(
            state = VideoRingMemberState(
                phoneNumber = normalizedCaller,
                members = merged,
            ),
            updatedCredentials = activeCredentials.takeIf { it != credentials },
        )
    }

    private fun getNativeTicket(transport: VideoRingTransport, credentials: AccountCredentials): String {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        val ecsToken = UnicomCookieCodec.value("ecs_token", cookie)?.trim().orEmpty()
        if (ecsToken.isEmpty()) throw VideoRingAPIException.MissingEcsToken
        val url = NATIVE_TICKET_ROOT.toHttpUrl().newBuilder()
            .addQueryParameter("token", ecsToken)
            .addQueryParameter("appId", NATIVE_APP_ID)
            .build()
            .toString()
        val root = parseObject(
            execute(
                transport = transport,
                method = "GET",
                url = url,
                headers = mapOf(
                    "Accept" to "*/*",
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Cookie" to cookie,
                    "Accept-Language" to "zh-Hans-CN;q=1.0",
                    "User-Agent" to nativeUserAgent(),
                ),
            ),
        )
        val code = first(root, "rsp_code", "code", "status")
        val ticket = first(root, "ticket")
        if (!UnicomResponseStatus.isSuccess(code) || ticket.isNullOrEmpty()) {
            throw VideoRingAPIException.TicketFailed(first(root, "rsp_desc", "message", "desc") ?: "未返回可用 ticket")
        }
        return ticket
    }

    private fun ecsAppletLogin(transport: VideoRingTransport, ticket: String): LoginResult {
        val root = postForm(
            transport = transport,
            path = LOGIN,
            values = mapOf("appid" to NATIVE_APP_ID, "ticket" to ticket),
            accessToken = null,
        )
        val code = first(root, "code", "rsp_code", "status")
        if (code != null && !UnicomResponseStatus.isSuccess(code)) {
            throw VideoRingAPIException.LoginFailed(first(root, "message", "desc", "rsp_desc") ?: "10155 登录失败")
        }
        val result = root["result"] as? JsonObject
        val caller = result?.let { first(it, "caller") }
        val accessToken = first(root, "accessToken")
        if (caller.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
            throw VideoRingAPIException.LoginFailed(first(root, "message", "desc", "rsp_desc") ?: "10155 登录未返回 caller/accessToken")
        }
        return LoginResult(caller = caller, accessToken = accessToken)
    }

    private fun getMemberInfo(transport: VideoRingTransport, accessToken: String): List<VideoRingMember> {
        val root = postJson(
            transport = transport,
            path = MEMBER_INFO,
            json = """{"includeAllConfigure":"1"}""",
            accessToken = accessToken,
        )
        ensureBusinessSuccess(root, "会员信息查询失败")
        val result = root["result"] as? JsonArray ?: throw UnicomAPIException.InvalidResponse
        return result.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val type = first(item, "memberType") ?: return@mapNotNull null
            val name = first(item, "memberName") ?: return@mapNotNull null
            VideoRingMember(
                id = type,
                name = name,
                memberType = type,
                isMember = false,
            )
        }
    }

    private fun getMemberState(transport: VideoRingTransport, accessToken: String): List<VideoRingMember> {
        val root = postJson(
            transport = transport,
            path = MEMBER_STATE,
            json = "{}",
            accessToken = accessToken,
        )
        ensureBusinessSuccess(root, "会员开通状态查询失败")
        val result = root["result"] as? JsonArray ?: throw UnicomAPIException.InvalidResponse
        return result.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val type = first(item, "memberType") ?: return@mapNotNull null
            val name = first(item, "memberName") ?: return@mapNotNull null
            VideoRingMember(
                id = type,
                name = name,
                memberType = type,
                isMember = first(item, "status") == "1",
                startTime = first(item, "startTime"),
                endTime = first(item, "endTime"),
            )
        }
    }

    private fun postForm(
        transport: VideoRingTransport,
        path: String,
        values: Map<String, String>,
        accessToken: String?,
    ): JsonObject = parseObject(
        execute(
            transport = transport,
            method = "POST",
            url = ROOT + path,
            body = unicomFormEncoded(values),
            headers = authenticationHeaders("application/x-www-form-urlencoded", accessToken),
        ),
    )

    private fun postJson(
        transport: VideoRingTransport,
        path: String,
        json: String,
        accessToken: String,
    ): JsonObject = parseObject(
        execute(
            transport = transport,
            method = "POST",
            url = ROOT + path,
            body = json.encodeToByteArray(),
            headers = authenticationHeaders("application/json", accessToken),
        ),
    )

    private fun authenticationHeaders(contentType: String, accessToken: String?): Map<String, String> {
        val timestamp = clockMillis().toString()
        val nonce = nonceProvider()
        val signature = md5Upper(timestamp + SIGN_SALT + nonce)
        return buildMap {
            put("Content-Type", contentType)
            put("Accept", "*/*")
            put("appid", CLIENT_APP_ID)
            put("uid", clientUID)
            put("timestamp", timestamp)
            put("nonce", nonce)
            put("sign", signature)
            put("oswoversion", OS_WO_VERSION)
            put("Accept-Language", "zh-Hans-CN;q=1.0")
            put("User-Agent", nativeUserAgent())
            accessToken?.let { put("accessToken", normalizedBearerToken(it)) }
        }
    }

    private fun nativeUserAgent(): String {
        val systemVersion = systemVersionProvider().trim().ifEmpty { "11" }
        return UnicomClientProfile.nativeUserAgent(systemVersion)
    }

    private fun execute(
        transport: VideoRingTransport,
        method: String,
        url: String,
        body: ByteArray = byteArrayOf(),
        headers: Map<String, String>,
    ): ByteArray {
        val response = transport.execute(VideoRingTransportRequest(method, url, body, headers))
        if (response.statusCode !in 200..299) throw UnicomAPIException.HttpStatus(response.statusCode)
        return response.body
    }

    private fun parseObject(data: ByteArray): JsonObject =
        parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse

    private fun ensureBusinessSuccess(root: JsonObject, fallback: String) {
        val code = first(root, "code", "rsp_code", "status")
        if (code != null && !UnicomResponseStatus.isSuccess(code)) {
            if (UnicomResponseStatus.isExpired(code)) throw UnicomAPIException.SessionExpired
            throw VideoRingAPIException.Server(first(root, "message", "desc", "rsp_desc") ?: fallback)
        }
    }

    private fun first(value: JsonObject, vararg keys: String): String? = keys.asSequence()
        .mapNotNull { value[it].stringValue()?.trim() }
        .firstOrNull { it.isNotEmpty() }

    private fun normalizedBearerToken(token: String): String {
        val value = token.trim()
        if (value.isEmpty()) return ""
        return if (value.startsWith("bearer ", ignoreCase = true)) value else "Bearer $value"
    }

    private fun normalizePhone(value: String): String = value.filter(Char::isDigit)

    private fun md5Upper(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> String.format(Locale.US, "%02X", byte.toInt() and 0xFF) }

    private data class LoginResult(val caller: String, val accessToken: String)
}

sealed class VideoRingAPIException(message: String) : Exception(message) {
    data object InvalidPhoneNumber : VideoRingAPIException("当前号码格式无效")
    data object InvalidClientUID : VideoRingAPIException("视频彩铃客户端标识格式无效")
    data object MissingEcsToken : VideoRingAPIException("当前号码的登录凭据中缺少 ecs_token，请先刷新该号码的登录状态")
    data class TicketFailed(val detail: String) : VideoRingAPIException("获取当前号码的视频彩铃登录票据失败：$detail")
    data class LoginFailed(val detail: String) : VideoRingAPIException("登录视频彩铃会员中心失败：$detail")
    data class AccountMismatch(val expected: String, val actual: String) : VideoRingAPIException(
        "视频彩铃账号校验失败：选择的是 ${mask(expected)}，服务器返回的是 ${mask(actual)}",
    )
    data class Server(val detail: String) : VideoRingAPIException(detail)

    companion object {
        private fun mask(value: String): String = if (value.length >= 7) {
            value.take(3) + "****" + value.takeLast(4)
        } else value
    }
}