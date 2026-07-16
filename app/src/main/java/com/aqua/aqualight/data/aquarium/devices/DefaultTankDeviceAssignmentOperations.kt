package com.aqua.aqualight.data.aquarium.devices

import com.aqua.aqualight.application.devices.AssignDeviceToTankResult
import com.aqua.aqualight.application.devices.AvailableTankDevicesSnapshot
import com.aqua.aqualight.application.devices.RemoveDeviceFromTankResult
import com.aqua.aqualight.application.devices.TankDeviceAssignmentOperations
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.toTankDeviceListItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class DefaultTankDeviceAssignmentOperations(
    private val assignmentRepository: TankDeviceAssignmentRepository,
    private val devicesRepository: DevicesRepository
) : TankDeviceAssignmentOperations {

    override fun start(scope: CoroutineScope): Job = devicesRepository.start(scope)

    override fun assignedDevices(
        tankId: Long
    ): Flow<List<TankDeviceListItem>> {
        return assignmentRepository.assignedDevicesForTank(tankId)
            .map { snapshots -> snapshots.map { it.toTankDeviceListItem() } }
    }

    override fun availableDevices(
        tankId: Long
    ): Flow<AvailableTankDevicesSnapshot> {
        return combine(
            assignmentRepository.availableDevicesForTank(tankId),
            devicesRepository.devices
        ) { availableDevices, registeredDevices ->
            AvailableTankDevicesSnapshot(
                devices = availableDevices.map { it.toTankDeviceListItem() },
                hasRegisteredDevices = registeredDevices.isNotEmpty()
            )
        }
    }

    override suspend fun assignDevice(
        tankId: Long,
        deviceUid: String
    ): AssignDeviceToTankResult {
        val normalizedDeviceUid = deviceUid.trim()
        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return AssignDeviceToTankResult.InvalidRequest
        }
        return when (
            val result = assignmentRepository.assignDeviceToTank(
                tankId = tankId,
                deviceUid = DeviceUid(normalizedDeviceUid)
            )
        ) {
            is TankDeviceAssignmentResult.Assigned -> AssignDeviceToTankResult.Assigned
            is TankDeviceAssignmentResult.AlreadyAssigned ->
                AssignDeviceToTankResult.AlreadyAssigned
            is TankDeviceAssignmentResult.Conflict ->
                AssignDeviceToTankResult.Conflict(result.existingAssignment.tankId)
            TankDeviceAssignmentResult.TankNotFound -> AssignDeviceToTankResult.TankNotFound
            TankDeviceAssignmentResult.DeviceNotFound -> AssignDeviceToTankResult.DeviceNotFound
            TankDeviceAssignmentResult.InvalidRequest -> AssignDeviceToTankResult.InvalidRequest
            is TankDeviceAssignmentResult.Failure -> AssignDeviceToTankResult.Failure
        }
    }

    override suspend fun removeDevice(
        tankId: Long,
        deviceUid: String
    ): RemoveDeviceFromTankResult {
        val normalizedDeviceUid = deviceUid.trim()
        if (tankId <= 0L || normalizedDeviceUid.isBlank()) {
            return RemoveDeviceFromTankResult.INVALID_REQUEST
        }
        return when (
            assignmentRepository.removeDeviceFromTank(
                tankId = tankId,
                deviceUid = DeviceUid(normalizedDeviceUid)
            )
        ) {
            TankDeviceRemovalResult.Removed -> RemoveDeviceFromTankResult.REMOVED
            TankDeviceRemovalResult.NotAssigned -> RemoveDeviceFromTankResult.NOT_ASSIGNED
            TankDeviceRemovalResult.InvalidRequest -> RemoveDeviceFromTankResult.INVALID_REQUEST
            is TankDeviceRemovalResult.Failure -> RemoveDeviceFromTankResult.FAILURE
        }
    }
}
