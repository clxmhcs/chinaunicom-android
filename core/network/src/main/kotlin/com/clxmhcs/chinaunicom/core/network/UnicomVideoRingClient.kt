package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.VideoRingBenefit
import com.clxmhcs.chinaunicom.core.model.VideoRingMember
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberFetchResult
import com.clxmhcs.chinaunicom.core.model.VideoRingMemberState
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.CookieJar
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

internal class OkHttpVideoRingTransport(timeoutMillis: Long = 20_000L) : VideoRingTransport {
    private val client = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
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

/** Source-equivalent native client for iOS VideoRingMemberService + VideoRingAPIClient. */
class UnicomVideoRingClient private constructor(
    private val transport: VideoRingTransport,
    private val activateSession: (AccountCredentials) -> AccountCredentials,
) : VideoRingNetworkClient {
    constructor() : this(OkHttpVideoRingTransport(), UnicomAPIClient()::activateSession)

    internal constructor(
        transport: VideoRingTransport,
        activateSession: (AccountCredentials) -> AccountCredentials,
        testOnly: Unit,
    ) : this(transport, activateSession)

    companion object {
        const val NATIVE_APP_ID = "edop_unicom_c43eac06"
        const val NATIVE_TICKET_ROOT = "https://m.client.10010.com/edop_ng/getTicketByNative"
        const val ROOT = "https://m.10155.com"
        const val CLIENT_APP_ID = "3000013947"
        const val LOGIN = "/woapp/login/ecsAppletLogin"
        const val CRBT_FLAG = "/woapp/videoRing/getCrbtFlag"
        const val MEMBER_INFO = "/woapp/h5/woMember/getClientMemberInfosByUserId"
        const val MEMBER_DETAIL = "/woapp/h5/woMember/getMemberDetail"
    }

    override fun fetchMemberState(
        credentials: AccountCredentials,
        expectedPhoneNumber: String,
    ): VideoRingMemberFetchResult {
        val expected = normalizePhone(expectedPhoneNumber)
        if (expected.isEmpty()) throw VideoRingAPIException.InvalidPhoneNumber

        var activeCredentials = credentials
        val ticket = try {
            getNativeTicket(activeCredentials)
        } catch (firstError: Exception) {
            if (activeCredentials.appID.isNullOrBlank() || activeCredentials.tokenOnline.isNullOrBlank()) throw firstError
            activeCredentials = activateSession(activeCredentials)
            getNativeTicket(activeCredentials)
        }

        val session = ecsAppletLogin(ticket, expected)
        val enabled = getCrbtFlag(session)
        val members = getMemberInfo(session)
        val memberType = members.firstOrNull(VideoRingMember::isMember)?.memberType ?: "15"
        val benefits = getMemberDetail(session, memberType)
        return VideoRingMemberFetchResult(
            state = VideoRingMemberState(
                phoneNumber = expected,
                members = members,
                benefits = benefits,
                isEnabled = enabled,
            ),
            updatedCredentials = activeCredentials.takeIf { it != credentials },
        )
    }

    private fun getNativeTicket(credentials: AccountCredentials): String {
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        val ecsToken = UnicomCookieCodec.value("ecs_token", cookie)?.trim().orEmpty()
        if (ecsToken.isEmpty()) throw VideoRingAPIException.MissingEcsToken
        val url = NATIVE_TICKET_ROOT.toHttpUrl().newBuilder()
            .addQueryParameter("token", ecsToken)
            .addQueryParameter("appId", NATIVE_APP_ID)
            .build()
            .toString()
        val response = execute(
            method = "GET",
            url = url,
            headers = mapOf(
                "Accept" to "*/*",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Cookie" to cookie,
            ),
        )
        val root = parseObject(response)
        val code = first(root, "rsp_code", "code", "status")
        val ticket = first(root, "ticket")
        if (!UnicomResponseStatus.isSuccess(code) || ticket.isNullOrEmpty()) {
            throw VideoRingAPIException.TicketFailed(first(root, "rsp_desc", "message", "desc") ?: "未返回可用 ticket")
        }
        return ticket
    }

    private fun ecsAppletLogin(ticket: String, expectedPhoneNumber: String): VideoRingSession {
        val root = postForm(
            LOGIN,
            mapOf("appid" to NATIVE_APP_ID, "ticket" to ticket),
            session = null,
        )
        val successFlag = root["success"].stringValue()?.lowercase()
        val code = first(root, "code", "rsp_code", "status")
        val result = root["result"] as? JsonObject
        val caller = result?.let { first(it, "caller") }
        val accessToken = first(root, "accessToken")
        if (successFlag == "false" || (code != null && !UnicomResponseStatus.isSuccess(code)) || caller.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
            throw VideoRingAPIException.LoginFailed(first(root, "message", "desc", "rsp_desc") ?: "10155 登录失败")
        }
        val normalizedCaller = normalizePhone(caller)
        if (normalizedCaller != expectedPhoneNumber) {
            throw VideoRingAPIException.AccountMismatch(expectedPhoneNumber, normalizedCaller)
        }
        return VideoRingSession(
            uid = result?.let { first(it, "userid", "userId", "uid") }.orEmpty(),
            accessToken = accessToken,
        )
    }

    private fun getCrbtFlag(session: VideoRingSession): Boolean {
        val root = postForm(CRBT_FLAG, emptyMap(), session)
        ensureBusinessSuccess(root, "视频彩铃开通状态查询失败")
        return root["result"].stringValue()?.let { it == "1" || it.equals("true", ignoreCase = true) } == true
    }

    private fun getMemberInfo(session: VideoRingSession): List<VideoRingMember> {
        val root = postJson(MEMBER_INFO, """{"includeAllConfigure":"1"}""", session)
        ensureBusinessSuccess(root, "会员信息查询失败")
        val result = root["result"] as? JsonArray ?: throw UnicomAPIException.InvalidResponse
        return result.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val type = first(item, "memberType") ?: return@mapNotNull null
            VideoRingMember(
                id = type,
                name = first(item, "memberName") ?: type,
                memberType = type,
                isMember = first(item, "isMember")?.let { it == "1" || it.equals("true", ignoreCase = true) } == true,
            )
        }
    }

