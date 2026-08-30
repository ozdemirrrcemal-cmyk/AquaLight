package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceControlSurfacePreparationOperations

/** Keeps installable-debug fixtures on the same central preparation contract as real devices. */
internal class DebugFixtureControlSurfacePreparationOperations(
    private val delegate: DeviceControlSurfacePreparationOperations,
    rootOperations: DebugFixtureDeviceRootOperations,
    dosingChannelOperations: DeviceDosingChannelOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceControlSurfacePreparationOperations {

    private val fixtureDelegate = DefaultDeviceControlSurfacePreparationOperations(
        rootOperations = rootOperations,
        dosingChannelOperations = dosingChannelOperations
    )

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = operation(request.deviceUid).prepare(request)

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = operation(deviceUid).consumeFreshPreparation(deviceUid, family)

    override fun discardFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ) {
        operation(deviceUid).discardFreshPreparation(deviceUid, family)
    }

    private fun operation(deviceUid: String): DeviceControlSurfacePreparationOperations =
        if (fixtures.contains(deviceUid)) fixtureDelegate else delegate
}
