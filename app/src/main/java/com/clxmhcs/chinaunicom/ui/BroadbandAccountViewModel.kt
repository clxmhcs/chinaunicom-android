package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.core.model.AccountCredentials
import com.clxmhcs.chinaunicom.data.CredentialStoreProvider
import com.clxmhcs.chinaunicom.data.broadbandaccount.AndroidBroadbandAccountMetadataStore
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountDraft
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountInfo
import com.clxmhcs.chinaunicom.data.broadbandaccount.BroadbandAccountLifecycle
import com.clxmhcs.chinaunicom.data.broadbandaccount.DefaultBroadbandAccountRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BroadbandAccountUiState(
    val accounts: List<BroadbandAccountInfo> = emptyList(),
    val isWorking: Boolean = false,
    val statusTitle: String? = null,
    val statusMessage: String? = null,
    val operationSerial: Long = 0,
    val lastSaveSucceeded: Boolean = false,
)

/** Root-scoped M9-B4 broadband-account authority; intentionally separate from M6 mobile AppState. */
class BroadbandAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = DefaultBroadbandAccountRepository(
        AndroidBroadbandAccountMetadataStore(appContext),
    )
    private val lifecycle = BroadbandAccountLifecycle(
        repository = repository,
        credentialStore = CredentialStoreProvider.create(appContext),
    )
    private val mutableState = MutableStateFlow(
        BroadbandAccountUiState(accounts = lifecycle.loadAccounts()),
    )
    val state: StateFlow<BroadbandAccountUiState> = mutableState.asStateFlow()

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = lifecycle.loadAccounts()
            mutableState.update { it.copy(accounts = accounts) }
        }
    }

    fun validateAndSave(
        draft: BroadbandAccountDraft,
        cookie: String,
        appID: String,
        tokenOnline: String,
    ) {
        if (mutableState.value.isWorking) return
        mutableState.update {
            it.copy(
                isWorking = true,
                statusTitle = null,
                statusMessage = null,
                lastSaveSucceeded = false,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                lifecycle.validateAndSave(
                    draft = draft,
                    enteredCredentials = AccountCredentials(
                        cookie = cookie,
                        appID = appID.trim().takeIf(String::isNotEmpty),
                        tokenOnline = tokenOnline.trim().takeIf(String::isNotEmpty),
                    ),
                )
            }.onSuccess { saved ->
                mutableState.update {
                    it.copy(
                        accounts = lifecycle.loadAccounts(),
                        isWorking = false,
                        statusTitle = "验证成功",
                        statusMessage = "宽带凭据已通过联通真实余量验证，并安全保存到本机。",
                        operationSerial = it.operationSerial + 1,
                        lastSaveSucceeded = true,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        accounts = lifecycle.loadAccounts(),
                        isWorking = false,
                        statusTitle = "验证失败",
                        statusMessage = error.message?.take(240) ?: "宽带凭据验证或保存失败。",
                        operationSerial = it.operationSerial + 1,
                        lastSaveSucceeded = false,
                    )
                }
            }
        }
    }

    fun remove(accountID: UUID) {
        if (mutableState.value.isWorking) return
        mutableState.update { it.copy(isWorking = true, statusTitle = null, statusMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { lifecycle.remove(accountID) }
                .onSuccess {
                    mutableState.update { old ->
                        old.copy(
                            accounts = lifecycle.loadAccounts(),
                            isWorking = false,
                            statusTitle = "已删除",
                            statusMessage = "宽带账号元数据和对应本机凭据已删除。",
                            operationSerial = old.operationSerial + 1,
                            lastSaveSucceeded = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { old ->
                        old.copy(
                            accounts = lifecycle.loadAccounts(),
                            isWorking = false,
                            statusTitle = "删除失败",
                            statusMessage = error.message?.take(240) ?: "宽带账号删除失败。",
                            operationSerial = old.operationSerial + 1,
                            lastSaveSucceeded = false,
                        )
                    }
                }
        }
    }

    fun clearStatus() {
        mutableState.update { it.copy(statusTitle = null, statusMessage = null, lastSaveSucceeded = false) }
    }
}