    private fun getMemberDetail(session: VideoRingSession, memberType: String): List<VideoRingBenefit> {
        val body = """{"memberType":"${jsonEscape(memberType)}"}"""
        val root = postJson(MEMBER_DETAIL, body, session)
        ensureBusinessSuccess(root, "会员权益查询失败")
        val result = root["result"] as? JsonObject ?: return emptyList()
        val products = result["productlist"] as? JsonArray ?: return emptyList()
        return products.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = first(item, "spuId") ?: return@mapNotNull null
            val rightNum = first(item, "rightNum")?.toIntOrNull()
            val received = first(item, "received")?.toIntOrNull()?.let { it != 0 }
            VideoRingBenefit(
                id = id,
                name = first(item, "spuName") ?: id,
                imageURL = first(item, "spuImgurl"),
                price = rightNum?.let { "$it沃券" },
                received = received,
            )
        }
    }

    private fun postForm(path: String, values: Map<String, String>, session: VideoRingSession?): JsonObject =
        parseObject(
            execute(
                method = "POST",
                url = ROOT + path,
                body = unicomFormEncoded(values),
                headers = authenticationHeaders("application/x-www-form-urlencoded", session),
            ),
        )

    private fun postJson(path: String, json: String, session: VideoRingSession): JsonObject =
        parseObject(
            execute(
                method = "POST",
                url = ROOT + path,
                body = json.encodeToByteArray(),
                headers = authenticationHeaders("application/json", session),
            ),
        )

    private fun authenticationHeaders(contentType: String, session: VideoRingSession?): Map<String, String> = buildMap {
        put("Content-Type", contentType)
        put("Accept", "*/*")
        put("appid", CLIENT_APP_ID)
        session?.let {
            if (it.uid.isNotEmpty()) put("uid", it.uid)
            val token = normalizedBearerToken(it.accessToken)
            if (token.isNotEmpty()) {
                put("accessToken", token)
                put("Authorization", token)
            }
        }
    }

    private fun execute(
        method: String,
        url: String,
        body: ByteArray = byteArrayOf(),
        headers: Map<String, String>,
    ): ByteArray {
        val response = transport.execute(VideoRingTransportRequest(method, url, body, headers))
        if (response.statusCode !in 200..299) throw UnicomAPIException.HttpStatus(response.statusCode)
        return response.body
    }

    private fun parseObject(data: ByteArray): JsonObject = parseNetworkJson(data) as? JsonObject ?: throw UnicomAPIException.InvalidResponse

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

    private fun jsonEscape(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}

internal data class VideoRingSession(
    val uid: String,
    val accessToken: String,
)

sealed class VideoRingAPIException(message: String) : Exception(message) {
    data object InvalidPhoneNumber : VideoRingAPIException("当前号码格式无效")
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
