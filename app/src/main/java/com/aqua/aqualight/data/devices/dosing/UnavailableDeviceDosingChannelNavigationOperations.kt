package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Keeps Dosing routes present while production device data remains intentionally disconnected. */
internal object UnavailableDeviceDosingChannelNavigationOperations :
    DeviceDosingChannelNavigationOperations {

    override suspend fun resolve(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = null

    override fun observeTargets(
        deviceUid: String
    ): Flow<List<DeviceDosingChannelNavigationTarget>> = flowOf(emptyList())

    override suspend fun refreshTargets(deviceUid: String): Boolean = false

    override suspend fun resolveCurrent(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelNavigationTarget? = null
}
