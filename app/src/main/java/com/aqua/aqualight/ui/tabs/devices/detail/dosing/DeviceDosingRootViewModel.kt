package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceDosingDiagnosticSnapshot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
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
    private val channelNavigationOperations: DeviceDosingChannelNavigationOperations
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
    private var channelTargets: Map<String, DeviceDosingChannelNavigationTarget> = emptyMap()
    private var observeJob: Job? = null
    private var channelStateJob: Job? = null
    private var diagnosticJob: Job? = null
    private var channelRefreshJob: Job? = null
    private var channelNavigationJob: Job? = null

    fun bind(
        deviceUidText: String,
        fallbackTitle: String
    ) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            observeJob?.cancel()
            channelStateJob?.cancel()
            diagnosticJob?.cancel()
            channelRefreshJob?.cancel()
            channelNavigationJob?.cancel()
            boundDeviceUid = ""
            latestRootSnapshot = null
            channelTargets = emptyMap()
            _uiState.value = emptyState(fallbackTitle, "")
            return
        }
        if (boundDeviceUid == deviceUid) return

        boundDeviceUid = deviceUid
        this.fallbackTitle = fallbackTitle
        latestRootSnapshot = operations.current(deviceUid)
        channelTargets = emptyMap()
        _uiState.value = _uiState.value.copy(diagnostics = null)
        observeJob?.cancel()
        channelStateJob?.cancel()
        diagnosticJob?.cancel()
        channelRefreshJob?.cancel()
        renderBoundState()
        operations.connect(deviceUid)
        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                latestRootSnapshot = snapshot
                renderBoundState()
            }
        }
        channelStateJob = viewModelScope.launch {
            channelNavigationOperations.observeTargets(deviceUid).collect { targets ->
                if (boundDeviceUid != deviceUid) return@collect
                channelTargets = targets.associateBy(DeviceDosingChannelNavigationTarget::slotId)
                renderBoundState()
            }
        }
        diagnosticJob = viewModelScope.launch {
            channelNavigationOperations.observeDiagnostics(deviceUid).collect { diagnostics ->
                if (boundDeviceUid != deviceUid) return@collect
                _uiState.value = _uiState.value.copy(diagnostics = diagnostics)
            }
        }
        channelRefreshJob = viewModelScope.launch {
            channelNavigationOperations.refreshTargets(deviceUid)
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
        val diagnostics = _uiState.value.diagnostics
        val rendered = latestRootSnapshot?.toRootUiState(
            fallbackTitle = fallbackTitle,
            targets = channelTargets
        ) ?: emptyState(fallbackTitle, deviceUid)
        _uiState.value = rendered.copy(diagnostics = diagnostics)
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
        targets: Map<String, DeviceDosingChannelNavigationTarget>
    ): DeviceDosingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val catalogChannels = if (catalogState == DeviceRootCatalogState.VALID) {
            channelSlots.dosingChannels
        } else {
            emptyList()
        }
        val exactChannelCount = catalogChannels.size
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
                    .withNavigationTarget(targets[slot.id.value])
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
    val diagnostics: DeviceDosingDiagnosticSnapshot? = null,
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
