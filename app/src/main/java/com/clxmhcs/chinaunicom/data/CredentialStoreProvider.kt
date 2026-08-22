package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.security.AndroidCredentialStores
import com.clxmhcs.chinaunicom.core.security.CredentialStore

/**
 * M5 credential-storage entry point for app-owned code.
 *
 * Login/repository layers must use this provider rather than writing Cookie,
 * appID or token_online into plain SharedPreferences/files.
 */
object CredentialStoreProvider {
    fun create(context: Context): CredentialStore = AndroidCredentialStores.accountCredentials(context)
}
