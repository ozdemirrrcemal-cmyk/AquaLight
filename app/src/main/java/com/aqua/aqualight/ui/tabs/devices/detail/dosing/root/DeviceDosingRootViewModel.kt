package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DeviceDosingRootViewModel(
    private val operations: DeviceRootOperations,
    private val channelNavigationOperations: DeviceDosingChannelNavigationOperations,
    private val channelOperations: DeviceDosingChannelOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceDosingRootUiState())
    val uiState: StateFlow<DeviceDosingRootUiState> = _uiState.asStateFlow()
    private val navigationEventChannel = Channel<DeviceDosingChannelNavigationTarget>(
        capacity = Channel.BUFFERED
    )
    val navigationEvents: Flow<DeviceDosingChannelNavigationTarget> =
        navigationEventChannel.receiveAsFlow()
    private val navigationFailureEventChannel = Channel<Unit>(capacity = Channel.BUFFERED)
    val navigationFailureEvents: Flow<Unit> = navigationFailureEventChannel.receiveAsFlow()

    private var boundDeviceUid: String = ""
    private var fallbackTitle: String = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var channelSnapshots: List<DeviceDosingChannelSnapshot> = emptyList()
    private var observeJob: Job? = null
    private var channelDataJob: Job? = null
    private var channelDataRefreshJob: Job? = null
    private var channelNavigationJob: Job? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            channelDataJob?.cancel()
            channelDataRefreshJob?.cancel()
            channelNavigationJob?.cancel()
            boundDeviceUid = ""
            latestRootSnapshot = null
            channelSnapshots = emptyList()
            _uiState.value = emptyState(fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) {
            refreshAuthoritative()
            return
        }

        boundDeviceUid = deviceUid
        this.fallbackTitle = fallbackTitle
        latestRootSnapshot = operations.current(deviceUid)
        channelSnapshots = emptyList()
        observeJob?.cancel()
        channelDataJob?.cancel()
        channelDataRefreshJob?.cancel()
        channelNavigationJob?.cancel()
        renderBoundState()
        operations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        channelDataJob = viewModelScope.launch {
            channelOperations.observeAll(deviceUid).collect { snapshots ->
                if (boundDeviceUid != deviceUid) return@collect
                channelSnapshots = snapshots
                renderBoundState()
            }
        }
        refreshAuthoritative()
    }

    fun refreshAuthoritative() {
        val deviceUid = boundDeviceUid
        if (deviceUid.isBlank() || channelDataRefreshJob?.isActive == true) return
        channelDataRefreshJob = viewModelScope.launch {
            channelOperations.refreshAll(deviceUid)
        }
    }

    fun openChannel(slotId: String) {
        val requestedDeviceUid = boundDeviceUid
        val requestedSlotId = slotId.trim()
        if (requestedDeviceUid.isBlank() || requestedSlotId.isBlank()) return

        channelNavigationJob?.cancel()
        channelNavigationJob = viewModelScope.launch {
            val target = channelNavigationOperations.resolveCurrent(
                deviceUid = requestedDeviceUid,
                slotId = requestedSlotId
            )
            if (boundDeviceUid != requestedDeviceUid) return@launch

            if (target == null) {
                navigationFailureEventChannel.send(Unit)
            } else {
                navigationEventChannel.send(target)
            }
        }
    }

    private fun renderBoundState() {
        val deviceUid = boundDeviceUid
        _uiState.value = latestRootSnapshot?.toRootUiState(
            fallbackTitle = fallbackTitle,
            snapshots = channelSnapshots
        ) ?: emptyState(fallbackTitle, deviceUid)
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

    private fun DeviceRootSnapshot.toRootUiState(
        fallbackTitle: String,
        snapshots: List<DeviceDosingChannelSnapshot>
    ): DeviceDosingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val catalogChannels = when (catalogState) {
            DeviceRootCatalogState.PENDING,
            DeviceRootCatalogState.VALID -> channelSlots.dosingChannels
            DeviceRootCatalogState.UNSUPPORTED,
            DeviceRootCatalogState.INVALID -> emptyList()
        }
        val channelPresentation = resolveDosingRootChannelPresentation(
            deviceUid = deviceUid,
            catalogChannels = catalogChannels,
            snapshots = snapshots
        )
        return DeviceDosingRootUiState(
            title = title.ifBlank { fallbackTitle },
            deviceUid = deviceUid,
            connectionStatusRes = DeviceRootPresentationMapper.availabilityLabelRes(this),
            ipText = ipAddress,
            firmwareText = firmwareLabel,
            modelText = modelLabel,
            pumpCount = channelPresentation.pumpCount,
            channels = channelPresentation.channels,
            pumpStates = channelPresentation.pumpStates,
            primaryCountLabelRes = KIND.primaryCountLabelRes,
            primaryCountText = channelPresentation.pumpCount
                .takeIf { it > 0 }
                ?.toString()
                .orEmpty(),
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
    val pumpStates: List<DosingPumpVisualState> = emptyList(),
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
internal const val UNKNOWN_DOSING_PUMP_COUNT = 0
