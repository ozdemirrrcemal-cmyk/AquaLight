package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceRootOperations(
    private val devicesRepository: DevicesRepository
) : DeviceRootOperations {

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) return flowOf(null)
        return devicesRepository.observeDevice(DeviceUid(normalized))
            .map { snapshot -> snapshot?.toDeviceRootSnapshot() }
    }

    override fun current(deviceUid: String): DeviceRootSnapshot? {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) return null
        return devicesRepository.currentDevice(DeviceUid(normalized))?.toDeviceRootSnapshot()
    }

    override fun connect(deviceUid: String): Result<Unit> {
        val normalized = deviceUid.trim()
        if (normalized.isBlank()) {
            return Result.failure(IllegalArgumentException("Device uid is missing."))
        }
        return devicesRepository.connectRuntime(DeviceUid(normalized))
    }
}
