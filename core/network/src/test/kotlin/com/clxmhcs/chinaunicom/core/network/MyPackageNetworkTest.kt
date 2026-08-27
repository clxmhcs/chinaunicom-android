package com.clxmhcs.chinaunicom.core.network

import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MyPackageNetworkTest {
    @Test
    fun memberCryptoMatchesSourceAesCbcZeroPaddingContract() {
        val source = "{\"myNumbers\":[{\"packageName\":\"家庭套餐\"}]}".toByteArray()
        val key = "#user3ExtraInfo6".toByteArray(StandardCharsets.UTF_8)
        val paddedSize = ((source.size + 15) / 16) * 16
        val padded = ByteArray(paddedSize)
        source.copyInto(padded)
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
        val encoded = URLEncoder.encode(Base64.getEncoder().encodeToString(cipher.doFinal(padded)), StandardCharsets.UTF_8.name())

        assertArrayEquals(source, MyPackageCrypto.decryptMemberPayload(encoded))
    }

    @Test
    fun optionalEnhancementFailureDoesNotDiscardSuccessfulPrimaryPackage() {
        val seen = mutableListOf<UnicomRequest>()
        val transport = UnicomTransport { request ->
            seen += request
            if (request.url.endsWith("/servicequerybusiness/queryPackage/myPackage")) {
                UnicomRawResponse(
                    200,
                    """{"code":"0000","data":{"productName":"校园套餐","productId":"P1","productType":"T1","packageResourceType":"1"}}""".toByteArray(),
                    mapOf("Set-Cookie" to listOf("SESSION=renewed; Path=/")),
                )
            } else {
                UnicomRawResponse(200, """{"code":"30001","desc":"optional unavailable"}""".toByteArray())
            }
        }
        val client = UnicomMyPackageClient(
            http = UnicomHTTPClient(transport, retryDelayMillis = 0),
            systemVersionProvider = { "18.7" },
        )

        val result = client.fetch(AccountCredentials("SESSION=old", "app", "token"))

        assertEquals("校园套餐", result.snapshot.productName)
        assertEquals(0, result.snapshot.mobileRules.size)
        assertEquals(0, result.snapshot.memberGroups.size)
        assertNotNull(result.updatedCredentials)
        assertEquals("renewed", UnicomCookieCodec.value("SESSION", result.updatedCredentials!!.cookie))
        assertEquals(5, seen.size)
        assertEquals(true, seen.first().headers["User-Agent"]?.contains("iphone_c@12.1400"))
    }
}
