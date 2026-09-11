@file:Suppress("MagicNumber")

package com.aqua.aqualight.data.devices.menu

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceFanOutputSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.DeviceTemperatureSensorSlot
import com.aqua.aqualight.application.devices.DeviceTimerChannelSlot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelRegime
import com.aqua.aqualight.application.devices.timer.DeviceTimerChannelSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlCapabilities
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlFailure
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlOperations
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlResult
import com.aqua.aqualight.application.devices.timer.DeviceTimerControlSnapshot
import com.aqua.aqualight.application.devices.timer.DeviceTimerDisplayNameUpdate
import com.aqua.aqualight.application.devices.timer.DeviceTimerNextTransitionType
import com.aqua.aqualight.application.devices.timer.DeviceTimerOperatingState
import com.aqua.aqualight.application.devices.timer.DeviceTimerOutputHealth
import com.aqua.aqualight.application.devices.timer.DeviceTimerRuntimeReason
import com.aqua.aqualight.application.devices.timer.DeviceTimerScheduleDraft
import com.aqua.aqualight.data.devices.cooling.DisconnectedDeviceCoolingControlOperations
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
    fun `Cooling menu refreshes central authoritative state before ready`() = runTest {
        val cooling = FakeCoolingControlOperations(availableCoolingControl())
        val operations = DefaultDeviceControlSurfacePreparationOperations(
            rootOperations = FakeRootOperations(coolingRootSnapshot()),
            dosingChannelOperations = FakeChannelOperations(),
            coolingControlOperations = cooling,
            timerControlOperations = FakeTimerControlOperations(unavailableTimerControl())
        )

        val result = operations.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = COOLING_DEVICE_UID,
                family = OwnerDeviceFamily.COOLING
            )
        )

        assertTrue(result is DeviceControlSurfacePreparationResult.Ready)
        assertEquals(1, cooling.refreshCalls)
        assertEquals(0, cooling.observeCalls)
        assertTrue(
            operations.consumeFreshPreparation(
                COOLING_DEVICE_UID,
                OwnerDeviceFamily.COOLING
            )
        )
        assertFalse(
            operations.consumeFreshPreparation(
                COOLING_DEVICE_UID,
                OwnerDeviceFamily.DOSING
            )
        )
    }

    @Test
    fun `Cooling refresh failure keeps navigation unavailable`() = runTest {
        val cooling = FakeCoolingControlOperations(
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
        )
        val operations = DefaultDeviceControlSurfacePreparationOperations(
            rootOperations = FakeRootOperations(coolingRootSnapshot()),
            dosingChannelOperations = FakeChannelOperations(),
            coolingControlOperations = cooling,
            timerControlOperations = FakeTimerControlOperations(unavailableTimerControl())
        )

        val result = operations.prepare(
            DeviceControlSurfacePreparationRequest(
                deviceUid = COOLING_DEVICE_UID,
                family = OwnerDeviceFamily.COOLING
            )
        )

        assertTrue(result is DeviceControlSurfacePreparationResult.Unavailable)
        assertEquals(1, cooling.refreshCalls)
        assertFalse(
            operations.consumeFreshPreparation(
                COOLING_DEVICE_UID,
                OwnerDeviceFamily.COOLING
            )
        )
    }

    @Test
    fun `Timer menu refreshes authoritative central state before ready`() = runTest {
        val timer = FakeTimerControlOperations(availableTimerControl(channelCount = 2))
        val operations = timerPreparation(timerRootSnapshot(channelCount = 2), timer)

        val result = operations.prepare(timerRequest())

        assertEquals(DeviceControlSurfacePreparationResult.Ready, result)
        assertEquals(1, timer.refreshCalls)
        assertEquals(0, timer.observeCalls)
        assertTrue(operations.consumeFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER))
        assertFalse(operations.consumeFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER))
    }

    @Test
    fun `partial Timer status cannot publish ready surface`() = runTest {
        val timer = FakeTimerControlOperations(availableTimerControl(channelCount = 1))
        val operations = timerPreparation(timerRootSnapshot(channelCount = 2), timer)

        val result = operations.prepare(timerRequest())

        assertEquals(
            DeviceControlSurfacePreparationResult.Unavailable(
                DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH
            ),
            result
        )
        assertEquals(1, timer.refreshCalls)
        assertFalse(operations.consumeFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER))
    }

    @Test
    fun `Timer refresh failure keeps navigation unavailable`() = runTest {
        val timer = FakeTimerControlOperations(unavailableTimerControl())
        val operations = timerPreparation(timerRootSnapshot(channelCount = 2), timer)

        val result = operations.prepare(timerRequest())

        assertEquals(
            DeviceControlSurfacePreparationResult.Unavailable(
                DeviceMenuUnavailableReason.CURRENT_LIVENESS_NOT_PROVEN
            ),
            result
        )
        assertEquals(1, timer.refreshCalls)
        assertFalse(operations.consumeFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER))
    }

    @Test
    fun `Timer catalog mismatch fails closed before runtime refresh`() = runTest {
        val timer = FakeTimerControlOperations(availableTimerControl(channelCount = 2))
        val operations = timerPreparation(
            timerRootSnapshot(channelCount = 2, declaredChannelCount = 3),
            timer
        )

        val result = operations.prepare(timerRequest())

        assertTrue(result is DeviceControlSurfacePreparationResult.Unavailable)
        assertEquals(0, timer.refreshCalls)
    }

    @Test
    fun `abandoned Timer handoff is discarded idempotently`() = runTest {
        val timer = FakeTimerControlOperations(availableTimerControl(channelCount = 2))
        val operations = timerPreparation(timerRootSnapshot(channelCount = 2), timer)
        assertEquals(DeviceControlSurfacePreparationResult.Ready, operations.prepare(timerRequest()))

        operations.discardFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER)
        operations.discardFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER)

        assertFalse(operations.consumeFreshPreparation(TIMER_DEVICE_UID, OwnerDeviceFamily.TIMER))
    }

    private fun preparation(
        channelCount: Int,
        channels: FakeChannelOperations
    ) = DefaultDeviceControlSurfacePreparationOperations(
        rootOperations = FakeRootOperations(rootSnapshot(channelCount)),
        dosingChannelOperations = channels,
        coolingControlOperations = DisconnectedDeviceCoolingControlOperations,
        timerControlOperations = FakeTimerControlOperations(unavailableTimerControl())
    )

    private fun timerPreparation(
        root: DeviceRootSnapshot,
        timer: DeviceTimerControlOperations
    ) = DefaultDeviceControlSurfacePreparationOperations(
        rootOperations = FakeRootOperations(root),
        dosingChannelOperations = FakeChannelOperations(),
        coolingControlOperations = DisconnectedDeviceCoolingControlOperations,
        timerControlOperations = timer
    )

    private fun request() = DeviceControlSurfacePreparationRequest(
        deviceUid = DEVICE_UID,
        family = OwnerDeviceFamily.DOSING
    )

    private fun timerRequest() = DeviceControlSurfacePreparationRequest(
        deviceUid = TIMER_DEVICE_UID,
        family = OwnerDeviceFamily.TIMER
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

    private class FakeCoolingControlOperations(
        private val result: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        var observeCalls: Int = 0
        var refreshCalls: Int = 0

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
            observeCalls += 1
            return MutableStateFlow(result)
        }

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = result

        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult {
            refreshCalls += 1
            return result
        }

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = result

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult = result
    }

    private class FakeTimerControlOperations(
        private val result: DeviceTimerControlResult
    ) : DeviceTimerControlOperations {
        var observeCalls: Int = 0
        var refreshCalls: Int = 0

        override fun observeControl(deviceUid: String): Flow<DeviceTimerControlResult> {
            observeCalls += 1
            return MutableStateFlow(result)
        }

        override fun currentControl(deviceUid: String): DeviceTimerControlResult = result

        override suspend fun refreshControl(deviceUid: String): DeviceTimerControlResult {
            refreshCalls += 1
            return result
        }

        override suspend fun refreshChannel(
            deviceUid: String,
            slotId: String
        ): DeviceTimerControlResult = result

        override suspend fun setRegime(
            deviceUid: String,
            slotId: String,
            regime: DeviceTimerChannelRegime
        ): DeviceTimerControlResult = result

        override suspend fun setTemporaryOverride(
            deviceUid: String,
            slotId: String,
            regime: DeviceTimerChannelRegime,
            durationMillis: Long
        ): DeviceTimerControlResult = result

        override suspend fun setDisplayName(
            deviceUid: String,
            slotId: String,
            update: DeviceTimerDisplayNameUpdate
        ): DeviceTimerControlResult = result

        override suspend fun replaceSchedules(
            deviceUid: String,
            slotId: String,
            expectedRevision: Long,
            schedules: List<DeviceTimerScheduleDraft>
        ): DeviceTimerControlResult = result
    }

    private companion object {
        const val DEVICE_UID = "dose-pro-4"
        const val COOLING_DEVICE_UID = "cool-pro-1f"
        const val TIMER_DEVICE_UID = "timer-pro-2"
    }
}

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

