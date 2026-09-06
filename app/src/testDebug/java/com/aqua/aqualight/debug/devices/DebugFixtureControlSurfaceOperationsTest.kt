package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuOpenResult
import com.aqua.aqualight.application.devices.DeviceMenuOpenUseCase
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.data.devices.menu.DefaultDeviceControlSurfacePreparationOperations
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugFixtureControlSurfaceOperationsTest {

    @Test
    fun fixtureMenuUsesFixturePreparationAndRetainsOneShotHandoff() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val fixturePreparation = DefaultDeviceControlSurfacePreparationOperations(
            rootOperations = DebugFixtureDeviceRootOperations(NoDeviceRootOperations, fixtures),
            dosingChannelOperations = UnavailableDeviceDosingChannelOperations
        )
        val productionPreparation = RecordingPreparationOperations(
            result = unavailableResult(),
            freshPreparation = false
        )
        val operations = DebugFixtureControlSurfacePreparationOperations(
            delegate = productionPreparation,
            fixtureDelegate = fixturePreparation,
            fixtures = fixtures
        )
        val menuOpen = DeviceMenuOpenUseCase(
            menuAccessOperations = DebugFixtureMenuAccessOperations(
                delegate = FailingMenuAccessOperations,
                fixtures = fixtures
            ),
            controlSurfacePreparationOperations = operations
        )
        val fixtureUid = fixtures.coolingFixtureUid()

        val result = menuOpen.resolve(fixtureUid)

        assertTrue(result is DeviceMenuOpenResult.Ready)
        assertEquals(0, productionPreparation.prepareCalls)
        assertTrue(operations.consumeFreshPreparation(fixtureUid, OwnerDeviceFamily.COOLING))
        assertFalse(operations.consumeFreshPreparation(fixtureUid, OwnerDeviceFamily.COOLING))
    }

    @Test
    fun realDevicePreparationStillUsesProductionBoundary() = runTest {
        val productionPreparation = RecordingPreparationOperations(
            result = unavailableResult(),
            freshPreparation = false
        )
        val fixturePreparation = RecordingPreparationOperations(
            result = DeviceControlSurfacePreparationResult.Ready,
            freshPreparation = true
        )
        val operations = DebugFixtureControlSurfacePreparationOperations(
            delegate = productionPreparation,
            fixtureDelegate = fixturePreparation,
            fixtures = DebugDeviceFixtureCatalog()
        )

        val result = operations.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = REAL_DEVICE_UID,
                family = OwnerDeviceFamily.COOLING
            )
        )

        assertTrue(result is DeviceControlSurfacePreparationResult.Unavailable)
        assertEquals(1, productionPreparation.prepareCalls)
        assertEquals(0, fixturePreparation.prepareCalls)
    }

}

private class RecordingPreparationOperations(
    private val result: DeviceControlSurfacePreparationResult,
    private val freshPreparation: Boolean
) : DeviceControlSurfacePreparationOperations {
    var prepareCalls = 0
    var consumeCalls = 0

    override suspend fun prepare(
        request: DeviceControlSurfacePreparationRequest
    ): DeviceControlSurfacePreparationResult {
        prepareCalls += 1
        return result
    }

    override fun consumeFreshPreparation(
        deviceUid: String,
        family: OwnerDeviceFamily
    ): Boolean {
        consumeCalls += 1
        return freshPreparation
    }
}

private data object FailingMenuAccessOperations : DeviceMenuAccessOperations {
    override suspend fun resolve(deviceUid: String) = error(
        "Fixture menu access must not call production."
    )
}

private data object NoDeviceRootOperations : DeviceRootOperations {
    override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = flowOf(null)

    override fun current(deviceUid: String): DeviceRootSnapshot? = null

    override fun connect(deviceUid: String): Result<Unit> = Result.failure(
        IllegalStateException("Production root must not be used for a fixture UID.")
    )
}

private fun DebugDeviceFixtureCatalog.coolingFixtureUid(): String = snapshots
    .single { snapshot -> snapshot.product.family == DeviceFamily.COOLING }
    .deviceUid
    .value

private fun unavailableResult() = DeviceControlSurfacePreparationResult.Unavailable(
    DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
)

private const val REAL_DEVICE_UID = "REAL-DEVICE-001"
