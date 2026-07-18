package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceRootOverviewViewModel(
    private val operations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceRootOverviewUiState())
    val uiState: StateFlow<DeviceRootOverviewUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(
        kind: DeviceRootKind,
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            boundDeviceUid = ""
            _uiState.value = emptyState(kind, fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        observeJob?.cancel()
        _uiState.value = emptyState(kind, fallbackTitle, deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toOverviewState(kind, fallbackTitle)
                    ?: emptyState(kind, fallbackTitle, deviceUid)
            }
        }
    }

    private fun DeviceRootSnapshot.toOverviewState(
        kind: DeviceRootKind,
        fallbackTitle: String
    ): DeviceRootOverviewUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = kind, snapshot = this)
        val count = DeviceRootPresentationMapper.primaryCount(this, kind)
        return DeviceRootOverviewUiState(
            title = titleText(title, fallbackTitle, defaultTitleRes = kind.defaultTitleRes),
            deviceUid = deviceUid,
            connectionStatus = AquaUiText.Resource(
                DeviceRootPresentationMapper.availabilityLabelRes(this)
            ),
            ipText = dynamicOrUnknown(ipAddress),
            firmwareText = dynamicOrUnknown(firmwareLabel),
            modelText = dynamicOrUnknown(modelLabel),
            primaryCountLabelRes = kind.primaryCountLabelRes,
            primaryCountText = count.takeIf { it > 0 }
                ?.let { AquaUiText.Dynamic(it.toString()) }
                ?: AquaUiText.Resource(R.string.device_unknown),
            featuresText = DeviceRootPresentationMapper.overviewFeatureText(this, kind),
            primarySectionTitleRes = kind.primarySectionTitleRes,
            primarySectionPlaceholder = menuSections.primaryText(kind.primarySectionPlaceholderRes),
            secondarySectionTitleRes = kind.secondarySectionTitleRes,
            secondarySectionPlaceholder = menuSections.secondaryText(kind.secondarySectionPlaceholderRes)
        )
    }

    private fun emptyState(
        kind: DeviceRootKind,
        fallbackTitle: String,
        deviceUid: String
    ) = DeviceRootOverviewUiState(
        title = titleText(fallbackTitle, defaultTitleRes = kind.defaultTitleRes),
        deviceUid = deviceUid,
        connectionStatus = AquaUiText.Resource(R.string.device_offline),
        primaryCountLabelRes = kind.primaryCountLabelRes,
        primarySectionTitleRes = kind.primarySectionTitleRes,
        primarySectionPlaceholder = AquaUiText.Resource(kind.primarySectionPlaceholderRes),
        secondarySectionTitleRes = kind.secondarySectionTitleRes,
        secondarySectionPlaceholder = AquaUiText.Resource(kind.secondarySectionPlaceholderRes)
    )

    private fun dynamicOrUnknown(value: String): AquaUiText =
        value.takeIf(String::isNotBlank)
            ?.let(AquaUiText::Dynamic)
            ?: AquaUiText.Resource(R.string.device_unknown)

    private fun titleText(
        vararg candidates: String,
        @StringRes defaultTitleRes: Int
    ): AquaUiText = candidates.firstOrNull(String::isNotBlank)
        ?.let(AquaUiText::Dynamic)
        ?: AquaUiText.Resource(defaultTitleRes)
}

enum class DeviceRootKind(
    @StringRes val defaultTitleRes: Int,
    @StringRes val primaryCountLabelRes: Int,
    @StringRes val primarySectionTitleRes: Int,
    @StringRes val primarySectionPlaceholderRes: Int,
    @StringRes val secondarySectionTitleRes: Int,
    @StringRes val secondarySectionPlaceholderRes: Int
) {
    DOSING(
        defaultTitleRes = R.string.device_family_dosing,
        primaryCountLabelRes = R.string.device_dosing_channels_label,
        primarySectionTitleRes = R.string.device_menu_channels_title,
        primarySectionPlaceholderRes = R.string.device_menu_dosing_controls_preparing,
        secondarySectionTitleRes = R.string.device_menu_schedules_title,
        secondarySectionPlaceholderRes = R.string.device_menu_dosing_schedules_preparing
    ),
    TIMER(
        defaultTitleRes = R.string.device_family_timer,
        primaryCountLabelRes = R.string.device_menu_timer_channels_title,
        primarySectionTitleRes = R.string.device_menu_timer_channels_title,
        primarySectionPlaceholderRes = R.string.device_menu_timer_controls_preparing,
        secondarySectionTitleRes = R.string.device_menu_schedules_title,
        secondarySectionPlaceholderRes = R.string.device_menu_timer_schedules_preparing
    ),
    COOLING(
        defaultTitleRes = R.string.device_family_cooling,
        primaryCountLabelRes = R.string.device_fan_outputs_label,
        primarySectionTitleRes = R.string.device_menu_fan_control_title,
        primarySectionPlaceholderRes = R.string.device_menu_fan_control_preparing,
        secondarySectionTitleRes = R.string.device_menu_temperature_automation_title,
        secondarySectionPlaceholderRes = R.string.device_menu_temperature_automation_preparing
    )
}

data class DeviceRootOverviewUiState(
    val title: AquaUiText = AquaUiText.Resource(R.string.device_unknown_device),
    val deviceUid: String = "",
    val connectionStatus: AquaUiText = AquaUiText.Resource(R.string.device_offline),
    val ipText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    val firmwareText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    val modelText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    @StringRes val primaryCountLabelRes: Int = R.string.device_menu_channels_title,
    val primaryCountText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    val featuresText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    @StringRes val primarySectionTitleRes: Int = R.string.device_menu_controls_title,
    val primarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_controls_preparing
    ),
    @StringRes val secondarySectionTitleRes: Int = R.string.device_menu_schedules_title,
    val secondarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_programs_preparing
    )
)