private fun coolingRootSnapshot() = DeviceRootSnapshot(
    deviceUid = "cool-pro-1f",
    title = "Cool Pro 1F",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.COOLING,
    catalogState = DeviceRootCatalogState.VALID,
    fanOutputCount = 1,
    temperatureSensorCount = 1,
    channelSlots = DeviceChannelSlots(
        lightChannels = emptyList(),
        timerChannels = emptyList(),
        dosingChannels = emptyList(),
        fanOutputs = listOf(
            DeviceFanOutputSlot(
                index = DeviceSlotIndex(0),
                wireKey = DeviceChannelWireKey("fan1"),
                defaultDisplayName = "Fan 1",
                displayNameEditable = false,
                route = DeviceRootRoute.COOLING_CONTROL
            )
        ),
        temperatureSensors = listOf(
            DeviceTemperatureSensorSlot(
                index = DeviceSlotIndex(0),
                defaultDisplayName = "Temperature 1",
                route = DeviceRootRoute.COOLING_TEMPERATURE
            )
        )
    )
)

private fun availableCoolingControl(): DeviceCoolingControlResult =
    DeviceCoolingControlResult.Available(
        DeviceCoolingControlSnapshot(
            mode = DeviceCoolingControlMode.AUTOMATIC,
            manualFanPercent = null,
            actualFanPercent = null,
            tankTemperatureC = null,
            capabilities = DeviceCoolingControlCapabilities(
                supportedModes = setOf(DeviceCoolingControlMode.AUTOMATIC),
                modeSelectionWritable = true,
                manualFan = null
            )
        )
    )

