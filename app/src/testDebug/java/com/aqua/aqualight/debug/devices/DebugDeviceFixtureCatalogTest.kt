package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelNavigationTarget
import com.aqua.aqualight.application.devices.DeviceMenuAccessOperations
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import kotlinx.coroutines.flow.first
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
    fun fixtureDosingDeviceExposesCalibrationAndDetailWithoutRuntimeDelegate() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val dosingRoot = fixtures.snapshots
            .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
            .first { root -> root.channelSlots.dosingChannels.size >= 2 }
        val stateStore = DebugFixtureDosingStateStore(fixtures)
        val operations = DebugFixtureDosingChannelNavigationOperations(
            delegate = object : DeviceDosingChannelNavigationOperations {
                override suspend fun resolve(
                    deviceUid: String,
                    slotId: String
                ): DeviceDosingChannelNavigationTarget? {
                    error("Fixture navigation must not call the physical runtime delegate.")
                }
            },
            fixtures = fixtures,
            stateStore = stateStore
        )

        val firstChannel = dosingRoot.channelSlots.dosingChannels[0]
        val secondChannel = dosingRoot.channelSlots.dosingChannels[1]
        val firstTarget = requireNotNull(
            operations.resolve(dosingRoot.deviceUid, firstChannel.id.value)
        )
        val secondTarget = requireNotNull(
            operations.resolve(dosingRoot.deviceUid, secondChannel.id.value)
        )
        val observedTargets = operations.observeTargets(dosingRoot.deviceUid).first()

        assertEquals(DeviceDosingChannelDestination.CALIBRATION, firstTarget.destination)
        assertEquals(DeviceDosingChannelDestination.DETAIL, secondTarget.destination)
        assertEquals(0L, firstTarget.lastCalibratedAtEpochSeconds)
        assertTrue(secondTarget.lastCalibratedAtEpochSeconds in 1L..MAX_EPOCH_SECONDS)
        assertEquals(
            setOf(
                DeviceDosingChannelDestination.CALIBRATION,
                DeviceDosingChannelDestination.DETAIL
            ),
            observedTargets.map { target -> target.destination }.toSet()
        )
    }

    @Test
    fun fixtureCalibrationCompletionPromotesChannelToDetail() = runTest {
        val fixtures = DebugDeviceFixtureCatalog()
        val dosingRoot = fixtures.snapshots
            .mapNotNull { snapshot -> fixtures.rootSnapshot(snapshot.deviceUid.value) }
            .first { root -> root.channelSlots.dosingChannels.isNotEmpty() }
        val channel = dosingRoot.channelSlots.dosingChannels.first()
        val stateStore = DebugFixtureDosingStateStore(fixtures)
        val operations = DebugFixtureDosingChannelNavigationOperations(
            delegate = DeviceDosingChannelNavigationOperations { _, _ -> null },
            fixtures = fixtures,
            stateStore = stateStore
        )

        assertTrue(stateStore.start(dosingRoot.deviceUid, channel.id.value) is DeviceDosingCalibrationResult.Success)
        assertTrue(
            stateStore.finish(dosingRoot.deviceUid, channel.id.value, 4.0) is
                DeviceDosingCalibrationResult.Success
        )
        assertTrue(
            stateStore.startVerificationDose(dosingRoot.deviceUid, channel.id.value) is
                DeviceDosingCalibrationResult.Success
        )
        assertTrue(
            stateStore.refresh(dosingRoot.deviceUid, channel.id.value) is
                DeviceDosingCalibrationResult.Success
        )
        assertTrue(
            stateStore.confirm(dosingRoot.deviceUid, channel.id.value) is
                DeviceDosingCalibrationResult.Success
        )

        val result = operations.resolve(dosingRoot.deviceUid, channel.id.value)

        requireNotNull(result)
        assertEquals(DeviceDosingChannelDestination.DETAIL, result.destination)
        assertTrue(result.lastCalibratedAtEpochSeconds in 1L..MAX_EPOCH_SECONDS)
    }

    @Test
    fun realDeviceDosingChannelStillDelegatesToProductionBoundary() = runTest {
        val expected = DeviceDosingChannelNavigationTarget(
            deviceUid = "REAL-DEVICE-001",
            slotId = "dosing:channel1",
            pumpCount = 2,
            channelNumber = 1,
            channelTitle = "Nutrients",
            lastCalibratedAtEpochSeconds = 1L,
            destination = DeviceDosingChannelDestination.DETAIL
        )
        val fixtures = DebugDeviceFixtureCatalog()
        val operations = DebugFixtureDosingChannelNavigationOperations(
            delegate = DeviceDosingChannelNavigationOperations { _, _ -> expected },
            fixtures = fixtures,
            stateStore = DebugFixtureDosingStateStore(fixtures)
        )

        val result = operations.resolve(expected.deviceUid, expected.slotId)

        assertSame(expected, result)
    }

    private companion object {
        const val MAX_EPOCH_SECONDS = 0xFFFF_FFFFL
    }
}
