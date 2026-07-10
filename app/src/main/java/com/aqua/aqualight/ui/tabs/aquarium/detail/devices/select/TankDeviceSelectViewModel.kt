package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.devices.AssignDeviceToTankResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepositoryProvider
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class TankDeviceSelectViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val devicesRepository = DevicesRepositoryProvider.get(application)
    private val assignmentRepository =
        TankDeviceAssignmentRepositoryProvider.get(application)

    private val _uiState = MutableStateFlow(TankDeviceSelectUiState())
    val uiState: StateFlow<TankDeviceSelectUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDeviceSelectEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var boundTankId: Long = 0L
    private var observeJob: Job? = null
    private var assigningDeviceUid: String? = null

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
            assignmentRepository.availableDevicesForTank(tankId).collect { snapshots ->
                val items = snapshots.map { snapshot ->
                    TankDeviceSelectItem(
                        deviceUid = snapshot.deviceUid.value,
                        card = DeviceCompactSnapshotMapper.map(
                            snapshot = snapshot
                        )
                    )
                }

                _uiState.value = TankDeviceSelectUiState(
                    devices = items,
                    isEmpty = items.isEmpty(),
                    assigningDeviceUid = assigningDeviceUid
                )
            }
        }
    }

    fun onDeviceClicked(
        item: TankDeviceSelectItem
    ) {
        val tankId = boundTankId

        if (
            tankId <= 0L ||
            item.deviceUid.isBlank() ||
            assigningDeviceUid != null
        ) {
            return
        }

        viewModelScope.launch {
            assigningDeviceUid = item.deviceUid
            publishOperationState()

            val result = runCatching {
                assignmentRepository.assignDeviceToTank(
                    tankId = tankId,
                    deviceUid = DeviceUid(item.deviceUid)
                )
            }.getOrElse {
                AssignDeviceToTankResult.InvalidInput
            }

            assigningDeviceUid = null
            publishOperationState()

            when (result) {
                is AssignDeviceToTankResult.Assigned -> {
                    _events.send(TankDeviceSelectEvent.DeviceAssigned)
                }

                is AssignDeviceToTankResult.AlreadyAssignedToTank -> {
                    if (result.tankId == tankId) {
                        _events.send(TankDeviceSelectEvent.DeviceAssigned)
                    } else {
                        _events.send(
                            TankDeviceSelectEvent.ShowAssignmentError(
                                messageRes = R.string.tank_device_assignment_already_assigned,
                                formatArg = result.tankName.ifBlank { "another aquarium" }
                            )
                        )
                    }
                }

                AssignDeviceToTankResult.TankNotFound -> {
                    _events.send(
                        TankDeviceSelectEvent.ShowAssignmentError(
                            messageRes = R.string.tank_device_assignment_tank_missing
                        )
                    )
                }

                AssignDeviceToTankResult.DeviceNotFound -> {
                    _events.send(
                        TankDeviceSelectEvent.ShowAssignmentError(
                            messageRes = R.string.tank_device_assignment_device_missing
                        )
                    )
                }

                AssignDeviceToTankResult.Unauthenticated -> {
                    _events.send(
                        TankDeviceSelectEvent.ShowAssignmentError(
                            messageRes = R.string.tank_device_assignment_session_missing
                        )
                    )
                }

                AssignDeviceToTankResult.InvalidInput -> {
                    _events.send(
                        TankDeviceSelectEvent.ShowAssignmentError(
                            messageRes = R.string.tank_device_assignment_failed
                        )
                    )
                }
            }
        }
    }

    private fun publishOperationState() {
        val current = _uiState.value
        _uiState.value = current.copy(
            assigningDeviceUid = assigningDeviceUid
        )
    }
}

data class TankDeviceSelectUiState(
    val devices: List<TankDeviceSelectItem> = emptyList(),
    val isEmpty: Boolean = true,
    val assigningDeviceUid: String? = null
) {
    val isAssigning: Boolean
        get() = assigningDeviceUid != null
}

sealed interface TankDeviceSelectEvent {
    data object DeviceAssigned : TankDeviceSelectEvent

    data class ShowAssignmentError(
        @StringRes val messageRes: Int,
        val formatArg: String? = null
    ) : TankDeviceSelectEvent
}
