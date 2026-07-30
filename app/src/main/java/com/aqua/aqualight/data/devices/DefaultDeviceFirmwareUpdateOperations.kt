package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceOtaCoordinator
import kotlinx.coroutines.flow.StateFlow

/** Shared OTA application adapter used by all family-specific Settings screens. */
internal class DefaultDeviceFirmwareUpdateOperations(
    private val devicesRepository: DevicesRepository
) : DeviceFirmwareUpdateOperations {

    private val coordinator = DeviceOtaCoordinator(
        snapshotProvider = devicesRepository::currentDevice,
        connectRuntime = devicesRepository::connectRuntime,
        updaterProvider = { devicesRepository.runtimeModules()?.firmwareUpdate },
        runtimeEvents = devicesRepository.runtimeEvents()
    )

    override fun observe(deviceUid: String): StateFlow<DeviceOtaState> =
        coordinator.observe(requireDeviceUid(deviceUid))

    override suspend fun checkAvailability(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<DeviceOtaState> = coordinator.checkAvailability(
        deviceUid = requireDeviceUid(deviceUid),
        manifestUrl = manifestUrl,
        applyNow = applyNow
    )

    override suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<PreparedDeviceFirmwareUpdate> = checkAvailability(
        deviceUid = deviceUid,
        manifestUrl = manifestUrl,
        applyNow = applyNow
    ).mapCatching { state ->
        when (state) {
            is DeviceOtaState.UpdateAvailable -> state.plan
            is DeviceOtaState.UpToDate -> error(
                "Device is already up to date: ${state.currentVersion}."
            )
            is DeviceOtaState.Unsupported -> error(state.reason)
            is DeviceOtaState.Failed -> error(state.message)
            else -> error("OTA availability did not produce a prepared update plan.")
        }
    }

    override fun startUpdate(
        plan: PreparedDeviceFirmwareUpdate
    ): DeviceFirmwareCommandResult = coordinator.startUpdate(plan)

    override fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.requestStatus(requireDeviceUid(deviceUid))

    override fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
        coordinator.clearStatus(requireDeviceUid(deviceUid))

    override fun close() {
        coordinator.close()
    }

    private fun requireDeviceUid(value: String): DeviceUid {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "Device uid is missing." }
        return DeviceUid(normalized)
    }
}
