package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugDeviceFixtureCatalogTest {

    @Test
    fun fixturesCoverEveryCommercialProductExactlyOnce() {
        val fixtures = DebugDeviceFixtureCatalog()
        val expectedProductKeys = AqlCommercialDeviceCatalog.products
            .map { product -> product.productKey.value }
        val actualProductKeys = fixtures.snapshots
            .map { snapshot -> snapshot.product.productKey }

        assertEquals(9, expectedProductKeys.size)
        assertEquals(expectedProductKeys, actualProductKeys)
        assertEquals(expectedProductKeys.toSet().size, actualProductKeys.toSet().size)

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

        assertEquals(9, items.size)
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

    @Test
    fun fixtureDosingChannelOpensCalibrationWithoutCallingRuntimeDelegate() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val dosingRoot = fixtures.snapshots
            .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
            .first { root -> root.channelSlots.dosingChannels.isNotEmpty() }
        val channel = dosingRoot.channelSlots.dosingChannels.first()
        val operations = DebugFixtureDosingChannelNavigationOperations(
            delegate = object : DeviceDosingChannelNavigationOperations {
                override suspend fun resolve(
                    deviceUid: String,
                    slotId: String
                ): DeviceDosingChannelNavigationTarget? {
                    error("Fixture navigation must not call the physical runtime delegate.")
                }
            },
            fixtures = fixtures
        )

        val result = operations.resolve(dosingRoot.deviceUid, channel.id.value)

        requireNotNull(result)
        assertEquals(DeviceDosingChannelDestination.CALIBRATION, result.destination)
        assertEquals(channel.id.value, result.slotId)
        assertEquals(channel.defaultDisplayName, result.channelTitle)
    }

    @Test
    fun realDeviceDosingChannelStillDelegatesToProductionBoundary() = runTest {
        val expected = DeviceDosingChannelNavigationTarget(
            deviceUid = "REAL-DEVICE-001",
            slotId = "dosing:channel1",
            channelTitle = "Nutrients",
            destination = DeviceDosingChannelDestination.DETAIL
        )
        val operations = DebugFixtureDosingChannelNavigationOperations(
            delegate = DeviceDosingChannelNavigationOperations { _, _ -> expected },
            fixtures = DebugDeviceFixtureCatalog()
        )

        val result = operations.resolve(expected.deviceUid, expected.slotId)

        assertSame(expected, result)
    }
}
