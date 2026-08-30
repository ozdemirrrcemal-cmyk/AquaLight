package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.text.AquaUiText
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

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState("")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = operations.current(deviceUid)?.toRootUiState()
            ?: emptyState(deviceUid)
        operations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toRootUiState()
                    ?: emptyState(deviceUid)
            }
        }
    }

    private fun emptyState(deviceUid: String) = DeviceCoolingRootUiState(
        deviceUid = deviceUid,
        connectionStatusRes = R.string.device_offline,
        connectionVisualState = DeviceConnectionVisualState.OFFLINE,
        contentEnabled = false,
        primaryCountLabelRes = KIND.primaryCountLabelRes,
        primarySectionTitleRes = KIND.primarySectionTitleRes,
        primarySectionPlaceholder = AquaUiText.Resource(KIND.primarySectionPlaceholderRes),
        secondarySectionTitleRes = KIND.secondarySectionTitleRes,
        secondarySectionPlaceholder = AquaUiText.Resource(KIND.secondarySectionPlaceholderRes)
    )

    private fun DeviceRootSnapshot.toRootUiState(): DeviceCoolingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val contentEnabled =
            availability == OwnerDeviceAvailability.REACHABLE &&
                catalogState == DeviceRootCatalogState.VALID &&
                family == OwnerDeviceFamily.COOLING
        val connectionVisualState = if (contentEnabled) {
            DeviceConnectionVisualState.ONLINE
        } else {
            DeviceConnectionVisualState.OFFLINE
        }
        return DeviceCoolingRootUiState(
            title = title,
            deviceUid = deviceUid,
            connectionStatusRes = connectionVisualState.statusLabelRes,
            connectionVisualState = connectionVisualState,
            contentEnabled = contentEnabled,
            ipText = ipAddress,
            firmwareText = firmwareLabel,
            modelText = modelLabel,
            primaryCountLabelRes = KIND.primaryCountLabelRes,
            primaryCountText = fanOutputCount.takeIf { it > 0 }?.toString().orEmpty(),
            featuresText = DeviceRootPresentationMapper.overviewFeatureText(this, KIND),
            primarySectionTitleRes = KIND.primarySectionTitleRes,
            primarySectionPlaceholder = menuSections.primaryText(KIND.primarySectionPlaceholderRes),
            secondarySectionTitleRes = KIND.secondarySectionTitleRes,
            secondarySectionPlaceholder = menuSections.secondaryText(KIND.secondarySectionPlaceholderRes)
        )
    }

    private companion object {
        val KIND = DeviceRootKind.COOLING
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    @StringRes val connectionStatusRes: Int = R.string.device_offline,
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val ipText: String = "",
    val firmwareText: String = "",
    val modelText: String = "",
    @StringRes val primaryCountLabelRes: Int = R.string.device_fan_outputs_label,
    val primaryCountText: String = "",
    val featuresText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    @StringRes val primarySectionTitleRes: Int = R.string.device_menu_fan_control_title,
    val primarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_fan_control_preparing
    ),
    @StringRes val secondarySectionTitleRes: Int =
        R.string.device_menu_temperature_automation_title,
    val secondarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_temperature_automation_preparing
    )
)
