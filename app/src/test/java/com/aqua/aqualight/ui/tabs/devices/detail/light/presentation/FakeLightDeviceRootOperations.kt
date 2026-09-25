package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeLightDeviceRootOperations(
    initialAvailability: OwnerDeviceAvailability = OwnerDeviceAvailability.REACHABLE
) : DeviceRootOperations {
    private val state = MutableStateFlow<DeviceRootSnapshot?>(
        snapshot(initialAvailability)
    )

    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = state

    override fun current(deviceUid: String): DeviceRootSnapshot? = state.value

    override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

    override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true

    fun setAvailability(availability: OwnerDeviceAvailability) {
        state.value = snapshot(availability)
    }

    private fun snapshot(availability: OwnerDeviceAvailability) = DeviceRootSnapshot(
        deviceUid = DEVICE_UID,
        title = "Light",
        availability = availability,
        family = OwnerDeviceFamily.LIGHT,
        catalogState = DeviceRootCatalogState.VALID
    )

    private companion object {
        const val DEVICE_UID = "device-1"
    }
}
