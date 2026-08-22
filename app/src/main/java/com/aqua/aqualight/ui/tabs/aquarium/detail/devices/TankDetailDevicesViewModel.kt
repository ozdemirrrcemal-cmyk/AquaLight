package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuPresentationState
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuPresentationStateHolder
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    private val assignmentOperations: TankDeviceAssignmentOperations,
    private val menuAccessOperations: DeviceMenuAccessOperations,
    private val routeResolver: DeviceRouteResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

    private val deviceMenuPresentation = DeviceMenuPresentationStateHolder(routeResolver)
    private val removingDevice = MutableStateFlow(false)
    private var boundTankId: Long = 0L
    private var observeJob: Job? = null
    private var menuOpenJob: Job? = null

    fun bind(tankId: Long) {
        if (tankId <= 0L || boundTankId == tankId) return

        boundTankId = tankId
        assignmentOperations.start(viewModelScope)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                assignmentOperations.assignedDevices(tankId),
                deviceMenuPresentation.state,
                removingDevice
            ) { devices, currentDeviceMenuState, isRemovingDevice ->
                val items = devices.map { device ->
                    TankAssignedDeviceItem(
                        deviceUid = device.deviceUid,
                        title = device.displayName,
                        card = DeviceCompactSnapshotMapper.map(device).copy(
                            isBusy = currentDeviceMenuState.deviceUid == device.deviceUid
                        )
                    )
                }
                TankDetailDevicesUiState(
                    devices = items,
                    isEmpty = items.isEmpty(),
                    isLoading = false,
                    deviceMenuState = currentDeviceMenuState,
                    isRemovingDevice = isRemovingDevice
                )
            }.catch {
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
        if (deviceUid.isBlank() || removingDevice.value) return
        val request = deviceMenuPresentation.begin(deviceUid) ?: return

        menuOpenJob?.cancel()
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

            deviceMenuPresentation.complete(request = request, result = result)
        }
    }

    fun onDeviceMenuResultHandled(requestId: Long) {
        if (deviceMenuPresentation.acknowledge(requestId)) {
            menuOpenJob = null
        }
    }

    fun removeDeviceFromTank(deviceUid: String) {
        val tankId = boundTankId
        if (!canRemoveDevice(tankId, deviceUid)) return

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

    private fun canRemoveDevice(tankId: Long, deviceUid: String): Boolean {
        val requestIsValid = tankId > 0L && deviceUid.isNotBlank()
        val operationsAreIdle =
            !removingDevice.value &&
                deviceMenuPresentation.isIdle
        return requestIsValid && operationsAreIdle
    }
}

data class TankDetailDevicesUiState(
    val devices: List<TankAssignedDeviceItem> = emptyList(),
    val isEmpty: Boolean = true,
    val isLoading: Boolean = true,
    val deviceMenuState: DeviceMenuPresentationState =
        DeviceMenuPresentationState.Idle,
    val isRemovingDevice: Boolean = false
) {
    val openingDeviceId: String?
        get() = deviceMenuState.deviceUid

    val isOpeningDeviceMenu: Boolean
        get() = deviceMenuState !is DeviceMenuPresentationState.Idle
}

sealed interface TankDetailDevicesEvent {
    data object ShowRemoveFailed : TankDetailDevicesEvent
    data object ShowLoadFailed : TankDetailDevicesEvent
}
