package com.aqua.aqualight.ui.tabs.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
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
    private val menuAccessOperations: DeviceMenuAccessOperations,
    private val routeResolver: DeviceRouteResolver
) : ViewModel() {

    private val selectedDeviceUids = MutableStateFlow<Set<String>>(emptySet())
    private val openingDeviceUid = MutableStateFlow<String?>(null)
    private val deletingDevices = MutableStateFlow(false)
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<DevicesEvent>(capacity = Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

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
        if (openingDeviceUid.value == deviceUid) return

        menuOpenJob?.cancel()
        openingDeviceUid.value = deviceUid
        menuOpenJob = viewModelScope.launch {
            val result = try {
                menuAccessOperations.resolve(deviceUid)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                DeviceMenuAccessResult.Unavailable(
                    title = "",
                    reason = DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
                )
            }

            when (result) {
                is DeviceMenuAccessResult.Available -> {
                    if (result.presentationPrepared) {
                        _events.send(
                            DevicesEvent.OpenRoute(
                                route = routeResolver.resolve(result)
                            )
                        )
                    } else {
                        _events.send(
                            DevicesEvent.ShowDeviceUnavailable(
                                requestDeviceUid = deviceUid,
                                title = result.title,
                                messageRes = DeviceMenuUnavailableMessageMapper.messageRes(
                                    DeviceMenuUnavailableReason.CURRENT_DATA_NOT_READY
                                )
                            )
                        )
                    }
                }
                is DeviceMenuAccessResult.Unavailable ->
                    _events.send(
                        DevicesEvent.ShowDeviceUnavailable(
                            requestDeviceUid = deviceUid,
                            title = result.title,
                            messageRes = DeviceMenuUnavailableMessageMapper.messageRes(
                                result.reason
                            )
                        )
                    )
            }
        }
    }

    /** Keeps the central loading barrier visible until the UI consumes the terminal menu event. */
    fun onDeviceMenuOpenHandled(deviceUid: String) {
        if (openingDeviceUid.value == deviceUid) {
            openingDeviceUid.value = null
            menuOpenJob = null
        }
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
                    val mapped = DeviceCardMapper.map(device = device)
                    mapped.copy(
                        card = mapped.card.copy(
                            isBusy = operation.openingDeviceUid == device.deviceUid
                        ),
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
