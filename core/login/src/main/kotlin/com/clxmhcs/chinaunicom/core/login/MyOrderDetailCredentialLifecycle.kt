package com.clxmhcs.chinaunicom.core.login

import com.clxmhcs.chinaunicom.core.network.UnicomAPIException
import com.clxmhcs.chinaunicom.core.network.UnicomCookieCodec
import com.clxmhcs.chinaunicom.core.security.CredentialStore
import java.util.UUID

/**
 * M5-owned detail bridge credential boundary.
 *
 * The returned Cookie is transient bridge input only. Callers must not place it in ordinary StateFlow,
 * disk cache, navigation arguments, logs, or analytics.
 */
class MyOrderDetailCredentialLifecycle(
    private val credentialStore: CredentialStore,
) {
    fun requireCookieHeader(accountID: UUID): String {
        val credentials = credentialStore.read(accountID)
            ?: throw LoginAccountLifecycleException.MissingCredentials
        val cookie = UnicomCookieCodec.normalize(credentials.cookie)
        if (cookie.isEmpty()) throw UnicomAPIException.MissingCookie
        return cookie
    }
}
