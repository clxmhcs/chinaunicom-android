package com.clxmhcs.chinaunicom.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.UnicomAccount
import com.clxmhcs.chinaunicom.core.network.UnicomAPIClient
import com.clxmhcs.chinaunicom.core.network.UnicomCookieCodec
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ElectronicReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val credentialStore = CredentialStoreProvider.create(application.applicationContext)
    private val api = UnicomAPIClient()
    private val storage = ElectronicReceiptStorage(application.applicationContext)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val _state = MutableStateFlow(
        ElectronicReceiptUiState(
            savedReceipts = storage.load(),
            exportDirectoryUri = preferences.getString(EXPORT_DIRECTORY_KEY, null),
        ),
    )
    val state: StateFlow<ElectronicReceiptUiState> = _state.asStateFlow()

    @Volatile
    private var activeSession: ElectronicReceiptWebSession? = null

    fun reconcileTargets(mobileAccounts: List<UnicomAccount>, broadbandAccounts: List<BroadbandAccountInfo>) {
        val mobileTargets = mobileAccounts
            .filter(UnicomAccount::isEnabled)
            .sortedBy(UnicomAccount::sortOrder)
            .map { account ->
                ElectronicReceiptTarget(
                    id = account.id,
                    serviceNumber = account.mobile.trim(),
                    displayName = account.displayName,
                    kind = ElectronicReceiptTargetKind.MOBILE,
                    sortOrder = account.sortOrder,
                )
            }
        val broadbandTargets = broadbandAccounts.mapIndexed { index, account ->
            ElectronicReceiptTarget(
                id = account.id,
                serviceNumber = account.serviceNumber.trim(),
                displayName = account.displayName.ifBlank { account.locationName.ifBlank { "独立宽带" } },
                kind = ElectronicReceiptTargetKind.BROADBAND,
                sortOrder = Int.MAX_VALUE - broadbandAccounts.size + index,
            )
        }
        val targets = mobileTargets + broadbandTargets
        _state.update { current ->
            val selected = current.selectedTargetID?.takeIf { id -> targets.any { it.id == id } }
            current.copy(targets = targets, selectedTargetID = selected)
        }
    }

    fun activate(targetID: UUID) {
        val target = _state.value.targets.firstOrNull { it.id == targetID } ?: return
        if (_state.value.isActivating) return
        activeSession = null
        _state.update {
            it.copy(
                selectedTargetID = targetID,
                activeTargetID = null,
                isActivating = true,
                pdfCandidate = null,
                statusMessage = null,
                errorMessage = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val stored = credentialStore.read(target.id) ?: error("账号缺少登录凭据")
                val activated = when (target.kind) {
                    ElectronicReceiptTargetKind.MOBILE -> api.activateSession(stored)
                    ElectronicReceiptTargetKind.BROADBAND -> api.fetchQuota(stored).updatedCredentials ?: stored
                }
                if (activated != stored) credentialStore.save(target.id, activated)
                buildWebSession(target, activated)
            }.onSuccess { session ->
                activeSession = session
                _state.update {
                    it.copy(
                        activeTargetID = target.id,
                        isActivating = false,
                        activationSerial = it.activationSerial + 1,
                        statusMessage = "登录态已激活，正在打开电子受理单",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                activeSession = null
                _state.update {
                    it.copy(
                        activeTargetID = null,
                        isActivating = false,
                        errorMessage = safeMessage(error),
                    )
                }
            }
        }
    }

    internal fun activeWebSession(): ElectronicReceiptWebSession? = activeSession

    fun reportPdfCandidate(candidate: ElectronicReceiptPdfCandidate?) {
        _state.update { it.copy(pdfCandidate = candidate) }
    }

    fun reportBrowserError(message: String) {
        _state.update { it.copy(errorMessage = message.takeIf(String::isNotBlank)) }
    }

    fun savePdf(candidate: ElectronicReceiptPdfCandidate, browserCookieHeader: String?) {
        val target = _state.value.targets.firstOrNull { it.id == _state.value.activeTargetID } ?: return
        val session = activeSession?.takeIf { it.targetID == target.id } ?: return
        if (_state.value.isSavingPdf) return
        _state.update { it.copy(isSavingPdf = true, statusMessage = "正在保存 PDF…", errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cookie = browserCookieHeader?.trim()?.takeIf(String::isNotEmpty) ?: session.cookieHeader
                val bytes = downloadPdf(candidate.urlString, cookie, session.userAgent)
                var record = storage.save(target, candidate, bytes)
                val exportDirectory = _state.value.exportDirectoryUri
                if (!exportDirectory.isNullOrBlank()) {
                    runCatching { exportRecord(record, Uri.parse(exportDirectory)) }
                        .onSuccess { exported -> record = exported }
                }
                record
            }.onSuccess { record ->
                _state.update {
                    it.copy(
                        isSavingPdf = false,
                        savedReceipts = storage.load(),
                        statusMessage = "电子受理单已保存：${record.dateText}",
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isSavingPdf = false, errorMessage = safeMessage(error)) }
            }
        }
    }

    fun setExportDirectory(uri: Uri?) {
        if (uri == null) return
        val app = getApplication<Application>()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        preferences.edit().putString(EXPORT_DIRECTORY_KEY, uri.toString()).apply()
        _state.update { it.copy(exportDirectoryUri = uri.toString(), statusMessage = "已选择 PDF 导出目录") }
    }

    fun clearExportDirectory() {
        preferences.edit().remove(EXPORT_DIRECTORY_KEY).apply()
        _state.update { it.copy(exportDirectoryUri = null, statusMessage = "已取消自动导出目录") }
    }

    fun exportReceipt(id: String) {
        val item = _state.value.savedReceipts.firstOrNull { it.id == id } ?: return
        val raw = _state.value.exportDirectoryUri ?: run {
            _state.update { it.copy(errorMessage = "请先选择 PDF 导出目录") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { exportRecord(item, Uri.parse(raw)) }
                .onSuccess {
                    _state.update { state -> state.copy(savedReceipts = storage.load(), statusMessage = "PDF 已导出到所选目录", errorMessage = null) }
                }
                .onFailure { error -> _state.update { it.copy(errorMessage = safeMessage(error)) } }
        }
    }

    fun deleteReceipt(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { storage.delete(id) }
                .onSuccess { _state.update { it.copy(savedReceipts = storage.load(), statusMessage = "已删除本地受理单") } }
                .onFailure { error -> _state.update { it.copy(errorMessage = safeMessage(error)) } }
        }
    }

    internal fun pdfFile(item: SavedElectronicReceipt): File = storage.fileFor(item)

    private fun buildWebSession(target: ElectronicReceiptTarget, credentials: AccountCredentials): ElectronicReceiptWebSession {
        val cookie = augmentedCookieHeader(credentials.cookie, target)
        val map = cookieMap(cookie)
        val provinceCity = if (target.kind == ElectronicReceiptTargetKind.MOBILE) provinceCity(map) else null
        val deviceCode = map["devicedid"] ?: map["d_devicecode"] ?: target.id.toString().uppercase(Locale.ROOT)
        return ElectronicReceiptWebSession(
            targetID = target.id,
            serviceNumber = target.serviceNumber,
            loginType = target.loginType,
            cookieHeader = cookie,
            userAgent = receiptUserAgent(),
            deviceCode = deviceCode,
            provinceCode = provinceCity?.first.orEmpty(),
            cityCode = provinceCity?.second.orEmpty(),
        )
    }

    private fun augmentedCookieHeader(raw: String, target: ElectronicReceiptTarget): String {
        val ordered = LinkedHashMap<String, Pair<String, String>>()
        UnicomCookieCodec.normalize(raw).split(';').forEach { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@forEach
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) ordered[name.lowercase(Locale.ROOT)] = name to value
        }
        fun set(name: String, value: String) {
            if (value.isNotEmpty()) ordered[name.lowercase(Locale.ROOT)] = name to value
        }
        fun add(name: String, value: String) {
            if (value.isNotEmpty()) ordered.putIfAbsent(name.lowercase(Locale.ROOT), name to value)
        }
        val number = target.serviceNumber.trim()
        val deviceCode = target.id.toString().uppercase(Locale.ROOT)
        set("unicomMallUid", number)
        set("c_mobile", number)
        set("u_account", number)
        add("c_version", "iphone_c@12.1300")
        set("login_type", target.loginType)
        add("random_login", "0")
        add("wo_family", "0")
        add("TOKEN_NET", "UNI")
        add("TOKEN_USER_NET", "1")
        add("channel", "GGPD")
        add("devicedId", deviceCode)
        add("d_deviceCode", deviceCode)
        add("PvSessionId", receiptSessionID(target.id))
        return ordered.values.joinToString("; ") { "${it.first}=${it.second}" }
    }

    private fun cookieMap(header: String): Map<String, String> = header.split(';').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null else part.substring(0, index).trim().lowercase(Locale.ROOT) to part.substring(index + 1).trim()
    }.toMap()

    private fun provinceCity(map: Map<String, String>): Pair<String, String>? {
        for (key in listOf("city", "mallcity", "usercity", "cdn_area", "gipgeo")) {
            val pieces = map[key]?.split('|') ?: continue
            if (pieces.size >= 2 && pieces[0].isNotBlank() && pieces[1].isNotBlank()) return pieces[0] to pieces[1]
        }
        return null
    }

    private fun receiptSessionID(accountID: UUID): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getDefault() }
        return formatter.format(Date()) + accountID.toString().uppercase(Locale.ROOT)
    }

    private fun receiptUserAgent(): String =
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0 Mobile Safari/537.36 unicom{version:iphone_c@12.1300};ltst;OSVersion/${Build.VERSION.RELEASE}"

    private fun downloadPdf(url: String, cookie: String, userAgent: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Cookie", cookie)
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Referer", RECEIPT_ENTRY_URL)
            setRequestProperty("Accept", "application/pdf,*/*")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("电子受理单 PDF 下载失败（HTTP $code）")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size < 4 || !bytes.copyOfRange(0, 4).contentEquals("%PDF".toByteArray())) {
                error("联通服务器没有返回 PDF 文件")
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun exportRecord(item: SavedElectronicReceipt, treeUri: Uri): SavedElectronicReceipt {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val treeID = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeID)
        val source = storage.fileFor(item)
        require(source.exists()) { "本地 PDF 已不存在" }
        val displayName = "电子受理单_${item.dateText}_${item.orderID.takeLast(8)}.pdf"
        val document = DocumentsContract.createDocument(resolver, parent, "application/pdf", displayName)
            ?: error("无法在所选目录创建 PDF")
        resolver.openOutputStream(document, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
            ?: error("无法写入所选目录")
        return storage.markExported(item.id, document.toString()) ?: item
    }

    private fun safeMessage(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: "电子受理单操作失败"

    companion object {
        internal const val RECEIPT_ENTRY_URL = "https://imgxx.client.10010.com/dianzishoulidan/index.html"
        private const val PREFERENCES = "electronic_receipt_preferences"
        private const val EXPORT_DIRECTORY_KEY = "export_directory_uri"
    }
}
