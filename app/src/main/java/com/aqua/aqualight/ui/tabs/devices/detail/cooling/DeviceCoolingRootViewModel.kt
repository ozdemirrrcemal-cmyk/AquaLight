package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.text.AppTextResolver
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val operations: DeviceRootOperations,
    private val textResolver: AppTextResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(emptyState("", ""))
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(deviceUidText: String, fallbackTitle: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        operations.connect(deviceUid)
        _uiState.value = emptyState(fallbackTitle, deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle, deviceUid)
            }
        }
    }

    private fun emptyState(title: String, deviceUid: String) = DeviceCoolingRootUiState(
        title = title.ifBlank { textResolver.get(KIND.defaultTitleRes) },
        deviceUid = deviceUid,
        connectionStatus = textResolver.get(R.string.device_runtime_offline),
        ipText = textResolver.get(R.string.device_runtime_unknown),
        firmwareText = textResolver.get(R.string.device_runtime_unknown),
        modelText = textResolver.get(R.string.device_runtime_unknown),
        primaryCountLabel = textResolver.get(KIND.primaryCountLabelRes),
        primaryCountText = textResolver.get(R.string.device_runtime_unknown),
        featuresText = textResolver.get(R.string.device_runtime_unknown),
        primarySectionTitle = textResolver.get(KIND.primarySectionTitleRes),
        primarySectionPlaceholder = textResolver.get(KIND.primarySectionPlaceholderRes),
        secondarySectionTitle = textResolver.get(KIND.secondarySectionTitleRes),
        secondarySectionPlaceholder = textResolver.get(KIND.secondarySectionPlaceholderRes)
    )

    private fun DeviceRootSnapshot.toRootUiState(fallbackTitle: String): DeviceCoolingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        return DeviceCoolingRootUiState(
            title = title.ifBlank { fallbackTitle }.ifBlank { textResolver.get(KIND.defaultTitleRes) },
            deviceUid = deviceUid,
            connectionStatus = DeviceRootPresentationMapper.availabilityLabel(this, textResolver),
            ipText = ipAddress.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            firmwareText = firmwareLabel.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            modelText = modelLabel.ifBlank { textResolver.get(R.string.device_runtime_unknown) },
            primaryCountLabel = textResolver.get(KIND.primaryCountLabelRes),
            primaryCountText = fanOutputCount.takeIf { it > 0 }?.toString()
                ?: textResolver.get(R.string.device_runtime_unknown),
            featuresText = DeviceRootPresentationMapper.overviewFeatureLabel(this, KIND, textResolver),
            primarySectionTitle = textResolver.get(KIND.primarySectionTitleRes),
            primarySectionPlaceholder = menuSections.primaryText(
                textResolver,
                KIND.primarySectionPlaceholderRes
            ),
            secondarySectionTitle = textResolver.get(KIND.secondarySectionTitleRes),
            secondarySectionPlaceholder = menuSections.secondaryText(
                textResolver,
                KIND.secondarySectionPlaceholderRes
            )
        )
    }

    private companion object {
        val KIND = DeviceRootKind.COOLING
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    val connectionStatus: String = "",
    val ipText: String = "",
    val firmwareText: String = "",
    val modelText: String = "",
    val primaryCountLabel: String = "",
    val primaryCountText: String = "",
    val featuresText: String = "",
    val primarySectionTitle: String = "",
    val primarySectionPlaceholder: String = "",
    val secondarySectionTitle: String = "",
    val secondarySectionPlaceholder: String = ""
)
