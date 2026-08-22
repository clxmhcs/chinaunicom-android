package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.network.UnicomPasswordLoginSession

/** App composition root for the M5-C password-login protocol core. */
object PasswordLoginSessionProvider {
    fun create(context: Context): UnicomPasswordLoginSession = UnicomPasswordLoginSession(
        identityStore = AndroidLoginDeviceIdentityStore(context),
    )
}
