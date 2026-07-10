package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGate
import com.aqua.aqualight.ui.tabs.devices.route.DeviceMenuOpenGateResult
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val devicesRepository = DevicesRepositoryProvider.get(application)
    private val menuOpenGate = DeviceMenuOpenGate(devicesRepository)
    private val assignmentRepository =
        TankDeviceAssignmentRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

    private val openingDeviceMenu = MutableStateFlow(false)
    private val removingDeviceUid = MutableStateFlow<String?>(null)

    private var boundTankId: Long = 0L
    private var observeJob: Job? = null

    fun bind(
        tankId: Long
    ) {
        if (tankId <= 0L || boundTankId == tankId) {
            return
        }

        boundTankId = tankId
        devicesRepository.start(viewModelScope)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                assignmentRepository.assignedDevicesForTank(tankId),
                openingDeviceMenu,
                removingDeviceUid
            ) { assignedDevices, isOpeningDeviceMenu, activeRemovalUid ->
                val items = assignedDevices.map { assignedDevice ->
                    val snapshot = assignedDevice.snapshot
                    val assignedTankText = getApplication<Application>().getString(
                        R.string.device_assigned_tank_supporting_text,
                        assignedDevice.tankName
                    )

                    TankAssignedDeviceItem(
                        deviceUid = snapshot.deviceUid.value,
                        title = snapshot.title.ifBlank { "Device" },
                        card = DeviceCompactSnapshotMapper.map(
                            snapshot = snapshot,
                            supportingText = assignedTankText
                        )
                    )
                }

                TankDetailDevicesUiState(
                    devices = items,
                    isEmpty = items.isEmpty(),
                    isOpeningDeviceMenu = isOpeningDeviceMenu,
                    removingDeviceUid = activeRemovalUid
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onDeviceClicked(
        deviceUid: String
    ) {
        if (
            deviceUid.isBlank() ||
            openingDeviceMenu.value ||
            removingDeviceUid.value != null
        ) {
            return
        }

        viewModelScope.launch {
            openingDeviceMenu.value = true
            val result = runCatching {
                menuOpenGate.resolve(deviceUid)
            }.getOrElse {
                DeviceMenuOpenGateResult.Blocked(
                    title = "",
                    messageRes = R.string.device_menu_offline_message
                )
            }
            openingDeviceMenu.value = false

            when (result) {
                is DeviceMenuOpenGateResult.OpenRoute -> {
                    _events.send(
                        TankDetailDevicesEvent.OpenDeviceRoute(result.route)
                    )
                }

                is DeviceMenuOpenGateResult.Blocked -> {
                    _events.send(
                        TankDetailDevicesEvent.ShowDeviceUnavailable(
                            title = result.title,
                            messageRes = result.messageRes
                        )
                    )
                }
            }
        }
    }

    fun removeDeviceFromTank(
        deviceUid: String
    ) {
        val tankId = boundTankId

        if (
            tankId <= 0L ||
            deviceUid.isBlank() ||
            removingDeviceUid.value != null
        ) {
            return
        }

        viewModelScope.launch {
            removingDeviceUid.value = deviceUid

            val result = runCatching {
                assignmentRepository.removeDeviceFromTank(
                    tankId = tankId,
                    deviceUid = DeviceUid(deviceUid)
                )
            }.getOrElse {
                RemoveDeviceFromTankResult.InvalidInput
            }

            removingDeviceUid.value = null

            when (result) {
                is RemoveDeviceFromTankResult.Removed,
                RemoveDeviceFromTankResult.NotAssigned -> Unit

                RemoveDeviceFromTankResult.Unauthenticated -> {
                    _events.send(
                        TankDetailDevicesEvent.ShowOperationError(
                            messageRes = R.string.tank_device_assignment_session_missing
                        )
                    )
                }

                RemoveDeviceFromTankResult.InvalidInput -> {
                    _events.send(
                        TankDetailDevicesEvent.ShowOperationError(
                            messageRes = R.string.tank_device_remove_failed
                        )
                    )
                }
            }
        }
    }
}

data class TankDetailDevicesUiState(
    val devices: List<TankAssignedDeviceItem> = emptyList(),
    val isEmpty: Boolean = true,
    val isOpeningDeviceMenu: Boolean = false,
    val removingDeviceUid: String? = null
) {
    val isRemovingDevice: Boolean
        get() = removingDeviceUid != null
}

sealed interface TankDetailDevicesEvent {
    data class OpenDeviceRoute(
        val route: DeviceRoute
    ) : TankDetailDevicesEvent

    data class ShowDeviceUnavailable(
        val title: String,
        @StringRes val messageRes: Int
    ) : TankDetailDevicesEvent

    data class ShowOperationError(
        @StringRes val messageRes: Int
    ) : TankDetailDevicesEvent
}
