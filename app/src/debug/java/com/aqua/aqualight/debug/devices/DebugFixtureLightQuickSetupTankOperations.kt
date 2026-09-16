package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.light.quicksetup.mapDeviceLightQuickSetupTank
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
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
        return if (fixture == null) {
            delegate.readForDevice(deviceUid)
        } else {
            readFixture(normalizedUid, fixture)
        }
    }

    private suspend fun readFixture(
        normalizedUid: String,
        fixture: DeviceSnapshot
    ): DeviceLightQuickSetupTankReadResult {
        val tankId = assignments.tankIdForDevice(normalizedUid)
        return when {
            fixture.product.family != DeviceFamily.LIGHT ->
                failed(DeviceLightQuickSetupTankFailure.INVALID_DEVICE)
            tankId == null -> failed(DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED)
            else -> readAssignedFixture(tankId, fixture)
        }
    }

    private suspend fun readAssignedFixture(
        tankId: Long,
        fixture: DeviceSnapshot
    ): DeviceLightQuickSetupTankReadResult = try {
        tankById(tankId)?.let { tank ->
            DeviceLightQuickSetupTankReadResult.Available(
                mapDeviceLightQuickSetupTank(tank = tank, device = fixture)
            )
        } ?: failed(DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(DeviceLightQuickSetupTankFailure.UNAVAILABLE)
    }
}

private fun failed(
    failure: DeviceLightQuickSetupTankFailure
): DeviceLightQuickSetupTankReadResult = DeviceLightQuickSetupTankReadResult.Failed(failure)
