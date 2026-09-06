package com.aqua.aqualight.ui.tabs.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val operations: OwnerDevicesOperations,
    private val menuOpenUseCase: DeviceMenuOpenUseCase,
    private val routeResolver: DeviceRouteResolver
) : ViewModel() {

    private val selectedDeviceUids = MutableStateFlow<Set<String>>(emptySet())
    private val openingDeviceUid = MutableStateFlow<String?>(null)
    private val deletingDevices = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<DevicesEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

    private var pendingMenuOpen: DeviceMenuOpenResult.Ready? = null
    private var menuOpenJob: Job? = null

    init {
        operations.start(viewModelScope)
        observeDevices()
        operations.refreshVisibleDevices()
    }

    fun onScreenVisible() {
        operations.refreshVisibleDevices()
    }

    fun onDeviceClicked(deviceUid: String) {
        if (deviceUid.isBlank() || deletingDevices.value) return
        if (_uiState.value.selectionMode) {
            toggleDeviceSelection(deviceUid)
            return
        }
        if (openingDeviceUid.value != null) return

        menuOpenJob?.cancel()
        openingDeviceUid.value = deviceUid
        menuOpenJob = viewModelScope.launch {
            try {
                when (val result = menuOpenUseCase.resolve(deviceUid)) {
                    is DeviceMenuOpenResult.Ready -> {
                        pendingMenuOpen = result
                        _events.send(
                            DevicesEvent.OpenRoute(
                                route = routeResolver.resolve(result.access)
                            )
                        )
                    }
                    is DeviceMenuOpenResult.Unavailable -> {
                        if (openingDeviceUid.value == deviceUid) {
                            openingDeviceUid.value = null
                        }
                        _events.send(result.toUnavailableEvent())
                    }
                }
            } catch (error: Throwable) {
                abandonPendingNavigation(deviceUid)
                if (openingDeviceUid.value == deviceUid) {
                    openingDeviceUid.value = null
                }
                if (error is CancellationException) throw error
                _events.send(
                    DevicesEvent.ShowDeviceUnavailable(
                        title = _uiState.value.devices
                            .firstOrNull { device -> device.deviceUid == deviceUid }
                            ?.card
                            ?.displayName
                            .orEmpty(),
                        messageRes = DeviceMenuUnavailableMessageMapper.messageRes(
                            DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                        ),
                        reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN,
                        diagnostic = DeviceOperationDiagnostic(
                            stage = "DEVICES_VIEW_MODEL",
                            outcome = "UNEXPECTED_EXCEPTION",
                            detail = "${error::class.java.name}: ${error.message.orEmpty()}"
                        )
                    )
                )
            }
        }
    }

    /** Called after the Fragment has either committed or abandoned the navigation attempt. */
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
        if (openingDeviceUid.value == deviceUid) {
            openingDeviceUid.value = null
        }
    }

    /** Cancels any in-flight open and invalidates a prepared handoff owned by the destroyed host. */
    fun onNavigationHostDestroyed() {
        menuOpenJob?.cancel()
        pendingMenuOpen?.let(menuOpenUseCase::abandon)
        pendingMenuOpen = null
        openingDeviceUid.value = null
    }

    fun onDeviceLongClicked(deviceUid: String) {
        if (
            deviceUid.isBlank() ||
            deletingDevices.value ||
            openingDeviceUid.value != null
        ) {
            return
        }
        selectedDeviceUids.value = selectedDeviceUids.value + deviceUid
    }

    fun clearSelection() {
        if (!deletingDevices.value) selectedDeviceUids.value = emptySet()
    }

    fun deleteSelectedDevices() {
        val selected = selectedDeviceUids.value
        if (
            selected.isEmpty() ||
            deletingDevices.value ||
            openingDeviceUid.value != null
        ) {
            return
        }

        viewModelScope.launch {
            deletingDevices.value = true
            try {
                val result = operations.deleteDevices(selected)
                selectedDeviceUids.value = result.failedDeviceUids

                when {
                    result.isCompleteSuccess -> Unit
                    result.isCompleteFailure ->
                        _events.send(
                            DevicesEvent.ShowDeleteFailed(
                                failedCount = result.failedCount
                            )
                        )
                    else ->
                        _events.send(
                            DevicesEvent.ShowDeletePartialSuccess(
                                succeededCount = result.succeededCount,
                                failedCount = result.failedCount
                            )
                        )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _events.send(
                    DevicesEvent.ShowDeleteFailed(failedCount = selected.size)
                )
            } finally {
                deletingDevices.value = false
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

    private fun toggleDeviceSelection(deviceUid: String) {
        val current = selectedDeviceUids.value
        selectedDeviceUids.value = if (deviceUid in current) {
            current - deviceUid
        } else {
            current + deviceUid
        }
    }

    private fun observeDevices() {
        val operationState = combine(
            openingDeviceUid,
            deletingDevices
        ) { currentOpeningDeviceUid, isDeletingDevices ->
            OperationState(
                openingDeviceUid = currentOpeningDeviceUid,
                isDeletingDevices = isDeletingDevices
            )
        }

        viewModelScope.launch {
            combine(
                operations.devices,
                selectedDeviceUids,
                operationState
            ) { devices, selectedUids, operation ->
                val cards = devices.map { device ->
                    DeviceCardMapper.map(device = device).copy(
                        isSelected = device.deviceUid in selectedUids
                    )
                }
                val visibleSelectedCount = cards.count { card -> card.isSelected }
                DevicesUiState(
                    devices = cards,
                    isEmpty = cards.isEmpty(),
                    isDiscovering = cards.isEmpty(),
                    selectionMode = visibleSelectedCount > 0,
                    selectedCount = visibleSelectedCount,
                    openingDeviceUid = operation.openingDeviceUid,
                    isOpeningDeviceMenu = operation.openingDeviceUid != null,
                    isDeletingDevices = operation.isDeletingDevices
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private data class OperationState(
        val openingDeviceUid: String?,
        val isDeletingDevices: Boolean
    )

    data class DevicesUiState(
        val devices: List<DeviceCardUi> = emptyList(),
        val isEmpty: Boolean = true,
        val isDiscovering: Boolean = true,
        val selectionMode: Boolean = false,
        val selectedCount: Int = 0,
        val openingDeviceUid: String? = null,
        val isOpeningDeviceMenu: Boolean = false,
        val isDeletingDevices: Boolean = false
    )
}

private fun DeviceMenuOpenResult.Unavailable.toUnavailableEvent() =
    DevicesEvent.ShowDeviceUnavailable(
        title = title,
        messageRes = DeviceMenuUnavailableMessageMapper.messageRes(reason),
        reason = reason,
        diagnostic = diagnostic
    )
