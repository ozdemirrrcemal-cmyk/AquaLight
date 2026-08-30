package com.aqua.aqualight.ui.tabs.devices.detail.dosing.root

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootMenuMapper
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootPresentationMapper
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.card.DosingChannelCardUiState
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.presentation.pump.DosingPumpVisualState
import kotlinx.coroutines.CoroutineStart
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
    private val channelOperations: DeviceDosingChannelOperations,
    private val controlSurfacePreparationOperations: DeviceControlSurfacePreparationOperations
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
    private val surfaceUnavailableEventChannel = Channel<DeviceMenuUnavailableReason>(
        capacity = Channel.BUFFERED
    )
    val surfaceUnavailableEvents: Flow<DeviceMenuUnavailableReason> =
        surfaceUnavailableEventChannel.receiveAsFlow()

    private var boundDeviceUid: String = ""
    private var latestRootSnapshot: DeviceRootSnapshot? = null
    private var validatedCatalogChannels: List<DeviceDosingChannelSlot> = emptyList()
    private var channelSnapshots: List<DeviceDosingChannelSnapshot> = emptyList()
    private var lastAuthoritativePresentation: DeviceDosingRootChannelPresentation? = null
    private var surfacePreparationPending: Boolean = false
    private var observeJob: Job? = null
    private var channelDataJob: Job? = null
    private var channelNavigationJob: Job? = null
    private var surfacePreparationJob: Job? = null

    fun bind(deviceUidText: String) {
        val deviceUid = deviceUidText.trim()
        if (deviceUid.isBlank()) {
            clearBinding()
            return
        }
        if (boundDeviceUid == deviceUid) {
            renderBoundState()
            return
        }

        val preparedHandoff = controlSurfacePreparationOperations.consumeFreshPreparation(
            deviceUid = deviceUid,
            family = OwnerDeviceFamily.DOSING
        )

        boundDeviceUid = deviceUid
        validatedCatalogChannels = emptyList()
        channelSnapshots = emptyList()
        lastAuthoritativePresentation = null
        surfacePreparationPending = false
        acceptRootSnapshot(operations.current(deviceUid))
        observeJob?.cancel()
        channelDataJob?.cancel()
        channelNavigationJob?.cancel()
        surfacePreparationJob?.cancel()

        // Reconnect/reuse is resolved by the central repository. The preparation marker is never
        // accepted as proof by itself: after this call we re-read the authoritative channel view.
        operations.connect(deviceUid)
        val authoritativeAtBind = currentAuthoritativeSurface(deviceUid)
        val preparedSurfaceStillCurrent = preparedHandoff && authoritativeAtBind.isNotEmpty()
        channelSnapshots = authoritativeAtBind
        surfacePreparationPending = !preparedSurfaceStillCurrent
        renderBoundState()

        observeJob = viewModelScope.launch {
            operations.observe(deviceUid).collect { snapshot ->
                if (boundDeviceUid != deviceUid) return@collect
                acceptRootSnapshot(snapshot)
                renderBoundState()
            }
        }
        channelDataJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            channelOperations.observeAll(deviceUid).collect { snapshots ->
                if (boundDeviceUid != deviceUid) return@collect
                channelSnapshots = snapshots
                renderBoundState()
            }
        }
        if (!preparedSurfaceStillCurrent) {
            prepareRestoredSurface(deviceUid)
        }
    }

    fun openChannel(slotId: String) {
        val requestedDeviceUid = boundDeviceUid
        val requestedSlotId = slotId.trim()
        if (
            requestedDeviceUid.isBlank() ||
            requestedSlotId.isBlank() ||
            !_uiState.value.contentEnabled
        ) {
            return
        }

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

    private fun clearBinding() {
        observeJob?.cancel()
        channelDataJob?.cancel()
        channelNavigationJob?.cancel()
        surfacePreparationJob?.cancel()
        boundDeviceUid = ""
        latestRootSnapshot = null
        validatedCatalogChannels = emptyList()
        channelSnapshots = emptyList()
        lastAuthoritativePresentation = null
        surfacePreparationPending = false
        _uiState.value = emptyState("")
    }

    private fun prepareRestoredSurface(deviceUid: String) {
        if (deviceUid.isBlank() || surfacePreparationJob?.isActive == true) return
        surfacePreparationPending = true
        renderBoundState()
        surfacePreparationJob = viewModelScope.launch {
            val result = runCatching {
                controlSurfacePreparationOperations.prepare(
                    DeviceControlSurfacePreparationRequest(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.DOSING
                    )
                )
            }.getOrElse {
                DeviceControlSurfacePreparationResult.Unavailable(
                    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            }
            if (boundDeviceUid != deviceUid) return@launch

            when (result) {
                DeviceControlSurfacePreparationResult.Ready -> {
                    controlSurfacePreparationOperations.consumeFreshPreparation(
                        deviceUid = deviceUid,
                        family = OwnerDeviceFamily.DOSING
                    )
                    acceptRootSnapshot(operations.current(deviceUid))
                    val preparedSnapshots = currentAuthoritativeSurface(deviceUid)
                    if (preparedSnapshots.isEmpty()) {
                        finishUnavailablePreparation(
                            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                        )
                    } else {
                        channelSnapshots = preparedSnapshots
                        surfacePreparationPending = false
                        renderBoundState()
                    }
                }
                is DeviceControlSurfacePreparationResult.Unavailable -> {
                    finishUnavailablePreparation(result.reason)
                }
            }
        }
    }

    private suspend fun finishUnavailablePreparation(reason: DeviceMenuUnavailableReason) {
        surfacePreparationPending = false
        renderBoundState()
        surfaceUnavailableEventChannel.send(reason)
    }

    private fun currentAuthoritativeSurface(
        deviceUid: String
    ): List<DeviceDosingChannelSnapshot> {
        if (validatedCatalogChannels.isEmpty()) return emptyList()
        val current = validatedCatalogChannels.mapNotNull { slot ->
            channelOperations.current(deviceUid, slot.id.value)
        }
        val presentation = resolveDosingRootChannelPresentation(
            deviceUid = deviceUid,
            catalogChannels = validatedCatalogChannels,
            snapshots = current
        )
        return current.takeIf { presentation.authoritative } ?: emptyList()
    }

    private fun renderBoundState() {
        val deviceUid = boundDeviceUid
        val snapshot = latestRootSnapshot
        _uiState.value = if (snapshot == null) {
            val current = _uiState.value
            if (current.pumpCount > 0) {
                current.copy(
                    connectionVisualState = DeviceConnectionVisualState.OFFLINE,
                    connectionStatusRes = R.string.device_offline,
                    contentEnabled = false,
                    showBlockingPreparation = false
                )
            } else {
                emptyState(deviceUid)
            }
        } else {
            snapshot.toRootUiState(
                catalogChannels = validatedCatalogChannels,
                snapshots = channelSnapshots
            )
        }
    }

    /**
     * Runtime metadata is write authority, not a command to erase an already validated topology.
     * Keep the last validated channel catalog across a transient reconnect while the root snapshot
     * remains the sole source of dynamic device identity, including the user-defined title.
     */
    private fun acceptRootSnapshot(snapshot: DeviceRootSnapshot?) {
        latestRootSnapshot = snapshot
        if (snapshot?.catalogState == DeviceRootCatalogState.VALID) {
            validatedCatalogChannels = snapshot.channelSlots.dosingChannels
        }
    }

    private fun emptyState(deviceUid: String) = DeviceDosingRootUiState(
        deviceUid = deviceUid,
        connectionStatusRes = R.string.device_offline,
        connectionVisualState = DeviceConnectionVisualState.OFFLINE,
        contentEnabled = false,
        showBlockingPreparation = deviceUid.isNotBlank(),
        pumpCount = UNKNOWN_DOSING_PUMP_COUNT,
        primaryCountLabelRes = KIND.primaryCountLabelRes,
        primarySectionTitleRes = KIND.primarySectionTitleRes,
        primarySectionPlaceholder = AquaUiText.Resource(KIND.primarySectionPlaceholderRes),
        secondarySectionTitleRes = KIND.secondarySectionTitleRes,
        secondarySectionPlaceholder = AquaUiText.Resource(KIND.secondarySectionPlaceholderRes)
    )

    private fun DeviceRootSnapshot.toRootUiState(
        catalogChannels: List<DeviceDosingChannelSlot>,
        snapshots: List<DeviceDosingChannelSnapshot>
    ): DeviceDosingRootUiState {
        val menuSections = DeviceRootMenuMapper.overview(kind = KIND, snapshot = this)
        val channelPresentation = resolveDosingRootChannelPresentation(
            deviceUid = deviceUid,
            catalogChannels = catalogChannels,
            snapshots = snapshots
        )
        if (channelPresentation.authoritative) {
            lastAuthoritativePresentation = channelPresentation
        }
        val displayedPresentation = if (channelPresentation.authoritative) {
            channelPresentation
        } else {
            lastAuthoritativePresentation ?: channelPresentation
        }
        val contentEnabled =
            !surfacePreparationPending &&
                availability == OwnerDeviceAvailability.REACHABLE &&
                catalogState == DeviceRootCatalogState.VALID &&
                channelPresentation.authoritative
        val connectionVisualState = if (contentEnabled) {
            DeviceConnectionVisualState.ONLINE
        } else {
            DeviceConnectionVisualState.OFFLINE
        }
        return DeviceDosingRootUiState(
            title = title,
            deviceUid = deviceUid,
            connectionStatusRes = connectionVisualState.statusLabelRes,
            connectionVisualState = connectionVisualState,
            contentEnabled = contentEnabled,
            showBlockingPreparation =
                surfacePreparationPending && displayedPresentation.pumpCount == 0,
            ipText = ipAddress,
            firmwareText = firmwareLabel,
            modelText = modelLabel,
            pumpCount = displayedPresentation.pumpCount,
            channels = displayedPresentation.channels,
            pumpStates = displayedPresentation.pumpStates,
            primaryCountLabelRes = KIND.primaryCountLabelRes,
            primaryCountText = displayedPresentation.pumpCount
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
    val connectionVisualState: DeviceConnectionVisualState = DeviceConnectionVisualState.OFFLINE,
    val contentEnabled: Boolean = false,
    val showBlockingPreparation: Boolean = false,
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
