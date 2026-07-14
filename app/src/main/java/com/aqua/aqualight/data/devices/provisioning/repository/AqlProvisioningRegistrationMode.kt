package com.aqua.aqualight.data.devices.provisioning.repository

import com.aqua.aqualight.data.devices.model.DeviceUid

/**
 * Defines whether a verified provisioning session creates a local registration
 * or refreshes the existing registration for the same physical device.
 *
 * This resolver is called only after the secure BLE provisioning contract has
 * verified the device identity and accepted its setup mode.
 */
enum class AqlProvisioningRegistrationMode {
    NEW_DEVICE,
    RECONFIGURE_EXISTING
}

object AqlProvisioningRegistrationModeResolver {

    fun resolve(
        existingDeviceUid: DeviceUid?,
        verifiedDeviceUid: DeviceUid
    ): AqlProvisioningRegistrationMode {
        val verifiedValue = verifiedDeviceUid.value.trim()
        require(verifiedValue.isNotBlank()) {
            "Verified device UID must not be blank."
        }

        if (existingDeviceUid == null) {
            return AqlProvisioningRegistrationMode.NEW_DEVICE
        }

        require(
            existingDeviceUid.value.trim().equals(
                verifiedValue,
                ignoreCase = true
            )
        ) {
            "Existing registration does not match the verified BLE device."
        }

        return AqlProvisioningRegistrationMode.RECONFIGURE_EXISTING
    }
}
