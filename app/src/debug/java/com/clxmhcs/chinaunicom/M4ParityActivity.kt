package com.clxmhcs.chinaunicom

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.model.FlowPackage
import com.clxmhcs.chinaunicom.core.model.QuotaFetchResult
import com.clxmhcs.chinaunicom.core.model.RemainingQuerySnapshot
import com.clxmhcs.chinaunicom.core.model.VoicePackage
import com.clxmhcs.chinaunicom.core.network.UnicomAPIClient
import com.clxmhcs.chinaunicom.core.network.UnicomAPIException
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * M4-F debug-only real-network parity harness.
 *
 * This Activity exists only in the debug source set. It reads the frozen iOS v1 credential archive
 * through Android's document picker, keeps credential material in process memory only, performs the
 * M4 quota/balance calls, and emits a sanitized result report. It never prints, persists, copies or
 * exports raw Cookie/appId/token_online values or authenticated response bodies.
 */
class M4ParityActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var runButton: Button
    private lateinit var exportButton: Button
    private lateinit var copyButton: Button
    private lateinit var statusView: TextView
    private lateinit var reportView: TextView
    private var sanitizedReport: String? = null

    private val openArchive = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runArchive(uri)
    }

    private val createReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val report = sanitizedReport ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(report.toByteArray(Charsets.UTF_8))
            } ?: error("output stream unavailable")
        }.onSuccess {
            Toast.makeText(this, "脱敏报告已保存", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "报告保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "M4 联网一致性验收"
        setContentView(buildContentView())
    }

    override fun onDestroy() {
        executor.shutdownNow()
        sanitizedReport = null
        super.onDestroy()
    }

    private fun buildContentView(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "M4-F 真机联网一致性验收（仅 Debug）"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "选择 iOS：设置 → 账户凭据 → 导出全部凭据 生成的 JSON。原始文件只在本机内存读取；Cookie、appId、token_online、原始响应均不会写入报告。"
            textSize = 15f
            setPadding(0, dp(12), 0, dp(16))
        })

        runButton = Button(this).apply {
            text = "选择 iOS 凭据 JSON 并运行"
            setOnClickListener { openArchive.launch(arrayOf("application/json", "text/json", "text/plain")) }
        }
        root.addView(runButton, matchWidth())

        exportButton = Button(this).apply {
            text = "导出脱敏 TXT 报告"
            isEnabled = false
            setOnClickListener { createReport.launch("ChinaUnicom-M4-F-Android-Parity.txt") }
        }
        root.addView(exportButton, matchWidth())

        copyButton = Button(this).apply {
            text = "复制脱敏报告"
            isEnabled = false
            setOnClickListener {
                val report = sanitizedReport ?: return@setOnClickListener
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("M4-F Android parity", report))
                Toast.makeText(this@M4ParityActivity, "已复制脱敏报告", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(copyButton, matchWidth())

        statusView = TextView(this).apply {
            text = "状态：尚未运行"
            textSize = 15f
            setPadding(0, dp(14), 0, dp(10))
        }
        root.addView(statusView, matchWidth())

        reportView = TextView(this).apply {
            text = "报告会显示在这里。"
            textSize = 13f
            setTextIsSelectable(true)
        }
        root.addView(reportView, matchWidth())

        return ScrollView(this).apply { addView(root) }
    }

    private fun runArchive(uri: Uri) {
        sanitizedReport = null
        runButton.isEnabled = false
        exportButton.isEnabled = false
        copyButton.isEnabled = false
        statusView.text = "状态：正在读取本地凭据并查询联通接口…"
        reportView.text = "凭据不会显示或写入报告。"

        executor.execute {
            val outcome = runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("credential archive unavailable")
                try {
                    buildSanitizedParityReport(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
            runOnUiThread {
                runButton.isEnabled = true
                outcome.onSuccess { report ->
                    sanitizedReport = report
                    reportView.text = report
                    exportButton.isEnabled = true
                    copyButton.isEnabled = true
                    statusView.text = if (report.contains("overall=PASS")) {
                        "状态：Android 真实查询完成"
                    } else {
                        "状态：Android 查询完成，但存在失败项"
                    }
                }.onFailure {
                    sanitizedReport = null
                    reportView.text = "M4-F_INPUT_ERROR=${safeErrorTag(it)}"
                    statusView.text = "状态：凭据文件读取/格式校验失败"
                }
            }
        }
    }

    private fun buildSanitizedParityReport(data: ByteArray): String {
        val archive = JSONObject(data.toString(Charsets.UTF_8))
        val version = archive.optInt("version", -1)
        require(version == 1) { "unsupported archive version" }
        val accounts = archive.optJSONArray("accounts") ?: error("accounts missing")
        require(accounts.length() > 0) { "accounts empty" }

        val client = UnicomAPIClient()
        var allPassed = true
        val body = StringBuilder()
        body.appendLine("M4_F_ANDROID_PARITY_REPORT_V1")
        body.appendLine("generatedAt=${Instant.now()}")
        body.appendLine("networkVerificationCommit=107a3806cdc0ce7d745fb7ea4f9a3dff5db5d649")
        body.appendLine("archiveVersion=$version")
        body.appendLine("accountCount=${accounts.length()}")
        body.appendLine("security.rawCredentialsIncluded=false")
        body.appendLine("security.authenticatedResponseBodyIncluded=false")

        for (index in 0 until accounts.length()) {
            val item = accounts.getJSONObject(index)
            val account = item.optJSONObject("account") ?: JSONObject()
            val credentialsObject = item.getJSONObject("credentials")
            val mobile = account.optString("mobile", "")
            val credentials = AccountCredentials(
                cookie = requiredString(credentialsObject, "cookie"),
                appID = firstOptionalString(credentialsObject, "appID", "appId"),
                tokenOnline = firstOptionalString(credentialsObject, "tokenOnline", "token_online"),
            )

            body.appendLine()
            body.appendLine("[account.${index + 1}]")
            body.appendLine("mobile=${maskMobile(mobile)}")

            var activeCredentials = credentials
            var quotaResult: QuotaFetchResult? = null
            val quotaError = runCatching {
                client.fetchQuota(activeCredentials).also { result ->
                    quotaResult = result
                    result.updatedCredentials?.let { activeCredentials = it }
                }
            }.exceptionOrNull()

            if (quotaError == null) {
                body.appendLine("quota.result=PASS")
                appendQuota(body, quotaResult!!)
            } else {
                allPassed = false
                body.appendLine("quota.result=FAIL")
                body.appendLine("quota.error=${safeErrorTag(quotaError)}")
            }

            val balanceOutcome = runCatching { client.fetchBalance(activeCredentials) }
            balanceOutcome.onSuccess { result ->
                result.updatedCredentials?.let { activeCredentials = it }
                body.appendLine("balance.result=PASS")
                body.appendLine("balance.yuan=${number(result.balanceYuan)}")
                val detail = result.unavailableBalanceDetail
                body.appendLine("balance.current=${safeText(detail?.currentBalance)}")
                body.appendLine("balance.unavailableLimit=${safeText(detail?.unavailableLimitFee)}")
                body.appendLine("balance.frozen=${safeText(detail?.frozenFee)}")
                body.appendLine("balance.totalUnavailable=${safeText(detail?.totalUnavailable)}")
                body.appendLine("balance.limitItemCount=${detail?.limitItems?.size ?: 0}")
                body.appendLine("balance.frozenItemCount=${detail?.frozenItems?.size ?: 0}")
            }.onFailure { error ->
                allPassed = false
                body.appendLine("balance.result=FAIL")
                body.appendLine("balance.error=${safeErrorTag(error)}")
            }

            val credentialMutationObserved = quotaResult?.updatedCredentials != null ||
                balanceOutcome.getOrNull()?.updatedCredentials != null
            body.appendLine("session.credentialMutationObserved=$credentialMutationObserved")
            activeCredentials = AccountCredentials("", null, null)
        }

        body.appendLine()
        body.appendLine("overall=${if (allPassed) "PASS" else "FAIL"}")
        body.appendLine("NEXT=Compare this sanitized Android report with the same-account iOS values; never upload the source credential JSON.")
        return body.toString()
    }

    private fun appendQuota(body: StringBuilder, result: QuotaFetchResult) {
        body.appendLine("quota.resourceStatus=${result.quotaResourceStatus.rawValue}")
        body.appendLine("quota.packageName=${safeText(result.packageName)}")
        body.appendLine("quota.flowCount=${result.packages.size}")
        result.packages.forEachIndexed { index, packageValue -> appendFlow(body, index + 1, packageValue) }
        body.appendLine("quota.voiceCount=${result.voicePackages.size}")
        result.voicePackages.forEachIndexed { index, packageValue -> appendVoice(body, index + 1, packageValue) }
        appendRemaining(body, result.remainingQuerySnapshot)
    }

    private fun appendFlow(body: StringBuilder, index: Int, packageValue: FlowPackage) {
        val prefix = "quota.flow.$index"
        body.appendLine("$prefix.name=${safeText(packageValue.originalName)}")
        body.appendLine("$prefix.totalMB=${number(packageValue.totalMB)}")
        body.appendLine("$prefix.usedMB=${number(packageValue.usedMB)}")
        body.appendLine("$prefix.remainingMB=${number(packageValue.remainingMB)}")
        body.appendLine("$prefix.quotaType=${packageValue.detectedQuotaType.rawValue}")
        body.appendLine("$prefix.category=${packageValue.detectedCategory.rawValue}")
        body.appendLine("$prefix.shareScope=${packageValue.resolvedShareScope.rawValue}")
        body.appendLine("$prefix.carryForward=${packageValue.resolvedCarryForwardScope.rawValue}")
        body.appendLine("$prefix.currentMonthTotalMB=${number(packageValue.currentMonthTotalMB)}")
        body.appendLine("$prefix.carryForwardTotalMB=${number(packageValue.carryForwardTotalMB)}")
    }

    private fun appendVoice(body: StringBuilder, index: Int, packageValue: VoicePackage) {
        val prefix = "quota.voice.$index"
        body.appendLine("$prefix.name=${safeText(packageValue.originalName)}")
        body.appendLine("$prefix.totalMinutes=${number(packageValue.totalMinutes)}")
        body.appendLine("$prefix.usedMinutes=${number(packageValue.usedMinutes)}")
        body.appendLine("$prefix.remainingMinutes=${number(packageValue.remainingMinutes)}")
        body.appendLine("$prefix.unlimited=${packageValue.isUnlimited}")
        body.appendLine("$prefix.shared=${packageValue.isShared}")
    }

    private fun appendRemaining(body: StringBuilder, snapshot: RemainingQuerySnapshot?) {
        if (snapshot == null) {
            body.appendLine("remaining.present=false")
            return
        }
        body.appendLine("remaining.present=true")
        body.appendLine("remaining.memberCount=${snapshot.members.size}")
        body.appendLine("remaining.flowSummaryCount=${snapshot.flowSummaries.size}")
        body.appendLine("remaining.flowCount=${snapshot.flowPackages.size}")
        snapshot.flowPackages.forEachIndexed { index, packageValue ->
            val prefix = "remaining.flow.${index + 1}"
            body.appendLine("$prefix.name=${safeText(packageValue.name)}")
            body.appendLine("$prefix.category=${packageValue.category?.rawValue ?: "--"}")
            body.appendLine("$prefix.totalMB=${number(packageValue.totalMB)}")
            body.appendLine("$prefix.usedMB=${number(packageValue.usedMB)}")
            body.appendLine("$prefix.remainingMB=${number(packageValue.remainingMB)}")
            body.appendLine("$prefix.unlimited=${packageValue.resolvedIsUnlimited}")
            body.appendLine("$prefix.speedLimitMB=${number(packageValue.speedLimitMB)}")
            body.appendLine("$prefix.shared=${packageValue.isShared}")
        }
        body.appendLine("remaining.voiceRemainingMinutes=${number(snapshot.voice.remainingMinutes)}")
        body.appendLine("remaining.voiceUsedMinutes=${number(snapshot.voice.usedMinutes)}")
        body.appendLine("remaining.voicePackageCount=${snapshot.voice.packages.size}")
        body.appendLine("remaining.voiceUnsharedCount=${snapshot.voice.unsharedPackages.size}")
        body.appendLine("remaining.smsRemaining=${number(snapshot.sms.remainingCount)}")
        body.appendLine("remaining.smsUsed=${number(snapshot.sms.usedCount)}")
        body.appendLine("remaining.smsPackageCount=${snapshot.sms.packages.size}")
        body.appendLine("remaining.smsUnsharedCount=${snapshot.sms.unsharedPackages.size}")
    }

    private fun requiredString(objectValue: JSONObject, key: String): String =
        optionalString(objectValue, key) ?: error("missing $key")

    private fun firstOptionalString(objectValue: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { optionalString(objectValue, it) }

    private fun optionalString(objectValue: JSONObject, key: String): String? {
        if (!objectValue.has(key) || objectValue.isNull(key)) return null
        return objectValue.optString(key).trim().takeIf { it.isNotEmpty() }
    }

    private fun maskMobile(value: String): String {
        val normalized = value.filter(Char::isDigit)
        if (normalized.length < 7) return "****"
        return normalized.take(3) + "****" + normalized.takeLast(4)
    }

    private fun safeText(value: String?): String = value
        ?.replace("\r", " ")
        ?.replace("\n", " ")
        ?.replace("\t", " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "--"

    private fun number(value: Double?): String {
        if (value == null || !value.isFinite()) return "--"
        return String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
    }

    private fun safeErrorTag(error: Throwable): String = when (error) {
        is UnicomAPIException.HttpStatus -> "HTTP_${error.statusCode}"
        is UnicomAPIException.SessionExpired -> "SESSION_EXPIRED"
        is UnicomAPIException.MissingCookie -> "MISSING_COOKIE"
        is UnicomAPIException.MissingCredentials -> "MISSING_CREDENTIALS"
        is UnicomAPIException.NoPackages -> "NO_PACKAGES"
        is UnicomAPIException.Server -> "SERVER_ERROR"
        is UnicomAPIException.InvalidResponse -> "INVALID_RESPONSE"
        is IllegalArgumentException -> "INVALID_ARCHIVE"
        else -> error::class.java.simpleName.uppercase(Locale.ROOT).take(60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWidth(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
}