private fun timerRootSnapshot(
    channelCount: Int,
    declaredChannelCount: Int = channelCount
) = DeviceRootSnapshot(
    deviceUid = "timer-pro-2",
    title = "Timer Pro 2",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.TIMER,
    catalogState = DeviceRootCatalogState.VALID,
    timerChannelCount = declaredChannelCount,
    channelSlots = DeviceChannelSlots(
        lightChannels = emptyList(),
        timerChannels = List(channelCount) { index ->
            DeviceTimerChannelSlot(
                index = DeviceSlotIndex(index),
                wireKey = DeviceChannelWireKey("timer${index + 1}"),
                defaultDisplayName = "Timer ${index + 1}",
                displayNameEditable = true
            )
        },
        dosingChannels = emptyList(),
        fanOutputs = emptyList(),
        temperatureSensors = emptyList()
    )
)

private fun availableTimerControl(channelCount: Int): DeviceTimerControlResult =
    DeviceTimerControlResult.Available(
        DeviceTimerControlSnapshot(
            deviceUid = "timer-pro-2",
            revision = 1L,
            lockLoop = false,
            uptimeMillis = 1_000L,
            maxSchedulesPerChannel = 8,
            capabilities = DeviceTimerControlCapabilities(
                readOnly = false,
                supportsConfigApply = true,
                supportsChannelState = true,
                supportsSchedules = true,
                supportsSpansMidnight = true,
                supportsTemporaryOverride = true,
                supportsChannelDisplayName = true
            ),
            channels = List(channelCount) { index ->
                DeviceTimerChannelSnapshot(
                    slotId = "timer:timer${index + 1}",
                    channelNumber = index + 1,
                    defaultName = "Timer ${index + 1}",
                    displayName = "Timer ${index + 1}",
                    regime = DeviceTimerChannelRegime.AUTO,
                    operatingState = DeviceTimerOperatingState.OFF,
                    scheduleCount = 0,
                    activeScheduleSlotId = null,
                    activeScheduleName = null,
                    nextTransitionType = DeviceTimerNextTransitionType.NONE,
                    nextTransitionAtEpochMillis = null,
                    runtimeReason = DeviceTimerRuntimeReason.NO_ENABLED_SCHEDULES,
                    clockReady = true,
                    temporaryOverrideActive = false,
                    temporaryOverrideRemainingMillis = 0L,
                    outputHealth = DeviceTimerOutputHealth.UNVERIFIED,
                    physicalFeedbackAvailable = false,
                    displayNameEditable = true,
                    schedules = null
                )
            }
        )
    )

private fun unavailableTimerControl(): DeviceTimerControlResult =
    DeviceTimerControlResult.Failed(DeviceTimerControlFailure.Unavailable)
