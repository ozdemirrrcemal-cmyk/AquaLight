package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceCoolingModeOption
import com.aqua.aqualight.application.devices.DeviceCoolingOperationResult
import com.aqua.aqualight.application.devices.DeviceCoolingOperations
import com.aqua.aqualight.application.devices.DeviceCoolingSnapshot
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceRootOperations(
    private val devicesRepository: DevicesRepository
) : DeviceRootOperations, DeviceCoolingOperations {
    private val coolingOperations = DefaultDeviceCoolingOperations(devicesRepository)

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

    override fun observeCooling(deviceUid: String): Flow<DeviceCoolingSnapshot?> =
        coolingOperations.observeCooling(deviceUid)

    override fun currentCooling(deviceUid: String): DeviceCoolingSnapshot? =
        coolingOperations.currentCooling(deviceUid)

    override suspend fun refresh(deviceUid: String): DeviceCoolingOperationResult =
        coolingOperations.refresh(deviceUid)

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingModeOption,
        save: Boolean
    ): DeviceCoolingOperationResult = coolingOperations.setMode(deviceUid, mode, save)

    override suspend fun setTemperatureRange(
        deviceUid: String,
        minTemperatureC: Double,
        maxTemperatureC: Double,
        save: Boolean
    ): DeviceCoolingOperationResult = coolingOperations.setTemperatureRange(
        deviceUid,
        minTemperatureC,
        maxTemperatureC,
        save
    )

    override suspend fun setFanDisplayName(
        deviceUid: String,
        fanKey: String,
        displayName: String?,
        save: Boolean
    ): DeviceCoolingOperationResult = coolingOperations.setFanDisplayName(
        deviceUid,
        fanKey,
        displayName,
        save
    )
}
