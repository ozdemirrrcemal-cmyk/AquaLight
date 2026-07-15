package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeleteOwnerDevicesResult
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDevicesOperations
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignmentRepository
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
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
    private val deviceDataCleaner: OwnerDeviceDataCleaner
) : OwnerDevicesOperations {

    override val devices: Flow<List<OwnerDeviceListItem>> = combine(
        devicesRepository.devices,
        assignmentRepository.assignedTankNamesByDevice()
    ) { snapshots, tankNamesByDevice ->
        snapshots.map { snapshot ->
            snapshot.toApplicationItem(
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
        return DeleteOwnerDevicesResult(
            succeededDeviceUids = result.succeededDeviceUids
                .mapTo(linkedSetOf()) { deviceUid -> deviceUid.value },
            failedDeviceUids = result.failures
                .mapTo(linkedSetOf()) { failure -> failure.deviceUid.value }
        )
    }

    private fun DeviceSnapshot.toApplicationItem(
        assignedTankName: String
    ): OwnerDeviceListItem {
        return OwnerDeviceListItem(
            deviceUid = deviceUid.value,
            displayName = title.ifBlank { deviceUid.value },
            serialText = identity.serialNumber
                .ifBlank { identity.firmwareSerial }
                .ifBlank { identity.shortId }
                .ifBlank { deviceUid.value },
            family = product.family.toApplicationFamily(),
            availability = connectionState.onlineState.toApplicationAvailability(),
            assignedTankName = assignedTankName.trim()
        )
    }

    private fun DeviceFamily.toApplicationFamily(): OwnerDeviceFamily {
        return when (this) {
            DeviceFamily.LIGHT -> OwnerDeviceFamily.LIGHT
            DeviceFamily.TIMER -> OwnerDeviceFamily.TIMER
            DeviceFamily.DOSING -> OwnerDeviceFamily.DOSING
            DeviceFamily.COOLING -> OwnerDeviceFamily.COOLING
            DeviceFamily.UNKNOWN -> OwnerDeviceFamily.UNKNOWN
        }
    }

    private fun DeviceOnlineState.toApplicationAvailability(): OwnerDeviceAvailability {
        return when (this) {
            DeviceOnlineState.AUTHENTICATED,
            DeviceOnlineState.PROVISIONING,
            DeviceOnlineState.OTA_UPDATING -> OwnerDeviceAvailability.REACHABLE

            DeviceOnlineState.ONLINE_LAN,
            DeviceOnlineState.CONNECTING_WS,
            DeviceOnlineState.UNKNOWN,
            DeviceOnlineState.DISCOVERING,
            DeviceOnlineState.STALE,
            DeviceOnlineState.OFFLINE,
            DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
            DeviceOnlineState.AUTH_REQUIRED,
            DeviceOnlineState.ERROR -> OwnerDeviceAvailability.UNREACHABLE
        }
    }
}
