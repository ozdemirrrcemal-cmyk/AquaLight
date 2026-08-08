package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.data.devices.model.DeviceFamily
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugDeviceFixtureCatalogTest {

    @Test
    fun fixturesRemainCatalogValidatedForEverySupportedFamily() {
        val fixtures = DebugDeviceFixtureCatalog()

        assertEquals(
            setOf(
                DeviceFamily.LIGHT,
                DeviceFamily.TIMER,
                DeviceFamily.DOSING,
                DeviceFamily.COOLING
            ),
            fixtures.snapshots.map { snapshot -> snapshot.product.family }.toSet()
        )
        fixtures.snapshots.forEach { snapshot ->
            assertTrue(snapshot.runtimeMetadataGeneration > 0L)
            val root = requireNotNull(fixtures.rootSnapshot(snapshot.deviceUid.value))
            assertEquals(DeviceRootCatalogState.VALID, root.catalogState)
            assertTrue(root.allowedRoutes.isNotEmpty())
        }
    }

    @Test
    fun fixtureCardsAreClearlyMarkedAndReachable() {
        val items = DebugDeviceFixtureCatalog().listItems()

        assertEquals(4, items.size)
        items.forEach { item ->
            assertTrue(item.deviceUid.startsWith("DEBUG-FIXTURE-"))
            assertTrue(item.displayName.endsWith("[TEST]"))
            assertEquals(OwnerDeviceAvailability.REACHABLE, item.availability)
        }
    }

    @Test
    fun fixtureMenuAccessDoesNotCallPhysicalLivenessDelegate() = runTest {
        var delegateCalled = false
        val delegate = object : DeviceMenuAccessOperations {
            override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult {
                delegateCalled = true
                error("Fixture access must not call the physical liveness delegate.")
            }
        }
        val fixtures = DebugDeviceFixtureCatalog()
        val operations = DebugFixtureMenuAccessOperations(delegate, fixtures)

        fixtures.snapshots.forEach { snapshot ->
            val result = operations.resolve(snapshot.deviceUid.value)
            assertTrue(result is DeviceMenuAccessResult.Available)
        }
        assertFalse(delegateCalled)
    }

    @Test
    fun realDeviceMenuAccessStillDelegatesToProductionBoundary() = runTest {
        val expected = DeviceMenuAccessResult.Unavailable(
            title = "Real device",
            reason = DeviceMenuUnavailableReason.DEVICE_UNRESPONSIVE
        )
        val delegate = object : DeviceMenuAccessOperations {
            override suspend fun resolve(deviceUid: String): DeviceMenuAccessResult = expected
        }
        val operations = DebugFixtureMenuAccessOperations(
            delegate = delegate,
            fixtures = DebugDeviceFixtureCatalog()
        )

        val result = operations.resolve("REAL-DEVICE-001")

        assertSame(expected, result)
    }
}
