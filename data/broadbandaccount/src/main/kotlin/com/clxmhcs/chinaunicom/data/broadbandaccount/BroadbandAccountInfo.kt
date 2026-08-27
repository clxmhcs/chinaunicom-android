package com.clxmhcs.chinaunicom.data.broadbandaccount

import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import java.time.Instant
import java.util.UUID

/**
 * Ordinary metadata for an independently saved China Unicom broadband account.
 * Credential material is intentionally absent and remains in the M5 CredentialStore.
 */
data class BroadbandAccountInfo(
    val id: UUID,
    val serviceNumber: String,
    val displayName: String,
    val idCardLastSix: String,
    val locationName: String,
    val provinceCode: String,
    val cityCode: String,
    val areaCode: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun maskedServiceNumber(): String {
        val value = serviceNumber.trim()
        return if (value.length > 8) {
            value.take(4) + "****" + value.takeLast(4)
        } else {
            value
        }
    }

    /**
     * Source-equivalent temporary adapter used by business modules such as MyPackage.
     * This does not insert broadband accounts into the M6 mobile-account repository.
     */
    fun toUnicomAccount(): UnicomAccount {
        val normalizedName = displayName.trim()
        val normalizedLocation = locationName.trim()
        val resolvedName = normalizedName.ifEmpty {
            if (normalizedLocation.isEmpty()) "宽带号码" else "宽带（$normalizedLocation）"
        }
        return UnicomAccount(
            id = id,
            displayName = resolvedName,
            mobile = serviceNumber.trim(),
            packageName = "宽带账号",
            isEnabled = true,
        )
    }
}
