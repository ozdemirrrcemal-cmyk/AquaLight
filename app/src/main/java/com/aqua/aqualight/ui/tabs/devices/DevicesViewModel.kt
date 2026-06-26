package com.aqua.aqualight.ui.tabs.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    data class DevicesUiState(
        val isEmpty: Boolean = true
    )
}
