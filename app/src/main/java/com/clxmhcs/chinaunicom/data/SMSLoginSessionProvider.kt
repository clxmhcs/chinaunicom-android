package com.clxmhcs.chinaunicom.data

import android.content.Context
import com.clxmhcs.chinaunicom.core.network.UnicomSMSLoginSession

/** Temporary app composition root for M5 login code. */
object SMSLoginSessionProvider {
    fun create(context: Context): UnicomSMSLoginSession = UnicomSMSLoginSession(
        identityStore = AndroidLoginDeviceIdentityStore(context),
    )
}
