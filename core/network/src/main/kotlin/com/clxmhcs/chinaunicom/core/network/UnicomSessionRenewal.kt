package com.clxmhcs.chinaunicom.core.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Shared current China Unicom native client identity from the iOS source of truth. */
object UnicomClientProfile {
    const val APP_VERSION = "12.15"
    const val PROTOCOL_VERSION = "iphone_c@12.1500"
    const val BUNDLE_IDENTIFIER = "com.chinaunicom.mobilebusiness"
    const val NATIVE_BUILD = "4"
    const val ALAMOFIRE_VERSION = "4.7.3"

    fun nativeUserAgent(systemVersion: String): String =
        "ChinaUnicom4.x/$APP_VERSION ($BUNDLE_IDENTIFIER; build:$NATIVE_BUILD; iOS $systemVersion) " +
            "Alamofire/$ALAMOFIRE_VERSION unicom{version:$PROTOCOL_VERSION}"
}

data class UnicomSessionRenewalDeviceContext(
    val deviceCode: String,
    val deviceID: String,
    val uniqueIdentifier: String,
    val deviceModel: String,
    val deviceOS: String,
    val userAgentSystemVersion: String,
    val localIPv4Address: String,
)

fun interface UnicomSessionRenewalDeviceContextProvider {
    fun current(): UnicomSessionRenewalDeviceContext
}

/**
 * Process composition seam for clients that are constructed below the Android UI/data layer.
 * Production installs the Android Keystore-backed M5 identity from Application.onCreate().
 */
object UnicomSessionRenewalEnvironment : UnicomSessionRenewalDeviceContextProvider {
    @Volatile
    private var provider: UnicomSessionRenewalDeviceContextProvider? = null

    fun install(provider: UnicomSessionRenewalDeviceContextProvider) {
        this.provider = provider
    }

    override fun current(): UnicomSessionRenewalDeviceContext =
        provider?.current() ?: throw UnicomAPIException.MissingCredentials
}

data class UnicomSessionRenewalRequest(
    val url: String,
    val body: ByteArray,
    val headers: Map<String, String>,
)

object UnicomModernSessionRenewalProfile {
    const val ONLINE_URL = "https://loginhl.10010.com/mobileService/onLine.htm"
    const val SIM_OPERATOR = "--,--,65535,65535,--@--,--,65535,65535,--"
    const val STEP = "welcom"
    const val IS_FIRST_INSTALL = "0"
    const val FLUSH_KEY = "1"
    const val VOIP_TOKEN = "citc-default-token-do-not-push"
    const val DEVICE_BRAND = "iPhone"
}

object UnicomSessionRenewalRequestFactory {
    fun modern(
        originalCookie: String,
        appID: String,
        tokenOnline: String,
        device: UnicomSessionRenewalDeviceContext,
        requestTime: String,
    ): UnicomSessionRenewalRequest {
        val cookie = UnicomCookieCodec.normalize(originalCookie)
        val headers = buildMap {
            put("Content-Type", "application/x-www-form-urlencoded")
            put("User-Agent", UnicomClientProfile.nativeUserAgent(device.userAgentSystemVersion))
            put("Accept", "*/*")
            put("Accept-Language", "zh-Hans-CN;q=1.0")
            put("Accept-Encoding", "gzip;q=1.0, compress;q=0.5")
            if (cookie.isNotEmpty()) put("Cookie", cookie)
        }
        return UnicomSessionRenewalRequest(
            url = UnicomModernSessionRenewalProfile.ONLINE_URL,
            body = unicomFormEncoded(
                mapOf(
                    "reqtime" to requestTime,
                    "version" to UnicomClientProfile.PROTOCOL_VERSION,
                    "simOperator" to UnicomModernSessionRenewalProfile.SIM_OPERATOR,
                    "token_online" to tokenOnline,
                    "appId" to appID,
                    "deviceId" to device.deviceID,
                    "pip" to device.localIPv4Address,
                    "deviceModel" to device.deviceModel,
                    "deviceOS" to device.deviceOS,
                    "deviceBrand" to UnicomModernSessionRenewalProfile.DEVICE_BRAND,
                    "uniqueIdentifier" to device.uniqueIdentifier,
                    "step" to UnicomModernSessionRenewalProfile.STEP,
                    "isFirstInstall" to UnicomModernSessionRenewalProfile.IS_FIRST_INSTALL,
                    "flushkey" to UnicomModernSessionRenewalProfile.FLUSH_KEY,
                    "deviceCode" to device.deviceCode,
                    "voipToken" to UnicomModernSessionRenewalProfile.VOIP_TOKEN,
                ),
            ),
            headers = headers,
        )
    }
}

fun currentUnicomLocalIPv4Address(): String? = runCatching {
    Collections.list(NetworkInterface.getNetworkInterfaces())
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}.getOrNull()
