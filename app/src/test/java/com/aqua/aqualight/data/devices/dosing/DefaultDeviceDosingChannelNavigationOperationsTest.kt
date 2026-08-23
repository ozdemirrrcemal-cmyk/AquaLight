package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelDestination
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultDeviceDosingChannelNavigationOperationsTest {

    @Test
    fun `current navigation refreshes and ignores an older observed calibration state`() = runTest {
        val stale = channelSnapshot(calibrated = false, lastCalibratedAt = 0L)
        val current = channelSnapshot(calibrated = true, lastCalibratedAt = CALIBRATED_AT)
        val channels = FakeChannelOperations(
            observed = stale,
            refreshed = DeviceDosingChannelOperationResult.Success(current)
        )
        val operations = DefaultDeviceDosingChannelNavigationOperations(
            rootOperations = FakeRootOperations(rootSnapshot(channelCount = 2)),
            channelOperations = channels
        )

        val target = operations.resolveCurrent(" $DEVICE_UID ", " $SLOT_ID ")

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CALIBRATED_AT, target?.lastCalibratedAtEpochSeconds)
        assertEquals(1, channels.refreshCount)
        assertEquals(DEVICE_UID, channels.refreshedDeviceUid)
        assertEquals(SLOT_ID, channels.refreshedSlotId)
    }

    @Test
    fun `validated current presentation resolves without a firmware refresh`() = runTest {
        val validated = channelSnapshot(
            calibrated = true,
            lastCalibratedAt = CALIBRATED_AT
        )
        val channels = FakeChannelOperations(
            observed = validated,
            refreshed = DeviceDosingChannelOperationResult.Failed,
            validatedPresentation = validated
        )
        val operations = DefaultDeviceDosingChannelNavigationOperations(
            rootOperations = FakeRootOperations(rootSnapshot(channelCount = 2)),
            channelOperations = channels
        )

        val target = operations.resolveCurrent(DEVICE_UID, SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CALIBRATED_AT, target?.lastCalibratedAtEpochSeconds)
        assertEquals(0, channels.refreshCount)
    }

    @Test
    fun `durable route snapshot resolves without another firmware refresh`() = runTest {
        val durableRoute = channelSnapshot(
            calibrated = true,
            lastCalibratedAt = CALIBRATED_AT
        )
        val channels = FakeChannelOperations(
            observed = durableRoute,
            refreshed = DeviceDosingChannelOperationResult.Failed,
            navigationSnapshot = durableRoute
        )
        val operations = DefaultDeviceDosingChannelNavigationOperations(
            rootOperations = FakeRootOperations(rootSnapshot(channelCount = 2)),
            channelOperations = channels
        )

        val target = operations.resolveCurrent(DEVICE_UID, SLOT_ID)

        assertEquals(DeviceDosingChannelDestination.DETAIL, target?.destination)
        assertEquals(CALIBRATED_AT, target?.lastCalibratedAtEpochSeconds)
        assertEquals(0, channels.refreshCount)
    }

    @Test
    fun `uncalibrated four channel product resolves only through authorized calibration route`() =
        runTest {
            val channel = channelSnapshot(
                pumpCount = 4,
                channelNumber = 4,
                slotId = "dosing:channel4",
                calibrated = false,
                lastCalibratedAt = 0L
            )
            val operations = DefaultDeviceDosingChannelNavigationOperations(
                rootOperations = FakeRootOperations(rootSnapshot(channelCount = 4)),
                channelOperations = FakeChannelOperations(
                    observed = channel,
                    refreshed = DeviceDosingChannelOperationResult.Success(channel)
                )
            )

            val target = operations.resolveCurrent(DEVICE_UID, "dosing:channel4")

            assertEquals(DeviceDosingChannelDestination.CALIBRATION, target?.destination)
            assertEquals(4, target?.pumpCount)
            assertEquals(4, target?.channelNumber)
        }

    @Test
    fun `navigation fails closed when root authorization or snapshot identity no longer matches`() =
        runTest {
            val channel = channelSnapshot(calibrated = true, lastCalibratedAt = CALIBRATED_AT)
            val unauthorized = DefaultDeviceDosingChannelNavigationOperations(
                rootOperations = FakeRootOperations(
                    rootSnapshot(channelCount = 2, allowedRoutes = emptySet())
                ),
                channelOperations = FakeChannelOperations(
                    observed = channel,
                    refreshed = DeviceDosingChannelOperationResult.Success(channel)
                )
            )
            val mismatched = DefaultDeviceDosingChannelNavigationOperations(
                rootOperations = FakeRootOperations(rootSnapshot(channelCount = 2)),
                channelOperations = FakeChannelOperations(
                    observed = channel,
                    refreshed = DeviceDosingChannelOperationResult.Success(
                        channel.copy(channelNumber = 2)
                    )
                )
            )

            assertNull(unauthorized.resolveCurrent(DEVICE_UID, SLOT_ID))
            assertNull(mismatched.resolveCurrent(DEVICE_UID, SLOT_ID))
        }

    private class FakeRootOperations(
        private val snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeChannelOperations(
        private val observed: DeviceDosingChannelSnapshot,
        private val refreshed: DeviceDosingChannelOperationResult,
        private val validatedPresentation: DeviceDosingChannelSnapshot? = null,
        private val navigationSnapshot: DeviceDosingChannelSnapshot? = validatedPresentation
    ) : DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        var refreshCount: Int = 0
        var refreshedDeviceUid: String = ""
        var refreshedSlotId: String = ""

        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingChannelSnapshot?> = flowOf(observed)

        override fun currentValidatedPresentation(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelSnapshot? = validatedPresentation

        override fun currentNavigationSnapshot(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelSnapshot? = navigationSnapshot

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult {
            refreshCount += 1
            refreshedDeviceUid = deviceUid
            refreshedSlotId = slotId
            return refreshed
        }
    }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel1"
        const val CALIBRATED_AT = 1_786_320_000L
    }
}

