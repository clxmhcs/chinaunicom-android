package com.clxmhcs.chinaunicom.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.core.network.UnicomLoginCaptchaChallenge
import com.clxmhcs.chinaunicom.core.network.UnicomSMSLoginSession
import com.clxmhcs.chinaunicom.core.network.UnicomSMSSendOutcome
import com.clxmhcs.chinaunicom.core.storage.AndroidAccountMetadataStores
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.LoginAccountLifecycleProvider
import com.clxmhcs.chinaunicom.data.SMSLoginSessionProvider
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.account.DefaultAccountRepository
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AccountOnboardingUiState(
    val isSendingCode: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isImporting: Boolean = false,
    val statusTitle: String? = null,
    val statusMessage: String? = null,
    val captchaChallenge: UnicomLoginCaptchaChallenge? = null,
    val loginSucceeded: Boolean = false,
)

/** Shared functional state holder for flow/voice plus minimal account onboarding wiring. */
class FlowViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = UnicomRepositoryProvider.create(application)
    private val accountMetadataRepository = DefaultAccountRepository(
        store = AndroidAccountMetadataStores.accounts(application),
    )
    private val loginLifecycle = LoginAccountLifecycleProvider.create(application)
    private val credentialStore = CredentialStoreProvider.create(application)

    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)
    private val _accountOnboardingState = MutableStateFlow(AccountOnboardingUiState())
    private var balanceLoopJob: Job? = null
    private var smsLoginSession: UnicomSMSLoginSession? = null
    private var smsLoginMobile: String? = null

    val uiState: StateFlow<FlowUiState> = _uiState.asStateFlow()
    val accountOnboardingState: StateFlow<AccountOnboardingUiState> = _accountOnboardingState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.appState, repository.balanceState) { appState, balanceState ->
                FlowUiState.Content(appState = appState, balanceState = balanceState)
            }.collect { state ->
                _uiState.value = state
            }
        }
        viewModelScope.launch {
            runCatching {
                repository.autoRefreshIfNeeded(QuotaAutomaticRefreshTrigger.COLD_LAUNCH)
            }.onFailure { throwable ->
                if (repository.appState.value.accounts.isEmpty()) {
                    _uiState.value = FlowUiState.Error(throwable.message ?: "数据加载失败")
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refreshAll() }
                .onFailure { throwable ->
                    if (repository.appState.value.accounts.isEmpty()) {
                        _uiState.value = FlowUiState.Error(throwable.message ?: "刷新失败")
                    }
                }
        }
    }

    fun refreshAccount(accountID: UUID) {
        viewModelScope.launch { repository.refreshAccount(accountID) }
    }

    fun refreshHomeBalanceManually() {
        viewModelScope.launch { repository.refreshHomeBalanceManually() }
    }

    fun setHomeBalanceAccountID(accountID: UUID?) {
        repository.setHomeBalanceAccountID(accountID)
    }

    /** Reuses M6's financial representative choice for comprehensive-card phone-bill entry. */
    fun financialRepresentativeAccountID(accountID: UUID): UUID? =
        repository.financialRepresentativeAccountID(accountID)

    fun sendSMSCode(mobile: String) {
        val normalizedMobile = normalizeMobile(mobile)
        if (normalizedMobile.length != 11) {
            publishOnboardingFailure("发送失败", "请输入正确的 11 位联通手机号")
            return
        }
        if (_accountOnboardingState.value.isSendingCode || _accountOnboardingState.value.isLoggingIn) return

        val session = SMSLoginSessionProvider.create(getApplication()).also {
            smsLoginSession = it
            smsLoginMobile = normalizedMobile
        }
        _accountOnboardingState.update {
            it.copy(
                isSendingCode = true,
                statusTitle = null,
                statusMessage = null,
                captchaChallenge = null,
                loginSucceeded = false,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                session.sendCode(
                    mobile = normalizedMobile,
                    preferredAppID = reusableAppID(normalizedMobile),
                )
            }.onSuccess { outcome ->
                when (outcome) {
                    is UnicomSMSSendOutcome.CodeSent -> {
                        _accountOnboardingState.update {
                            it.copy(
                                isSendingCode = false,
                                statusTitle = "验证码已发送",
                                statusMessage = outcome.message,
                                captchaChallenge = null,
                            )
                        }
                    }
                    is UnicomSMSSendOutcome.CaptchaRequired -> {
                        _accountOnboardingState.update {
                            it.copy(
                                isSendingCode = false,
                                statusTitle = "需要安全验证",
                                statusMessage = outcome.challenge.message,
                                captchaChallenge = outcome.challenge,
                            )
                        }
                    }
                }
            }.onFailure { error ->
                publishOnboardingFailure("发送失败", safeMessage(error))
            }
        }
    }

    fun captchaCookieHeader(): String = smsLoginSession?.currentCookieHeader().orEmpty()

    fun captchaSystemInfo(): Map<String, String> = smsLoginSession?.captchaSystemInfo().orEmpty()

    fun captchaUserAgent(): String {
        val deviceOS = captchaSystemInfo()["deviceOS"].orEmpty().ifBlank { "18.0" }
        val systemToken = deviceOS.replace('.', '_')
        return "Mozilla/5.0 (iPhone; CPU iPhone OS $systemToken like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) unicom{version:${UnicomSMSLoginSession.VERSION}};" +
            "ltst;OSVersion/$deviceOS"
    }

    fun continueSMSCaptcha(resultToken: String) {
        val token = resultToken.trim()
        val session = smsLoginSession
        val mobile = smsLoginMobile
        if (token.isEmpty()) return
        if (session == null || mobile.isNullOrBlank()) {
            publishOnboardingFailure("验证失败", "安全验证会话已失效，请重新获取验证码")
            return
        }
        if (_accountOnboardingState.value.isSendingCode || _accountOnboardingState.value.isLoggingIn) return

        _accountOnboardingState.update {
            it.copy(
                isSendingCode = true,
                statusTitle = null,
                statusMessage = null,
                captchaChallenge = null,
                loginSucceeded = false,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                session.sendCode(
                    mobile = mobile,
                    resultToken = token,
                    preferredAppID = reusableAppID(mobile),
                )
            }.onSuccess { outcome ->
                when (outcome) {
                    is UnicomSMSSendOutcome.CodeSent -> {
                        _accountOnboardingState.update {
                            it.copy(
                                isSendingCode = false,
                                statusTitle = "验证码已发送",
                                statusMessage = outcome.message,
                                captchaChallenge = null,
                            )
                        }
                    }
                    is UnicomSMSSendOutcome.CaptchaRequired -> {
                        _accountOnboardingState.update {
                            it.copy(
                                isSendingCode = false,
                                statusTitle = "需要安全验证",
                                statusMessage = outcome.challenge.message,
                                captchaChallenge = outcome.challenge,
                            )
                        }
                    }
                }
            }.onFailure { error ->
                publishOnboardingFailure("发送失败", safeMessage(error))
            }
        }
    }

    fun dismissSMSCaptcha() {
        _accountOnboardingState.update {
            it.copy(statusTitle = null, statusMessage = null, captchaChallenge = null)
        }
    }

    fun loginWithSMS(mobile: String, verificationCode: String) {
        val normalizedMobile = normalizeMobile(mobile)
        val normalizedCode = verificationCode.filter(Char::isDigit)
        if (normalizedMobile.length != 11) {
            publishOnboardingFailure("登录失败", "请输入正确的 11 位联通手机号")
            return
        }
        if (normalizedCode.length != 6) {
            publishOnboardingFailure("登录失败", "请输入收到的 6 位短信验证码")
            return
        }
        if (_accountOnboardingState.value.isLoggingIn || _accountOnboardingState.value.isSendingCode) return

        val session = if (smsLoginMobile == normalizedMobile) {
            smsLoginSession ?: SMSLoginSessionProvider.create(getApplication())
        } else {
            SMSLoginSessionProvider.create(getApplication())
        }
        smsLoginSession = session
        smsLoginMobile = normalizedMobile
        _accountOnboardingState.update {
            it.copy(
                isLoggingIn = true,
                statusTitle = null,
                statusMessage = null,
                captchaChallenge = null,
                loginSucceeded = false,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val result = session.login(
                    mobile = normalizedMobile,
                    code = normalizedCode,
                    preferredAppID = reusableAppID(normalizedMobile),
                )
                loginLifecycle.createValidatedSMSAccount(
                    mobile = normalizedMobile,
                    loginResult = result,
                ) { seed ->
                    accountMetadataRepository.createValidatedAccount(displayName = "", seed = seed)
                }
                repository.reloadAccountsFromPersistence()
                result.invalidAt
            }.onSuccess { invalidAt ->
                smsLoginSession = null
                smsLoginMobile = null
                val validity = invalidAt?.trim()?.takeIf { it.isNotEmpty() } ?: "联通未返回 invalidat"
                _accountOnboardingState.update {
                    it.copy(
                        isLoggingIn = false,
                        statusTitle = "登录成功",
                        statusMessage = "手机号已验证并写入正式账号库。登录态有效期：$validity。短信验证码未保存。",
                        captchaChallenge = null,
                        loginSucceeded = true,
                    )
                }
            }.onFailure { error ->
                publishOnboardingFailure("登录失败", safeMessage(error))
            }
        }
    }

    /**
     * Imports the same version-1 credential archive used by the historical M4 parity harness.
     * Raw archive bytes are read only on-device, never logged, and cleared after parsing.
     */
    fun importIOSCredentialArchive(uri: Uri) {
        if (_accountOnboardingState.value.isImporting || _accountOnboardingState.value.isLoggingIn) return
        _accountOnboardingState.update {
            it.copy(
                isImporting = true,
                statusTitle = null,
                statusMessage = null,
                captchaChallenge = null,
                loginSucceeded = false,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            var importedCount = 0
            var skippedCount = 0
            val outcome = runCatching {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取凭据文件")
                require(bytes.size <= MAX_CREDENTIAL_ARCHIVE_BYTES) { "凭据文件过大" }
                val archive = try {
                    JSONObject(bytes.toString(Charsets.UTF_8))
                } finally {
                    bytes.fill(0)
                }
                require(archive.optInt("version", -1) == 1) { "不支持的凭据文件版本" }
                val items = archive.optJSONArray("accounts") ?: error("凭据文件缺少 accounts")
                require(items.length() > 0) { "凭据文件没有可导入账号" }

                val knownMobiles = accountMetadataRepository.loadAccounts()
                    .map { normalizeMobile(it.mobile) }
                    .filter { it.length == 11 }
                    .toMutableSet()

                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    val accountObject = item.optJSONObject("account") ?: JSONObject()
                    val credentialObject = item.getJSONObject("credentials")
                    val mobileValue = normalizeMobile(accountObject.optString("mobile", ""))
                    require(mobileValue.length == 11) { "第 ${index + 1} 个账号手机号无效" }
                    if (mobileValue in knownMobiles) {
                        skippedCount += 1
                        continue
                    }

                    val cookie = credentialObject.optString("cookie", "").trim()
                    require(cookie.isNotEmpty()) { "第 ${index + 1} 个账号缺少 Cookie" }
                    val credentials = AccountCredentials(
                        cookie = cookie,
                        appID = firstOptionalString(credentialObject, "appID", "appId"),
                        tokenOnline = firstOptionalString(credentialObject, "tokenOnline", "token_online"),
                    )
                    val displayName = accountObject.optString("displayName", "")
                    loginLifecycle.createValidatedAccount(
                        mobile = mobileValue,
                        credentials = credentials,
                    ) { seed ->
                        accountMetadataRepository.createValidatedAccount(displayName = displayName, seed = seed)
                    }
                    knownMobiles += mobileValue
                    importedCount += 1
                }
                repository.reloadAccountsFromPersistence()
            }

            outcome.onSuccess {
                val detail = buildString {
                    append("已导入 $importedCount 个联通号码")
                    if (skippedCount > 0) append("，跳过 $skippedCount 个已存在号码")
                    append("。原始 Cookie/appId/token_online 未写入普通账号数据。")
                }
                _accountOnboardingState.update {
                    it.copy(
                        isImporting = false,
                        statusTitle = "导入完成",
                        statusMessage = detail,
                    )
                }
            }.onFailure { error ->
                runCatching { repository.reloadAccountsFromPersistence() }
                val prefix = if (importedCount > 0) "已成功导入 $importedCount 个号码；随后失败：" else ""
                _accountOnboardingState.update {
                    it.copy(
                        isImporting = false,
                        statusTitle = "导入失败",
                        statusMessage = prefix + safeMessage(error),
                    )
                }
            }
        }
    }

    fun consumeLoginSuccess() {
        _accountOnboardingState.update { it.copy(loginSucceeded = false) }
    }

    fun clearOnboardingStatus() {
        _accountOnboardingState.update {
            it.copy(statusTitle = null, statusMessage = null, captchaChallenge = null, loginSucceeded = false)
        }
    }

    fun onForeground() {
        viewModelScope.launch {
            repository.autoRefreshIfNeeded(QuotaAutomaticRefreshTrigger.FOREGROUND)
        }
        if (balanceLoopJob?.isActive != true) {
            balanceLoopJob = viewModelScope.launch {
                repository.runBalanceAutoRefreshLoop()
            }
        }
    }

    fun onBackground() {
        balanceLoopJob?.cancel()
        balanceLoopJob = null
    }

    fun onQuotaPolicyChanged() {
        viewModelScope.launch {
            repository.autoRefreshIfNeeded(QuotaAutomaticRefreshTrigger.POLICY_CHANGE)
        }
    }

    private fun reusableAppID(mobile: String): String? {
        val accounts = repository.appState.value.accounts
        val ordered = accounts.sortedBy { if (normalizeMobile(it.mobile) == mobile) 0 else 1 }
        for (account in ordered) {
            val appID = runCatching { credentialStore.read(account.id)?.appID }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            if (appID != null) return appID
        }
        return null
    }

    private fun publishOnboardingFailure(title: String, message: String) {
        _accountOnboardingState.update {
            it.copy(
                isSendingCode = false,
                isLoggingIn = false,
                isImporting = false,
                statusTitle = title,
                statusMessage = message,
                loginSucceeded = false,
            )
        }
    }

    private fun normalizeMobile(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length == 13 && digits.startsWith("86")) digits.drop(2) else digits
    }

    private fun firstOptionalString(objectValue: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            val value = objectValue.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: "操作失败"

    private companion object {
        const val MAX_CREDENTIAL_ARCHIVE_BYTES = 2 * 1024 * 1024
    }
}
