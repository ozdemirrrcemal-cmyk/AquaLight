package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupInput
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPersistenceResult
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupPlan
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupRecordedOutcome
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankFailure
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankOperations
import com.aqua.aqualight.application.devices.light.quicksetup.DeviceLightQuickSetupTankReadResult
import com.aqua.aqualight.data.aquarium.devices.TankDeviceAssignment
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import com.aqua.aqualight.data.devices.light.quicksetup.mapDeviceLightQuickSetupTank
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import java.time.LocalDate
import java.util.concurrent.CancellationException

/** Resolves quick-setup tank data for debug Light fixtures from their in-process assignment. */
internal class DebugFixtureLightQuickSetupTankOperations(
    private val delegate: DeviceLightQuickSetupTankOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val assignments: DebugFixtureTankAssignmentRuntime,
    private val tankById: suspend (Long) -> SavedAquariumTank?,
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() }
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
            fixture.fixtureLengthMmOrNull() == null ->
                failed(DeviceLightQuickSetupTankFailure.DEVICE_RUNTIME_UNVERIFIED)
            tankId == null -> failed(DeviceLightQuickSetupTankFailure.TANK_NOT_ASSIGNED)
            else -> readAssignedFixture(tankId, fixture)
        }
    }

    private suspend fun readAssignedFixture(
        tankId: Long,
        fixture: DeviceSnapshot
    ): DeviceLightQuickSetupTankReadResult = try {
        tankById(tankId)?.let { tank ->
            val today = todayEpochDay()
            DeviceLightQuickSetupTankReadResult.Available(
                mapDeviceLightQuickSetupTank(
                    tank = tank,
                    assignment = TankDeviceAssignment(
                        ownerUid = DEBUG_OWNER_UID,
                        tankId = tank.id,
                        deviceUid = fixture.deviceUid,
                        assignedAtMillis = System.currentTimeMillis()
                    ),
                    device = fixture,
                    fixtureLengthMm = requireNotNull(fixture.fixtureLengthMmOrNull()),
                    deviceLocalEpochDay = today,
                    installedPlanId = null,
                    installedPlanRevision = 0L
                )
            )
        } ?: failed(DeviceLightQuickSetupTankFailure.TANK_NOT_FOUND)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        failed(DeviceLightQuickSetupTankFailure.UNAVAILABLE)
    }

    override suspend fun prepareRecommendation(
        deviceUid: String,
        input: DeviceLightQuickSetupInput,
        plan: DeviceLightQuickSetupPlan
    ): DeviceLightQuickSetupPersistenceResult = if (fixtures.contains(deviceUid)) {
        DeviceLightQuickSetupPersistenceResult.Prepared(plan.recommendationId)
    } else {
        delegate.prepareRecommendation(deviceUid, input, plan)
    }

    override suspend fun recordRecommendationOutcome(
        deviceUid: String,
        auditId: String,
        outcome: DeviceLightQuickSetupRecordedOutcome
    ): DeviceLightQuickSetupPersistenceResult = if (fixtures.contains(deviceUid)) {
        DeviceLightQuickSetupPersistenceResult.Saved
    } else {
        delegate.recordRecommendationOutcome(deviceUid, auditId, outcome)
    }
}

private fun DeviceSnapshot.fixtureLengthMmOrNull(): Int? = when (product.productKey) {
    WRGB_PRO_ELITE_PRODUCT_KEY -> WRGB_PRO_ELITE_FIXTURE_LENGTH_MM
    else -> null
}

private fun failed(
    failure: DeviceLightQuickSetupTankFailure
): DeviceLightQuickSetupTankReadResult = DeviceLightQuickSetupTankReadResult.Failed(failure)

private const val DEBUG_OWNER_UID = "debug-fixture-owner"
private const val WRGB_PRO_ELITE_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
private const val WRGB_PRO_ELITE_FIXTURE_LENGTH_MM = 1_200
