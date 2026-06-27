package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class TankDeviceAssignmentRepository(
    private val devicesRepository: DevicesRepository,
    private val assignmentStore: TankDeviceAssignmentStore
) {
    fun assignedDevicesForTank(
        tankId: Long
    ): Flow<List<DeviceSnapshot>> {
        return combine(
            devicesRepository.devices,
            assignmentStore.assignments
        ) { devices, assignments ->
            val deviceByUid = devices.associateBy { snapshot ->
                snapshot.deviceUid
            }

            assignments
                .filter { assignment -> assignment.tankId == tankId }
                .sortedBy { assignment -> assignment.assignedAtMillis }
                .mapNotNull { assignment ->
                    deviceByUid[assignment.deviceUid]
                }
        }
    }

    fun availableDevicesForTank(
        tankId: Long
    ): Flow<List<DeviceSnapshot>> {
        return combine(
            devicesRepository.devices,
            assignmentStore.assignments
        ) { devices, assignments ->
            val assignedDeviceUids = assignments
                .map { assignment -> assignment.deviceUid }
                .toSet()

            devices
                .filter { snapshot ->
                    snapshot.deviceUid !in assignedDeviceUids
                }
                .sortedBy { snapshot ->
                    snapshot.title.lowercase()
                }
        }
    }

    fun assignDeviceToTank(
        tankId: Long,
        deviceUid: DeviceUid
    ) {
        assignmentStore.assignDeviceToTank(
            tankId = tankId,
            deviceUid = deviceUid
        )
    }

    fun removeDeviceFromTank(
        tankId: Long,
        deviceUid: DeviceUid
    ) {
        assignmentStore.removeDeviceFromTank(
            tankId = tankId,
            deviceUid = deviceUid
        )
    }
}
