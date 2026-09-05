package com.clxmhcs.chinaunicom

import android.app.Application
import android.os.Build
import com.clxmhcs.chinaunicom.core.network.UnicomSessionRenewalDeviceContext
import com.clxmhcs.chinaunicom.core.network.UnicomSessionRenewalDeviceContextProvider
import com.clxmhcs.chinaunicom.core.network.UnicomSessionRenewalEnvironment
import com.clxmhcs.chinaunicom.core.network.currentUnicomLocalIPv4Address
import com.clxmhcs.chinaunicom.data.AndroidLoginDeviceIdentityStore

class ChinaUnicomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val identityStore = AndroidLoginDeviceIdentityStore(this)
        UnicomSessionRenewalEnvironment.install(
            UnicomSessionRenewalDeviceContextProvider {
                val identity = identityStore.identity()
                UnicomSessionRenewalDeviceContext(
                    deviceCode = identity.deviceCode,
                    deviceID = identity.deviceID,
                    uniqueIdentifier = identity.uniqueIdentifier,
                    deviceModel = identity.deviceModel,
                    deviceOS = identity.deviceOS,
                    userAgentSystemVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { identity.deviceOS },
                    localIPv4Address = currentUnicomLocalIPv4Address().orEmpty(),
                )
            },
        )
    }
}
