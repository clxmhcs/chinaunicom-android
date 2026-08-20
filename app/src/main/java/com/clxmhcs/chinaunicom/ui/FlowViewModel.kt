package com.clxmhcs.chinaunicom.ui

import androidx.lifecycle.ViewModel
import com.clxmhcs.chinaunicom.data.UnicomRepository
import com.clxmhcs.chinaunicom.model.BusinessOverview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M4-G2-C5
 * Flow screen state holder backed by repository boundary.
 */
class FlowViewModel : ViewModel() {

    private val repository = UnicomRepository()

    private val _overview = MutableStateFlow(
        repository.loadOverview()
    )

    val overview: StateFlow<BusinessOverview> = _overview.asStateFlow()

    fun refresh() {
        _overview.value = repository.loadOverview()
    }

    fun updateOverview(overview: BusinessOverview) {
        _overview.value = overview
    }
}
