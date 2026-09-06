package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily

/**
 * Keeps production preparation for real devices and routes fixture UIDs to the fixture-backed
 * control surface. The same instance is shared by the device list and destination ViewModel so
 * one-shot preparation handoffs retain their normal production semantics.
 */
internal class DebugFixtureControlSurfacePreparationOperations(
    private val delegate: DeviceControlSurfacePreparationOperations,
    private val fixtureDelegate: DeviceControlSurfacePreparationOperations,
    private val fixtures: DebugDeviceFixtureCatalog
) : DeviceControlSurfacePreparationOperations {

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult = operationsFor(request.deviceUid).prepare(request)

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean = operationsFor(deviceUid).consumeFreshPreparation(deviceUid, family)

    override fun discardFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ) {
        operationsFor(deviceUid).discardFreshPreparation(deviceUid, family)
    }

    private fun operationsFor(deviceUid: String): DeviceControlSurfacePreparationOperations =
        if (fixtures.contains(deviceUid)) fixtureDelegate else delegate
}