private fun rootSnapshot(
    channelCount: Int,
    allowedRoutes: Set<DeviceRootRoute> = setOf(
        DeviceRootRoute.DOSING_CHANNELS,
        DeviceRootRoute.DOSING_CALIBRATION
    )
) = DeviceRootSnapshot(
    deviceUid = "device-1",
    title = "Dose Pro",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.DOSING,
    catalogState = DeviceRootCatalogState.VALID,
    channelSlots = dosingChannelSlots(channelCount),
    allowedRoutes = allowedRoutes
)

private fun dosingChannelSlots(channelCount: Int) = DeviceChannelSlots(
    lightChannels = emptyList(),
    timerChannels = emptyList(),
    dosingChannels = List(channelCount) { index ->
        DeviceDosingChannelSlot(
            index = DeviceSlotIndex(index),
            wireKey = DeviceChannelWireKey("channel${index + 1}"),
            defaultDisplayName = "Channel ${index + 1}",
            displayNameEditable = true
        )
    },
    fanOutputs = emptyList(),
    temperatureSensors = emptyList()
)

private fun channelSnapshot(
    pumpCount: Int = 2,
    channelNumber: Int = 1,
    slotId: String = "dosing:channel1",
    calibrated: Boolean,
    lastCalibratedAt: Long
) = DeviceDosingChannelSnapshot(
    deviceUid = "device-1",
    slotId = slotId,
    pumpCount = pumpCount,
    channelNumber = channelNumber,
    channelTitle = "Macro",
    revision = 7L,
    runtimeEnabled = true,
    runtimeReason = DeviceDosingRuntimeReason.NONE,
    deliveryAccountingCertain = true,
    calibrated = calibrated,
    lastCalibratedAtEpochSeconds = lastCalibratedAt,
    scheduling = DeviceDosingSchedulingPolicy(),
    program = null,
    progress = DeviceDosingChannelProgress(),
    reservoir = DeviceDosingReservoirSnapshot(),
    activeRun = DeviceDosingActiveRun(),
    controls = DeviceDosingChannelControls()
)
