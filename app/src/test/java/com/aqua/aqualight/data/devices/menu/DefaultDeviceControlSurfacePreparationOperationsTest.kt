package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceControlSurfacePreparationOperationsTest {

    @Test
    fun `cold Dose Pro refreshes complete central state before ready`() = runTest {
        val channels = FakeChannelOperations(
            refreshResult = true,
            refreshedSnapshots = snapshots(4)
        )
        val operations = preparation(channelCount = 4, channels = channels)

        val result = operations.prepare(request())

        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)
        assertEquals(1, channels.refreshCalls)
        assertEquals(snapshots(4), channels.currentAuthoritativeSnapshots())
        assertTrue(operations.consumeFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING))
        assertFalse(operations.consumeFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING))
    }

    @Test
    fun `warm Dose Pro still refreshes at menu freshness boundary`() = runTest {
        val initial = snapshots(4)
        val channels = FakeChannelOperations(
            initialSnapshots = initial,
            initialAuthoritativeSnapshots = initial,
            refreshedSnapshots = initial
        )
        val operations = preparation(channelCount = 4, channels = channels)

        val result = operations.prepare(request())

        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)
        assertEquals(1, channels.refreshCalls)
        assertTrue(operations.consumeFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING))
    }

    @Test
    fun `presentation only state cannot bypass authoritative refresh`() = runTest {
        val presentation = snapshots(4)
        val channels = FakeChannelOperations(
            initialSnapshots = presentation,
            initialAuthoritativeSnapshots = emptyList(),
            refreshedSnapshots = presentation
        )
        val operations = preparation(channelCount = 4, channels = channels)

        assertEquals(presentation, channels.currentPresentationSnapshots())
        assertTrue(channels.currentAuthoritativeSnapshots().isEmpty())

        val result = operations.prepare(request())

        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)
        assertEquals(1, channels.refreshCalls)
        assertEquals(presentation, channels.currentAuthoritativeSnapshots())
    }

    @Test
    fun `abandoned preparation handoff is discarded idempotently`() = runTest {
        val channels = FakeChannelOperations(
            refreshResult = true,
            refreshedSnapshots = snapshots(4)
        )
        val operations = preparation(channelCount = 4, channels = channels)

        val result = operations.prepare(request())
        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)

        operations.discardFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING)
        operations.discardFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING)

        assertFalse(operations.consumeFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING))
    }

    @Test
    fun `refresh failure keeps navigation unavailable`() = runTest {
        val channels = FakeChannelOperations(refreshResult = false)
        val operations = preparation(channelCount = 4, channels = channels)

        val result = operations.prepare(request())

        assertTrue(result is DeviceControlSurfacePreparationResult.Unavailable)
        assertEquals(1, channels.refreshCalls)
        assertFalse(operations.consumeFreshPreparation(DEVICE_UID, OwnerDeviceFamily.DOSING))
    }

    @Test
    fun `partial authoritative refresh cannot publish ready surface`() = runTest {
        val channels = FakeChannelOperations(
            refreshResult = true,
            refreshedSnapshots = snapshots(4).dropLast(1)
        )
        val operations = preparation(channelCount = 4, channels = channels)

        val result = operations.prepare(request())

        assertTrue(result is DeviceControlSurfacePreparationResult.Unavailable)
        assertEquals(1, channels.refreshCalls)
    }

    @Test
    fun `validated Cool Pro creates a family scoped preparation handoff`() = runTest {
        val operations = DefaultDeviceControlSurfacePreparationOperations(
            rootOperations = FakeRootOperations(coolingRootSnapshot()),
            dosingChannelOperations = FakeChannelOperations()
        )

        val result = operations.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = COOLING_DEVICE_UID,
                family = OwnerDeviceFamily.COOLING
            )
        )

        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)
        assertTrue(
            operations.consumeFreshPreparation(
                COOLING_DEVICE_UID,
                OwnerDeviceFamily.COOLING
            )
        )
        assertFalse(
            operations.consumeFreshPreparation(
                COOLING_DEVICE_UID,
                OwnerDeviceFamily.COOLING
            )
        )
    }

    private fun preparation(
        channelCount: Int,
        channels: FakeChannelOperations
    ) = DefaultDeviceControlSurfacePreparationOperations(
        rootOperations = FakeRootOperations(rootSnapshot(channelCount)),
        dosingChannelOperations = channels
    )

    private fun request() = DeviceControlSurfacePreparationRequest(
        deviceUid = DEVICE_UID,
        family = OwnerDeviceFamily.DOSING
    )

    private class FakeRootOperations(
        private val snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> =
            MutableStateFlow(snapshot)

        override fun current(deviceUid: String): DeviceRootSnapshot = snapshot

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeChannelOperations(
        initialSnapshots: List<DeviceDosingChannelSnapshot> = emptyList(),
        initialAuthoritativeSnapshots: List<DeviceDosingChannelSnapshot> = initialSnapshots,
        private val refreshResult: Boolean = true,
        private val refreshedSnapshots: List<DeviceDosingChannelSnapshot> = initialSnapshots
    ) : DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        private val presentationSnapshots = MutableStateFlow(initialSnapshots)
        private var authoritativeSnapshots = initialAuthoritativeSnapshots
        var refreshCalls: Int = 0

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            presentationSnapshots

        override fun current(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
            authoritativeSnapshots.singleOrNull { snapshot ->
                snapshot.deviceUid == deviceUid && snapshot.slotId == slotId
            }

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshCalls += 1
            if (refreshResult) {
                presentationSnapshots.value = refreshedSnapshots
                authoritativeSnapshots = refreshedSnapshots
            }
            return refreshResult
        }

        fun currentPresentationSnapshots(): List<DeviceDosingChannelSnapshot> =
            presentationSnapshots.value

        fun currentAuthoritativeSnapshots(): List<DeviceDosingChannelSnapshot> =
            authoritativeSnapshots
    }

    private companion object {
        const val DEVICE_UID = "dose-pro-4"
        const val COOLING_DEVICE_UID = "cool-pro-3f"
    }
}

private fun coolingRootSnapshot() = DeviceRootSnapshot(
    deviceUid = "cool-pro-3f",
    title = "Cool Pro 3 Fan",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.COOLING,
    catalogState = DeviceRootCatalogState.VALID,
    fanOutputCount = 3,
    temperatureSensorCount = 1
)

private fun rootSnapshot(channelCount: Int) = DeviceRootSnapshot(
    deviceUid = "dose-pro-4",
    title = "Dose Pro 4",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.DOSING,
    catalogState = DeviceRootCatalogState.VALID,
    dosingChannelCount = channelCount,
    channelSlots = DeviceChannelSlots(
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
)

private fun snapshots(count: Int): List<DeviceDosingChannelSnapshot> =
    (1..count).map { channelNumber ->
        DeviceDosingChannelSnapshot(
            deviceUid = "dose-pro-4",
            slotId = "dosing:channel$channelNumber",
            pumpCount = count,
            channelNumber = channelNumber,
            channelTitle = "Channel $channelNumber",
            revision = 1L,
            runtimeEnabled = true,
            runtimeReason = DeviceDosingRuntimeReason.NONE,
            deliveryAccountingCertain = true,
            calibrated = true,
            lastCalibratedAtEpochSeconds = 1L,
            scheduling = DeviceDosingSchedulingPolicy(),
            program = null,
            progress = DeviceDosingChannelProgress(),
            reservoir = DeviceDosingReservoirSnapshot(),
            activeRun = DeviceDosingActiveRun(),
            controls = DeviceDosingChannelControls(),
            usageToday = DeviceDosingDailyUsageSnapshot()
        )
    }
