package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceCoolingModeOption
import com.aqua.aqualight.application.devices.DeviceCoolingOperationResult
import com.aqua.aqualight.application.devices.DeviceCoolingOperations
import com.aqua.aqualight.application.devices.DeviceCoolingSnapshot
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DeviceCoolingRootViewModel(
    private val rootOperations: DeviceRootOperations,
    private val coolingOperations: DeviceCoolingOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceCoolingRootUiState())
    val uiState: StateFlow<DeviceCoolingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null
    private var refreshJob: Job? = null

    fun bind(deviceUidText: String, fallbackTitle: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding(fallbackTitle)
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        refreshJob?.cancel()
        _uiState.value = buildState(
            root = rootOperations.current(deviceUid),
            cooling = coolingOperations.current(deviceUid),
            fallbackTitle = fallbackTitle,
            deviceUid = deviceUid
        )
        val connected = rootOperations.connect(deviceUid).isSuccess
        observeJob = viewModelScope.launch {
            combine(
                rootOperations.observe(deviceUid),
                coolingOperations.observe(deviceUid)
            ) { root, cooling ->
                buildState(root, cooling, fallbackTitle, deviceUid)
            }.collect(_uiState::value::set)
        }
        if (connected) {
            refreshJob = viewModelScope.launch {
                applyRefreshResult(coolingOperations.refresh(deviceUid))
            }
        }
    }

    private fun clearBinding(fallbackTitle: String) {
        observeJob?.cancel()
        refreshJob?.cancel()
        boundDeviceUid = ""
        _uiState.value = emptyState(fallbackTitle, "")
    }

    private fun applyRefreshResult(result: DeviceCoolingOperationResult) {
        if (result is DeviceCoolingOperationResult.Failed) {
            _uiState.value = _uiState.value.copy(runtimeError = result.reason)
        }
    }

    private fun buildState(
        root: DeviceRootSnapshot?,
        cooling: DeviceCoolingSnapshot?,
        fallbackTitle: String,
        deviceUid: String
    ): DeviceCoolingRootUiState {
        if (root == null) return emptyState(fallbackTitle, deviceUid).withCooling(cooling)
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = root)
        return DeviceCoolingRootUiState(
            title = root.title.ifBlank { fallbackTitle },
            deviceUid = root.deviceUid,
            connectionStatusRes = DeviceRootPresentationMapper.availabilityLabelRes(root),
            ipText = root.ipAddress,
            firmwareText = root.firmwareLabel,
            modelText = root.modelLabel,
            primaryCountLabelRes = KIND.primaryCountLabelRes,
            primaryCountText = root.fanOutputCount.takeIf { it > 0 }?.toString().orEmpty(),
            featuresText = DeviceRootPresentationMapper.overviewFeatureText(root, KIND),
            primarySectionTitleRes = KIND.primarySectionTitleRes,
            primarySectionPlaceholder = menuSections.primaryText(KIND.primarySectionPlaceholderRes),
            secondarySectionTitleRes = KIND.secondarySectionTitleRes,
            secondarySectionPlaceholder = menuSections.secondaryText(
                KIND.secondarySectionPlaceholderRes
            )
        ).withCooling(cooling)
    }

    private fun emptyState(title: String, deviceUid: String) = DeviceCoolingRootUiState(
        title = title,
        deviceUid = deviceUid,
        connectionStatusRes = R.string.device_offline,
        primaryCountLabelRes = KIND.primaryCountLabelRes,
        primarySectionTitleRes = KIND.primarySectionTitleRes,
        primarySectionPlaceholder = AquaUiText.Resource(KIND.primarySectionPlaceholderRes),
        secondarySectionTitleRes = KIND.secondarySectionTitleRes,
        secondarySectionPlaceholder = AquaUiText.Resource(KIND.secondarySectionPlaceholderRes)
    )

    private fun DeviceCoolingRootUiState.withCooling(
        cooling: DeviceCoolingSnapshot?
    ): DeviceCoolingRootUiState = copy(
        coolingMode = cooling?.mode,
        minTemperatureC = cooling?.minTemperatureC,
        maxTemperatureC = cooling?.maxTemperatureC,
        temperatureSupported = cooling?.temperatureSupported == true,
        temperatureReadingValid = cooling?.readingValid == true,
        temperatureC = cooling?.temperatureC,
        temperatureSampledAtMs = cooling?.sampledAtMs ?: 0L,
        runtimeError = ""
    )

    private companion object {
        val KIND = DeviceRootKind.COOLING
    }
}

data class DeviceCoolingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    @StringRes val connectionStatusRes: Int = R.string.device_offline,
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
    ),
    val coolingMode: DeviceCoolingModeOption? = null,
    val minTemperatureC: Double? = null,
    val maxTemperatureC: Double? = null,
    val temperatureSupported: Boolean = false,
    val temperatureReadingValid: Boolean = false,
    val temperatureC: Double? = null,
    val temperatureSampledAtMs: Long = 0L,
    val runtimeError: String = ""
)
