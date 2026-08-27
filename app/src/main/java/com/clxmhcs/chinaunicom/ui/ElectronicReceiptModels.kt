package com.clxmhcs.chinaunicom.ui

import android.net.Uri
import java.util.UUID

enum class ElectronicReceiptTargetKind { MOBILE, BROADBAND }

data class ElectronicReceiptTarget(
    val id: UUID,
    val serviceNumber: String,
    val displayName: String,
    val kind: ElectronicReceiptTargetKind,
    val sortOrder: Int,
) {
    val loginType: String get() = if (kind == ElectronicReceiptTargetKind.BROADBAND) "03" else "01"
    val maskedNumber: String
        get() {
            val value = serviceNumber.trim()
            return when {
                value.length >= 11 -> value.take(3) + "****" + value.takeLast(4)
                value.length > 8 -> value.take(4) + "****" + value.takeLast(4)
                else -> value
            }
        }
    val menuText: String
        get() = displayName.trim().takeIf { it.isNotEmpty() && it != maskedNumber }?.let { "$it  $maskedNumber" } ?: maskedNumber
}

data class ElectronicReceiptPdfCandidate(
    val urlString: String,
    val orderID: String,
    val acceptDate: String,
    val queryMonth: String,
) {
    companion object {
        fun from(url: String?): ElectronicReceiptPdfCandidate? {
            val normalized = url?.replace("&amp;", "&")?.replace("\\u0026", "&")?.trim().orEmpty()
            if (!normalized.contains("/servicequerybusiness/queryNoPaper/noPaperDetailPdfByUser", ignoreCase = true)) return null
            val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
            val orderID = uri.getQueryParameter("orderId")?.trim().orEmpty()
            val acceptDate = uri.getQueryParameter("acceptDate")?.trim().orEmpty()
            if (orderID.isEmpty() || acceptDate.isEmpty()) return null
            return ElectronicReceiptPdfCandidate(
                urlString = normalized,
                orderID = orderID,
                acceptDate = acceptDate,
                queryMonth = acceptDate.take(6),
            )
        }
    }
}

data class SavedElectronicReceipt(
    val id: String,
    val accountID: UUID,
    val maskedNumber: String,
    val orderID: String,
    val acceptDate: String,
    val queryMonth: String,
    val fileName: String,
    val savedAtEpochMillis: Long,
    val exportedDocumentUri: String? = null,
) {
    val dateText: String
        get() = if (acceptDate.length >= 8) "${acceptDate.take(4)}-${acceptDate.substring(4, 6)}-${acceptDate.substring(6, 8)}" else acceptDate
}

internal data class ElectronicReceiptWebSession(
    val targetID: UUID,
    val serviceNumber: String,
    val loginType: String,
    val cookieHeader: String,
    val userAgent: String,
    val deviceCode: String,
    val provinceCode: String,
    val cityCode: String,
)

data class ElectronicReceiptUiState(
    val targets: List<ElectronicReceiptTarget> = emptyList(),
    val selectedTargetID: UUID? = null,
    val activeTargetID: UUID? = null,
    val activationSerial: Long = 0,
    val isActivating: Boolean = false,
    val isSavingPdf: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val pdfCandidate: ElectronicReceiptPdfCandidate? = null,
    val savedReceipts: List<SavedElectronicReceipt> = emptyList(),
    val exportDirectoryUri: String? = null,
)
