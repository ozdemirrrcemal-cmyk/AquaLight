package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.light.quicksetup.mapDeviceLightQuickSetupTank
import com.aqua.aqualight.data.devices.model.DeviceFamily
import java.util.concurrent.CancellationException

/** Resolves quick-setup tank data for debug Light fixtures from their in-process assignment. */
internal class DebugFixtureLightQuickSetupTankOperations(
    private val delegate: DeviceLightQuickSetupTankOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val assignments: DebugFixtureTankAssignmentRuntime,
    private val tankById: suspend (Long) -> SavedAquariumTank?
) : DeviceLightQuickSetupTankOperations {

    override suspend fun readForDevice(deviceUid: String): DeviceLightQuickSetupTankReadResult {
        val normalizedUid = deviceUid.trim()
        val fixture = fixtures.snapshot(normalizedUid)
            ?: return delegate.readForDevice(deviceUid)
        if (fixture.product.family != DeviceFamily.LIGHT) {
            return failed(DeviceLightQuickSetupTankFailure.INVALID_DEVICE)
        }
        val tankId = assignments.tankIdForDevice(normalizedUid)
            ?: return failed(DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED)
        return try {
            val tank = tankById(tankId)
                ?: return failed(DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND)
            DeviceLightQuickSetupTankReadResult.Available(
                mapDeviceLightQuickSetupTank(tank = tank, device = fixture)
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed(DeviceLightQuickSetupTankFailure.UNAVAILABLE)
        }
    }
}

private fun failed(
    failure: DeviceLightQuickSetupTankFailure
): DeviceLightQuickSetupTankReadResult = DeviceLightQuickSetupTankReadResult.Failed(failure)
