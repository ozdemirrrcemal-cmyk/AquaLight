package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget

/** Keeps Dosing routes present while production device data remains intentionally disconnected. */
internal object UnavailableDeviceDosingChannelNavigationOperations :
    DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = null

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = null
}
