package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceStatusOperations
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultDeviceStatusOperations(
    private val devicesRepository: DevicesRepository
) : DeviceStatusOperations {

    override val statuses: Flow<List<OwnerDeviceStatusSnapshot>> =
        devicesRepository.devices.map { snapshots ->
            snapshots.map(DeviceSnapshotMapping::toStatus)
        }

    override fun start(scope: CoroutineScope): Job = devicesRepository.start(scope)

    private object DeviceSnapshotMapping {
        fun toStatus(snapshot: com.aqua.aqualight.data.devices.model.DeviceSnapshot) =
            snapshot.toOwnerDeviceStatusSnapshot()
    }
}
