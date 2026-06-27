package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentStore
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.ui.common.devicecard.DeviceCompactSnapshotMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TankDetailDevicesViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val devicesRepository = DevicesRepositoryProvider.get(application)
    private val assignmentRepository = TankDeviceAssignmentRepository(
        devicesRepository = devicesRepository,
        assignmentStore = TankDeviceAssignmentStore.get(application)
    )

    private val _uiState = MutableStateFlow(TankDetailDevicesUiState())
    val uiState: StateFlow<TankDetailDevicesUiState> = _uiState.asStateFlow()

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
                            snapshot = snapshot,
                            supportingText = "Assigned • ${DeviceCompactSnapshotMapper.familyLabel(snapshot.product.family)}"
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
