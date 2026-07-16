package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(deviceUidText: String, fallbackTitle: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle.ifBlank { DEFAULT_TITLE }, deviceUid)
            }
        }
    }

    private fun emptyState(title: String, deviceUid: String) = DeviceCoolingRootUiState(
        title = title,
        deviceUid = deviceUid,
        connectionStatus = "Offline",
        primaryCountLabel = KIND.primaryCountLabel,
        primarySectionTitle = KIND.primarySectionTitle,
        primarySectionPlaceholder = KIND.primarySectionPlaceholder,
        secondarySectionTitle = KIND.secondarySectionTitle,
        secondarySectionPlaceholder = KIND.secondarySectionPlaceholder
    )

    private fun DeviceRootSnapshot.toRootUiState(fallbackTitle: String): DeviceCoolingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        return DeviceCoolingRootUiState(
            title = title.ifBlank { fallbackTitle }.ifBlank { DEFAULT_TITLE },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this),
            ipText = ipAddress.ifBlank { "Unknown" },
            firmwareText = firmwareLabel.ifBlank { "Unknown" },
            modelText = modelLabel.ifBlank { "Unknown" },
            primaryCountLabel = KIND.primaryCountLabel,
            primaryCountText = fanOutputCount.takeIf { it > 0 }?.toString() ?: "Unknown",
            featuresText = DeviceRootPresentationMapper.overviewFeatureLabel(this, KIND),
            primarySectionTitle = KIND.primarySectionTitle,
            primarySectionPlaceholder = menuSections.primaryText(KIND.primarySectionPlaceholder),
            secondarySectionTitle = KIND.secondarySectionTitle,
            secondarySectionPlaceholder = menuSections.secondaryText(KIND.secondarySectionPlaceholder)
        )
    }

    private companion object {
        val KIND = DeviceRootKind.COOLING
        const val DEFAULT_TITLE = "Cooling"
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "Cooling",
    val deviceUid: String = "",
    val connectionStatus: String = "Offline",
    val ipText: String = "Unknown",
    val firmwareText: String = "Unknown",
    val modelText: String = "Unknown",
    val primaryCountLabel: String = "Channels",
    val primaryCountText: String = "Unknown",
    val featuresText: String = "Unknown",
    val primarySectionTitle: String = "Controls",
    val primarySectionPlaceholder: String = "Controls hazırlanıyor.",
    val secondarySectionTitle: String = "Schedules",
    val secondarySectionPlaceholder: String = "Schedules hazırlanıyor."
)
