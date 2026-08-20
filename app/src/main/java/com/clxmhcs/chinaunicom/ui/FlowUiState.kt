package com.clxmhcs.chinaunicom.ui

import com.clxmhcs.chinaunicom.model.BusinessOverview

/**
 * M4-G2-C6
 * UI state contract for flow screen.
 */
sealed interface FlowUiState {
    data object Loading : FlowUiState

    data class Content(
        val overview: BusinessOverview
    ) : FlowUiState

    data class Error(
        val message: String
    ) : FlowUiState
}
