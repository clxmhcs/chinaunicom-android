package com.clxmhcs.chinaunicom.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rough flow state holder; UI polish remains deferred to M7.
 *
 * M6 supplies the release repository with application context so app-private account metadata can
 * be restored without retaining an Activity. Debug continues to use its isolated fake fixture.
 */
class FlowViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository = UnicomRepositoryProvider.create(application)
    private val _uiState = MutableStateFlow<FlowUiState>(FlowUiState.Loading)

    val uiState: StateFlow<FlowUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = FlowUiState.Loading

            runCatching {
                repository.loadOverview()
            }.onSuccess { overview ->
                _uiState.value = FlowUiState.Content(overview)
            }.onFailure { throwable ->
                _uiState.value = FlowUiState.Error(
                    throwable.message ?: "数据加载失败"
                )
            }
        }
    }

    fun updateOverview(overview: com.clxmhcs.chinaunicom.model.BusinessOverview) {
        _uiState.value = FlowUiState.Content(overview)
    }
}
