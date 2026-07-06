package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
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
    private val assignmentRepository = TankDeviceAssignmentRepository(
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(application)
    )

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

    private val openingDeviceMenu = MutableStateFlow(false)

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
                openingDeviceMenu
            ) { snapshots, isOpeningDeviceMenu ->
                val items = snapshots.map { snapshot ->
                    TankAssignedDeviceItem(
                        deviceUid = snapshot.deviceUid.value,
                        title = snapshot.title.ifBlank { "Device" },
                        card = DeviceCompactSnapshotMapper.map(
                            snapshot = snapshot
                        )
                    )
                }

                TankDetailDevicesUiState(
                    devices = items,
                    isEmpty = items.isEmpty(),
                    isOpeningDeviceMenu = isOpeningDeviceMenu
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun onDeviceClicked(
        deviceUid: String
    ) {
        if (deviceUid.isBlank() || openingDeviceMenu.value) {
            return
        }

        viewModelScope.launch {
            openingDeviceMenu.value = true
            val result = runCatching {
                menuOpenGate.resolve(deviceUid)
            }.getOrElse {
                DeviceMenuOpenGateResult.Blocked(
                    title = "Device",
                    message = "Make sure it is powered on and connected to the same Wi-Fi network."
                )
            }
            openingDeviceMenu.value = false

            when (result) {
                is DeviceMenuOpenGateResult.OpenRoute -> {
                    _events.send(TankDetailDevicesEvent.OpenDeviceRoute(result.route))
                }

                is DeviceMenuOpenGateResult.Blocked -> {
                    _events.send(
                        TankDetailDevicesEvent.ShowDeviceUnavailable(
                            title = result.title,
                            message = result.message
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
        if (tankId <= 0L || deviceUid.isBlank()) {
            return
        }

        assignmentRepository.removeDeviceFromTank(
            tankId = tankId,
            deviceUid = DeviceUid(deviceUid)
        )
    }
}

data class TankDetailDevicesUiState(
    val devices: List<TankAssignedDeviceItem> = emptyList(),
    val isEmpty: Boolean = true,
    val isOpeningDeviceMenu: Boolean = false
)

sealed interface TankDetailDevicesEvent {
    data class OpenDeviceRoute(
        val route: DeviceRoute
    ) : TankDetailDevicesEvent

    data class ShowDeviceUnavailable(
        val title: String,
        val message: String
    ) : TankDetailDevicesEvent
}
