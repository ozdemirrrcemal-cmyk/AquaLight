package com.aqua.aqualight.ui.tabs.aquarium.detail.devices.select

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
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
    private val assignmentRepository = TankDeviceAssignmentRepository(
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(application)
    )

    private val _uiState = MutableStateFlow(TankDeviceSelectUiState())
    val uiState: StateFlow<TankDeviceSelectUiState> = _uiState.asStateFlow()

    private val _events = Channel<TankDeviceSelectEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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
            assignmentRepository.availableDevicesForTank(tankId).collect { snapshots ->
                val items = snapshots.map { snapshot ->
                    TankDeviceSelectItem(
                        deviceUid = snapshot.deviceUid.value,
                        card = DeviceCompactSnapshotMapper.map(
                            snapshot = snapshot,
                            supportingText = "Available • ${DeviceCompactSnapshotMapper.familyLabel(snapshot.product.family)}",
                            showAction = true,
                            actionText = "Add"
                        )
                    )
                }

                _uiState.value = TankDeviceSelectUiState(
                    devices = items,
                    isEmpty = items.isEmpty()
                )
            }
        }
    }

    fun onDeviceClicked(
        item: TankDeviceSelectItem
    ) {
        val tankId = boundTankId
        if (tankId <= 0L || item.deviceUid.isBlank()) {
            return
        }

        viewModelScope.launch {
            assignmentRepository.assignDeviceToTank(
                tankId = tankId,
                deviceUid = DeviceUid(item.deviceUid)
            )

            _events.send(TankDeviceSelectEvent.DeviceAssigned)
        }
    }
}

data class TankDeviceSelectUiState(
    val devices: List<TankDeviceSelectItem> = emptyList(),
    val isEmpty: Boolean = true
)

sealed interface TankDeviceSelectEvent {
    data object DeviceAssigned : TankDeviceSelectEvent
}
