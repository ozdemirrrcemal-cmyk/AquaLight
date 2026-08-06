package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeleteOwnerDevicesResult
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.remove.OwnerDeviceDataCleaner
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal class DefaultOwnerDevicesOperations(
    private val devicesRepository: DevicesRepository,
    assignmentRepository: TankDeviceAssignmentRepository,
    private val deviceDataCleaner: OwnerDeviceDataCleaner,
    private val afterDeviceDeleted: suspend (String) -> Unit = {}
) : OwnerDevicesOperations {

    override val devices: Flow<List<OwnerDeviceListItem>> = combine(
        devicesRepository.devices,
        assignmentRepository.assignedTankNamesByDevice()
    ) { snapshots, tankNamesByDevice ->
        snapshots.map { snapshot ->
            snapshot.toOwnerDeviceListItem(
                assignedTankName = tankNamesByDevice[snapshot.deviceUid].orEmpty()
            )
        }
    }

    override fun start(scope: CoroutineScope): Job = devicesRepository.start(scope)

    override fun refreshVisibleDevices() {
        devicesRepository.refreshVisibleDevices()
    }

    override suspend fun deleteDevices(
        deviceUids: Set<String>
    ): DeleteOwnerDevicesResult {
        val normalizedDeviceUids = deviceUids
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::DeviceUid)
            .toSet()

        val result = deviceDataCleaner.deleteDevices(normalizedDeviceUids)
        result.succeededDeviceUids
            .sortedBy { deviceUid -> deviceUid.value }
            .forEach { deviceUid ->
                // Domain deletion is already committed. Notification/OTA cleanup is idempotent and
                // startup reconciliation remains the safety net if this derived cleanup fails.
                runCatching { afterDeviceDeleted(deviceUid.value) }
                    .rethrowFatalOrCancellation()
            }

        return DeleteOwnerDevicesResult(
            succeededDeviceUids = result.succeededDeviceUids
                .mapTo(linkedSetOf()) { deviceUid -> deviceUid.value },
            failedDeviceUids = result.failures
                .mapTo(linkedSetOf()) { failure -> failure.deviceUid.value }
        )
    }
}
