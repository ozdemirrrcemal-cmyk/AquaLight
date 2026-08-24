package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummary
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardSummaryPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    private val assignmentOperations: TankDeviceAssignmentOperations,
    private val menuOpenUseCase: DeviceMenuOpenUseCase,
    private val routeResolver: DeviceRouteResolver,
    private val dosingChannelOperations: DeviceDosingChannelOperations? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

    private val openingDeviceId = MutableStateFlow<String?>(null)
    private val removingDevice = MutableStateFlow(false)
    private val dosingSummaries = MutableStateFlow<Map<String, DeviceDosingCardSummary>>(emptyMap())
    private val spotlightIndices = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val dosingObserverJobs = mutableMapOf<String, Job>()
    private var boundTankId: Long = 0L
    private var observeJob: Job? = null
    private var menuOpenJob: Job? = null
    private var spotlightRotationJob: Job? = null
    private var pendingMenuOpen: DeviceMenuOpenResult.Ready? = null

    fun bind(tankId: Long) {
        if (tankId <= 0L || boundTankId == tankId) return

        boundTankId = tankId
        assignmentOperations.start(viewModelScope)
        observeJob?.cancel()

        val assignedDevices = assignmentOperations.assignedDevices(tankId)
            .onEach(::syncDosingObservers)
        val interactionState = observeInteractionState(
            openingDeviceId = openingDeviceId,
            removingDevice = removingDevice
        )
        val dosingPresentationState = observeDosingPresentationState(
            dosingSummaries = dosingSummaries,
            spotlightIndices = spotlightIndices
        )

        observeJob = viewModelScope.launch {
            combine(
                assignedDevices,
                interactionState,
                dosingPresentationState,
                ::buildTankDetailDevicesUiState
            ).catch {
                _uiState.update { current ->
                    current.copy(
                        devices = emptyList(),
                        isEmpty = true,
                        isLoading = false
                    )
                }
                _events.send(TankDetailDevicesEvent.ShowLoadFailed)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onDeviceClicked(deviceUid: String) {
        if (
            deviceUid.isBlank() ||
            removingDevice.value ||
            openingDeviceId.value != null
        ) {
            return
        }

        menuOpenJob?.cancel()
        openingDeviceId.value = deviceUid
        menuOpenJob = viewModelScope.launch {
            try {
                when (val result = menuOpenUseCase.resolve(deviceUid)) {
                    is DeviceMenuOpenResult.Ready -> {
                        pendingMenuOpen = result
                        _events.send(
                            TankDetailDevicesEvent.OpenDeviceRoute(
                                route = routeResolver.resolve(result.access)
                            )
                        )
                    }
                    is DeviceMenuOpenResult.Unavailable -> {
                        clearMenuOpen(deviceUid)
                        _events.send(result.toUnavailableEvent())
                    }
                }
            } catch (error: Throwable) {
                abandonPendingNavigation(deviceUid)
                clearMenuOpen(deviceUid)
                if (error is CancellationException) throw error
                _events.send(
                    TankDetailDevicesEvent.ShowDeviceUnavailable(
                        title = _uiState.value.devices
                            .firstOrNull { device -> device.deviceUid == deviceUid }
                            ?.title
                            .orEmpty(),
                        messageRes = DeviceMenuUnavailableMessageMapper.messageRes(
                            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                        )
                    )
                )
            }
        }
    }

    fun onDeviceNavigationFinished(
        deviceUid: String,
        committed: Boolean
    ) {
        val pending = pendingMenuOpen?.takeIf { ready ->
            ready.access.deviceUid == deviceUid
        }
        if (pending != null) {
            if (!committed) menuOpenUseCase.abandon(pending)
            pendingMenuOpen = null
        }
        clearMenuOpen(deviceUid)
    }

    fun onNavigationHostDestroyed() {
        menuOpenJob?.cancel()
        pendingMenuOpen?.let(menuOpenUseCase::abandon)
        pendingMenuOpen = null
        openingDeviceId.value = null
    }

    fun removeDeviceFromTank(deviceUid: String) {
        val tankId = boundTankId
        if (
            !isDeviceRemovalAllowed(
                tankId = tankId,
                deviceUid = deviceUid,
                isRemovingDevice = removingDevice.value,
                openingDeviceId = openingDeviceId.value
            )
        ) {
            return
        }

        viewModelScope.launch {
            removingDevice.value = true
            val result = try {
                assignmentOperations.removeDevice(tankId, deviceUid)
            } finally {
                removingDevice.value = false
            }

            when (result) {
                RemoveDeviceFromTankResult.REMOVED,
                RemoveDeviceFromTankResult.NOT_ASSIGNED -> Unit
                RemoveDeviceFromTankResult.INVALID_REQUEST,
                RemoveDeviceFromTankResult.FAILURE ->
                    _events.send(TankDetailDevicesEvent.ShowRemoveFailed)
            }
        }
    }

    private fun syncDosingObservers(devices: List<TankDeviceListItem>) {
        val operations = dosingChannelOperations ?: return
        val dosingDeviceIds = devices
            .asSequence()
            .filter { device -> device.family == OwnerDeviceFamily.DOSING }
            .map(TankDeviceListItem::deviceUid)
            .toSet()

        (dosingObserverJobs.keys - dosingDeviceIds).forEach { deviceUid ->
            dosingObserverJobs.remove(deviceUid)?.cancel()
            dosingSummaries.update { summaries -> summaries - deviceUid }
            spotlightIndices.update { indices -> indices - deviceUid }
        }
        syncSpotlightRotation()

        dosingDeviceIds
            .filterNot(dosingObserverJobs::containsKey)
            .forEach { deviceUid ->
                dosingObserverJobs[deviceUid] = viewModelScope.launch {
                    operations.observeAll(deviceUid)
                        .catch { emit(emptyList()) }
                        .collect { snapshots ->
                            updateDosingSummary(
                                deviceUid = deviceUid,
                                summary = DeviceDosingCardSummaryPolicy.build(
                                    deviceUid = deviceUid,
                                    snapshots = snapshots
                                )
                            )
                        }
                }
            }
    }

    private fun updateDosingSummary(
        deviceUid: String,
        summary: DeviceDosingCardSummary?
    ) {
        dosingSummaries.update { summaries ->
            if (summary == null) summaries - deviceUid else summaries + (deviceUid to summary)
        }
        spotlightIndices.update { indices ->
            when {
                summary == null || summary.channels.isEmpty() -> indices - deviceUid
                else -> indices + (
                    deviceUid to (indices[deviceUid] ?: 0).coerceIn(0, summary.channels.lastIndex)
                )
            }
        }
        syncSpotlightRotation()
    }

    private fun syncSpotlightRotation() {
        val shouldRotate = dosingSummaries.value.values.any { summary ->
            summary.channels.size > 1
        }
        if (!shouldRotate) {
            spotlightRotationJob?.cancel()
            spotlightRotationJob = null
            return
        }
        if (spotlightRotationJob?.isActive == true) return

        spotlightRotationJob = viewModelScope.launch {
            while (isActive) {
                delay(SPOTLIGHT_ROTATION_INTERVAL_MILLIS)
                val summaries = dosingSummaries.value
                spotlightIndices.update { current ->
                    buildMap {
                        current.forEach { (deviceUid, index) ->
                            put(deviceUid, index)
                        }
                        summaries.forEach { (deviceUid, summary) ->
                            val count = summary.channels.size
                            when {
                                count == 1 -> put(deviceUid, 0)
                                count > 1 -> put(
                                    deviceUid,
                                    ((current[deviceUid] ?: 0) + 1) % count
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun abandonPendingNavigation(deviceUid: String) {
        val pending = pendingMenuOpen?.takeIf { ready ->
            ready.access.deviceUid == deviceUid
        } ?: return
        menuOpenUseCase.abandon(pending)
        pendingMenuOpen = null
    }

    private fun clearMenuOpen(deviceUid: String) {
        if (openingDeviceId.value == deviceUid) {
            openingDeviceId.value = null
        }
    }

    private companion object {
        const val SPOTLIGHT_ROTATION_INTERVAL_MILLIS = 10_000L
    }
}

data class TankDetailDevicesUiState(
    val devices: List<TankAssignedDeviceItem> = emptyList(),
    val isEmpty: Boolean = true,
    val isLoading: Boolean = true,
    val openingDeviceId: String? = null,
    val isOpeningDeviceMenu: Boolean = false,
    val isRemovingDevice: Boolean = false
)

sealed interface TankDetailDevicesEvent {
    data class OpenDeviceRoute(val route: DeviceRoute) : TankDetailDevicesEvent

    data class ShowDeviceUnavailable(
        val title: String,
        @StringRes val messageRes: Int
    ) : TankDetailDevicesEvent

    data object ShowRemoveFailed : TankDetailDevicesEvent
    data object ShowLoadFailed : TankDetailDevicesEvent
}

private data class TankDeviceInteractionState(
    val openingDeviceId: String?,
    val isRemovingDevice: Boolean
)

private data class TankDosingPresentationState(
    val summaries: Map<String, DeviceDosingCardSummary>,
    val spotlightIndices: Map<String, Int>
)

private fun observeInteractionState(
    openingDeviceId: Flow<String?>,
    removingDevice: Flow<Boolean>
): Flow<TankDeviceInteractionState> = combine(
    openingDeviceId,
    removingDevice
) { currentOpeningDeviceId, isRemovingDevice ->
    TankDeviceInteractionState(
        openingDeviceId = currentOpeningDeviceId,
        isRemovingDevice = isRemovingDevice
    )
}

private fun observeDosingPresentationState(
    dosingSummaries: Flow<Map<String, DeviceDosingCardSummary>>,
    spotlightIndices: Flow<Map<String, Int>>
): Flow<TankDosingPresentationState> = combine(
    dosingSummaries,
    spotlightIndices
) { summaries, indices ->
    TankDosingPresentationState(
        summaries = summaries,
        spotlightIndices = indices
    )
}

private fun buildTankDetailDevicesUiState(
    devices: List<TankDeviceListItem>,
    interaction: TankDeviceInteractionState,
    dosingPresentation: TankDosingPresentationState
): TankDetailDevicesUiState {
    val items = devices.map { device ->
        val compactCard = DeviceCompactSnapshotMapper.map(device)
        val liveDosingSummary = dosingPresentation.summaries[device.deviceUid]
            .takeIf { device.availability == OwnerDeviceAvailability.REACHABLE }
        val dosingCard = if (device.family == OwnerDeviceFamily.DOSING) {
            compactCard.toDosingSpotlightCardUi(
                summary = liveDosingSummary,
                selectedIndex = dosingPresentation.spotlightIndices[device.deviceUid] ?: 0
            )
        } else {
            null
        }
        TankAssignedDeviceItem(
            deviceUid = device.deviceUid,
            title = device.displayName,
            card = compactCard,
            dosingCard = dosingCard
        )
    }
    return TankDetailDevicesUiState(
        devices = items,
        isEmpty = items.isEmpty(),
        isLoading = false,
        openingDeviceId = interaction.openingDeviceId,
        isOpeningDeviceMenu = interaction.openingDeviceId != null,
        isRemovingDevice = interaction.isRemovingDevice
    )
}

private fun isDeviceRemovalAllowed(
    tankId: Long,
    deviceUid: String,
    isRemovingDevice: Boolean,
    openingDeviceId: String?
): Boolean {
    val requestIsValid = tankId > 0L && deviceUid.isNotBlank()
    val operationsAreIdle = !isRemovingDevice && openingDeviceId == null
    return requestIsValid && operationsAreIdle
}

private fun DeviceMenuOpenResult.Unavailable.toUnavailableEvent() =
    TankDetailDevicesEvent.ShowDeviceUnavailable(
        title = title,
        messageRes = DeviceMenuUnavailableMessageMapper.messageRes(reason)
    )
