package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRoute
import com.aqua.aqualight.ui.tabs.devices.route.DeviceRouteResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val devicesRepository = DevicesRepositoryProvider.get(application)
    private val routeResolver = DeviceRouteResolver()
    private val assignmentRepository = TankDeviceAssignmentRepository(
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(application)
    )

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDetailDevicesEvent>(Channel.BUFFERED)
    val events: Flow<TankDetailDevicesEvent> = _events.receiveAsFlow()

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
            assignmentRepository.assignedDevicesForTank(tankId).collect { snapshots ->
                val items = snapshots.map { snapshot ->
                    TankAssignedDeviceItem(
                        deviceUid = snapshot.deviceUid.value,
                        title = snapshot.title.ifBlank { "Device" },
                        card = DeviceCompactSnapshotMapper.map(
                            snapshot = snapshot
                        )
                    )
                }

                _uiState.value = TankDetailDevicesUiState(
                    devices = items,
                    isEmpty = items.isEmpty()
                )
            }
        }
    }

    fun onDeviceClicked(
        deviceUid: String
    ) {
        if (deviceUid.isBlank()) {
            return
        }

        viewModelScope.launch {
            val route = runCatching {
                val uid = DeviceUid(deviceUid)
                val snapshot = devicesRepository.currentDevice(uid)

                if (snapshot != null && snapshot.endpoint.hasWebSocketEndpoint) {
                    devicesRepository.connectRuntime(uid)
                }

                routeResolver.resolve(
                    snapshot = snapshot,
                    requestedDeviceUid = deviceUid
                )
            }.getOrElse {
                routeResolver.resolve(
                    snapshot = null,
                    requestedDeviceUid = deviceUid
                )
            }

            _events.send(TankDetailDevicesEvent.OpenDeviceRoute(route))
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
    val isEmpty: Boolean = true
)

sealed interface TankDetailDevicesEvent {
    data class OpenDeviceRoute(
        val route: DeviceRoute
    ) : TankDetailDevicesEvent
}
