package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Shared functional state holder for the flow and voice dashboards. */
class FlowViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = UnicomRepositoryProvider.create(application)
    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)
    private var balanceLoopJob: Job? = null

    val uiState: StateFlow<FlowUiState> = _uiState.asStateFlow()

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
}
