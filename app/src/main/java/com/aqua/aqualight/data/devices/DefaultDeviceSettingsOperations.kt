package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DEVICE_CUSTOM_NAME_MAX_LENGTH
import com.aqua.aqualight.application.devices.DeviceSettingsOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository

/** Owner-scoped implementation shared by Light, Timer, Dosing and Cooling Settings. */
internal class DefaultDeviceSettingsOperations(
    private val devicesRepository: DevicesRepository
) : DeviceSettingsOperations {

    override suspend fun updateCustomName(
        deviceUid: String,
        customName: String
    ): Result<Unit> = runCatching {
        val normalizedUid = deviceUid.trim()
        val normalizedName = customName.trim()

        require(normalizedUid.isNotBlank()) { "Device uid is missing." }
        require(normalizedName.isNotBlank()) { "Device name is missing." }
        require(normalizedName.length <= DEVICE_CUSTOM_NAME_MAX_LENGTH) {
            "Device name exceeds the supported length."
        }

        devicesRepository.updateCustomName(
            deviceUid = DeviceUid(normalizedUid),
            customName = normalizedName
        )
    }
}
