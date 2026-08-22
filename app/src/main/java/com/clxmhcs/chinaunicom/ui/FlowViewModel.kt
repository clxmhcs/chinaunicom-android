package com.clxmhcs.chinaunicom.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clxmhcs.chinaunicom.data.UnicomRepository
import com.clxmhcs.chinaunicom.data.UnicomRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * M4-G2-C6 rough flow state holder.
 *
 * Repository implementation is supplied by the active build variant. Debug
 * uses an isolated fake fixture; release intentionally has no fake data until
 * M6 wires the production repository graph.
 */
class FlowViewModel(
    private val repository: UnicomRepository = UnicomRepositoryProvider.create(),
) : ViewModel() {

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
