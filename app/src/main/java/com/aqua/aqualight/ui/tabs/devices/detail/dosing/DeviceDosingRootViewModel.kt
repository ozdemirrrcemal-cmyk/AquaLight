package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
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
import kotlinx.coroutines.launch

class DeviceDosingRootViewModel(
    private val operations: DeviceRootOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDosingRootUiState())
    val uiState: StateFlow<DeviceDosingRootUiState> = _uiState.asStateFlow()

    private var boundDeviceUid: String = ""
    private var observeJob: Job? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
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
        _uiState.value = operations.current(deviceUid)?.toRootUiState(fallbackTitle)
            ?: emptyState(fallbackTitle, deviceUid)
        operations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                _uiState.value = snapshot?.toRootUiState(fallbackTitle)
                    ?: emptyState(fallbackTitle, deviceUid)
            }
        }
    }

    private fun emptyState(title: String, deviceUid: String) = DeviceDosingRootUiState(
        title = title,
        deviceUid = deviceUid,
        connectionStatusRes = R.string.device_offline,
        pumpCount = UNKNOWN_DOSING_PUMP_COUNT,
        primaryCountLabelRes = KIND.primaryCountLabelRes,
        primarySectionTitleRes = KIND.primarySectionTitleRes,
        primarySectionPlaceholder = AquaUiText.Resource(KIND.primarySectionPlaceholderRes),
        secondarySectionTitleRes = KIND.secondarySectionTitleRes,
        secondarySectionPlaceholder = AquaUiText.Resource(KIND.secondarySectionPlaceholderRes)
    )

    private fun DeviceRootSnapshot.toRootUiState(fallbackTitle: String): DeviceDosingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val catalogChannels = if (catalogState == DeviceRootCatalogState.VALID) {
            channelSlots.dosingChannels
        } else {
            emptyList()
        }
        val exactChannelCount = when {
            catalogChannels.isNotEmpty() -> catalogChannels.size
            catalogState == DeviceRootCatalogState.VALID -> dosingChannelCount
            else -> UNKNOWN_DOSING_PUMP_COUNT
        }
        return DeviceDosingRootUiState(
            title = title.ifBlank { fallbackTitle },
            deviceUid = deviceUid,
            connectionStatusRes = DeviceRootPresentationMapper.availabilityLabelRes(this),
            ipText = ipAddress,
            firmwareText = firmwareLabel,
            modelText = modelLabel,
            pumpCount = resolveDosingPumpCount(exactChannelCount),
            channels = catalogChannels.map { slot ->
                slot.toInitialDosingChannelCardUiState()
            },
            primaryCountLabelRes = KIND.primaryCountLabelRes,
            primaryCountText = exactChannelCount.takeIf { it > 0 }?.toString().orEmpty(),
            featuresText = DeviceRootPresentationMapper.overviewFeatureText(this, KIND),
            primarySectionTitleRes = KIND.primarySectionTitleRes,
            primarySectionPlaceholder = menuSections.primaryText(KIND.primarySectionPlaceholderRes),
            secondarySectionTitleRes = KIND.secondarySectionTitleRes,
            secondarySectionPlaceholder = menuSections.secondaryText(KIND.secondarySectionPlaceholderRes)
        )
    }

    private companion object {
        val KIND = DeviceRootKind.DOSING
    }
}

data class DeviceDosingRootUiState(
    val title: String = "",
    val deviceUid: String = "",
    @StringRes val connectionStatusRes: Int = R.string.device_offline,
    val ipText: String = "",
    val firmwareText: String = "",
    val modelText: String = "",
    val pumpCount: Int = UNKNOWN_DOSING_PUMP_COUNT,
    val channels: List<DosingChannelCardUiState> = emptyList(),
    @StringRes val primaryCountLabelRes: Int = R.string.device_dosing_channels_label,
    val primaryCountText: String = "",
    val featuresText: AquaUiText = AquaUiText.Resource(R.string.device_unknown),
    @StringRes val primarySectionTitleRes: Int = R.string.device_menu_channels_title,
    val primarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_dosing_controls_preparing
    ),
    @StringRes val secondarySectionTitleRes: Int = R.string.device_menu_schedules_title,
    val secondarySectionPlaceholder: AquaUiText = AquaUiText.Resource(
        R.string.device_menu_dosing_schedules_preparing
    )
)

internal fun resolveDosingPumpCount(channelCount: Int): Int = when (channelCount) {
    DOSING_PRO_2_CHANNEL_COUNT -> DOSING_PRO_2_CHANNEL_COUNT
    DOSING_PRO_4_CHANNEL_COUNT -> DOSING_PRO_4_CHANNEL_COUNT
    else -> UNKNOWN_DOSING_PUMP_COUNT
}

private const val DOSING_PRO_2_CHANNEL_COUNT = 2
private const val DOSING_PRO_4_CHANNEL_COUNT = 4
private const val UNKNOWN_DOSING_PUMP_COUNT = 0
