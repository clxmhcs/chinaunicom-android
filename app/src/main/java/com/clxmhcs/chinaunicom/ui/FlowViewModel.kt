package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import com.clxmhcs.chinaunicom.data.refresh.QuotaAutomaticRefreshTrigger
import com.clxmhcs.chinaunicom.model.BusinessAggregator
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rough flow state holder; visual parity remains deferred.
 *
 * M6-B observes the production StateFlow immediately, then invokes the source-equivalent cold
 * launch / foreground quota refresh gates. Repository refresh failures are reflected in account
 * AppState rather than replacing already-restored content with a synthetic UI error.
 */
class FlowViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = UnicomRepositoryProvider.create(application)
    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)

    val uiState: StateFlow<FlowUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.appState.collect { state ->
                _uiState.value = FlowUiState.Content(
                    BusinessAggregator.aggregateAccounts(state.accounts),
                )
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
        viewModelScope.launch {
            repository.refreshAccount(accountID)
        }
    }

    fun onForeground() {
        viewModelScope.launch {
            repository.autoRefreshIfNeeded(QuotaAutomaticRefreshTrigger.FOREGROUND)
        }
    }

    fun onQuotaPolicyChanged() {
        viewModelScope.launch {
            repository.autoRefreshIfNeeded(QuotaAutomaticRefreshTrigger.POLICY_CHANGE)
        }
    }
}
