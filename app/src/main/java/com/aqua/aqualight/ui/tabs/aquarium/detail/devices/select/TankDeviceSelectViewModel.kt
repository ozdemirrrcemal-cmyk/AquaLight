package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TankDeviceSelectViewModel(
    private val assignmentOperations: TankDeviceAssignmentOperations
) : ViewModel() {

    private val _uiState = MutableStateFlow(TankDeviceSelectUiState())
    val uiState: StateFlow<TankDeviceSelectUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDeviceSelectEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var boundTankId: Long = 0L
    private var observeJob: Job? = null

    fun bind(tankId: Long) {
        if (tankId <= 0L || boundTankId == tankId) return

        boundTankId = tankId
        observeJob?.cancel()
        assignmentOperations.start(viewModelScope)
        observeJob = viewModelScope.launch {
            assignmentOperations.availableDevices(tankId)
                .catch {
                    _uiState.update { current ->
                        current.copy(
                            devices = emptyList(),
                            isEmpty = true,
                            isLoading = false
                        )
                    }
                    _events.send(TankDeviceSelectEvent.ShowLoadFailed)
                }
                .collect { snapshot ->
                    val items = snapshot.devices.map { device ->
                        TankDeviceSelectItem(
                            deviceUid = device.deviceUid,
                            card = DeviceCompactSnapshotMapper.map(device)
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            devices = items,
                            isEmpty = items.isEmpty(),
                            emptyReason = when {
                                items.isNotEmpty() -> TankDeviceSelectEmptyReason.NONE
                                snapshot.hasRegisteredDevices ->
                                    TankDeviceSelectEmptyReason.ALL_REGISTERED_DEVICES_ASSIGNED
                                else -> TankDeviceSelectEmptyReason.NO_REGISTERED_DEVICES
                            },
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onDeviceClicked(item: TankDeviceSelectItem) {
        val tankId = boundTankId
        val currentState = _uiState.value
        if (tankId <= 0L || item.deviceUid.isBlank() || currentState.isAssigning) return

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isAssigning = true) }
            val result = try {
                assignmentOperations.assignDevice(tankId, item.deviceUid)
            } finally {
                _uiState.update { current -> current.copy(isAssigning = false) }
            }

            when (result) {
                AssignDeviceToTankResult.Assigned,
                AssignDeviceToTankResult.AlreadyAssigned ->
                    _events.send(TankDeviceSelectEvent.DeviceAssigned)
                is AssignDeviceToTankResult.Conflict ->
                    _events.send(
                        TankDeviceSelectEvent.ShowAssignmentConflict(
                            existingTankId = result.existingTankId
                        )
                    )
                AssignDeviceToTankResult.TankNotFound ->
                    _events.send(TankDeviceSelectEvent.ShowTankNotFound)
                AssignDeviceToTankResult.DeviceNotFound ->
                    _events.send(TankDeviceSelectEvent.ShowDeviceNotFound)
                AssignDeviceToTankResult.InvalidRequest,
                AssignDeviceToTankResult.Failure ->
                    _events.send(TankDeviceSelectEvent.ShowAssignFailed)
            }
        }
    }
}

data class TankDeviceSelectUiState(
    val devices: List<TankDeviceSelectItem> = emptyList(),
    val isEmpty: Boolean = true,
    val emptyReason: TankDeviceSelectEmptyReason =
        TankDeviceSelectEmptyReason.NO_REGISTERED_DEVICES,
    val isLoading: Boolean = true,
    val isAssigning: Boolean = false
)

enum class TankDeviceSelectEmptyReason {
    NONE,
    NO_REGISTERED_DEVICES,
    ALL_REGISTERED_DEVICES_ASSIGNED
}

sealed interface TankDeviceSelectEvent {
    data object DeviceAssigned : TankDeviceSelectEvent
    data class ShowAssignmentConflict(val existingTankId: Long) : TankDeviceSelectEvent
    data object ShowTankNotFound : TankDeviceSelectEvent
    data object ShowDeviceNotFound : TankDeviceSelectEvent
    data object ShowAssignFailed : TankDeviceSelectEvent
    data object ShowLoadFailed : TankDeviceSelectEvent
}
