package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import com.aqua.aqualight.ui.common.devicepresence.DeviceMenuUnavailableMessageMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
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
    private val menuOpenUseCase: DeviceMenuOpenUseCase,
    private val routeResolver: DeviceRouteResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

    private val openingDeviceId = MutableStateFlow<String?>(null)
    private val removingDevice = MutableStateFlow(false)
    private var boundTankId: Long = 0L
    private var observeJob: Job? = null
    private var menuOpenJob: Job? = null
    private var pendingMenuOpen: DeviceMenuOpenResult.Ready? = null

    fun bind(tankId: Long) {
        if (tankId <= 0L || boundTankId == tankId) return

        boundTankId = tankId
        assignmentOperations.start(viewModelScope)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                assignmentOperations.assignedDevices(tankId),
                openingDeviceId,
                removingDevice
            ) { devices, currentOpeningDeviceId, isRemovingDevice ->
                val items = devices.map { device ->
                    TankAssignedDeviceItem(
                        deviceUid = device.deviceUid,
                        title = device.displayName,
                        card = DeviceCompactSnapshotMapper.map(device)
                    )
                }
                TankDetailDevicesUiState(
                    devices = items,
                    isEmpty = items.isEmpty(),
                    isLoading = false,
                    openingDeviceId = currentOpeningDeviceId,
                    isOpeningDeviceMenu = currentOpeningDeviceId != null,
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

    private fun canRemoveDevice(tankId: Long, deviceUid: String): Boolean {
        val requestIsValid = tankId > 0L && deviceUid.isNotBlank()
        val operationsAreIdle = !removingDevice.value && openingDeviceId.value == null
        return requestIsValid && operationsAreIdle
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

private fun DeviceMenuOpenResult.Unavailable.toUnavailableEvent() =
    TankDetailDevicesEvent.ShowDeviceUnavailable(
        title = title,
        messageRes = DeviceMenuUnavailableMessageMapper.messageRes(reason)
    )
