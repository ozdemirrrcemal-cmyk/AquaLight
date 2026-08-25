package com.aqua.aqualight.data.devices.dosing.v1

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCardUnavailableReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.data.devices.dosing.UnavailableDeviceDosingChannelOperations
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.ws.AqlWsConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDosingV1CardOperationsTest {

    @Test
    fun `reused authenticated session hydrates through central refresh`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = authenticatedState(),
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(refreshAllResult = true)
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertEquals(SINGLE_REFRESH, channels.refreshAllCalls)
        assertEquals(listOf(DeviceDosingCardState.Preparing), states)
    }

    @Test
    fun `reused authenticated refresh failure is surfaced as typed unavailable state`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = authenticatedState(),
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(refreshAllResult = false)
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertEquals(SINGLE_REFRESH, channels.refreshAllCalls)
        assertEquals(
            listOf(
                DeviceDosingCardState.Preparing,
                DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.AUTHORITATIVE_REFRESH_FAILED
                )
            ),
            states
        )
    }

    @Test
    fun `connection failure is surfaced instead of becoming an endless preparing state`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.failure(IllegalStateException(CONNECTION_FAILURE_MESSAGE))
        )
        val channels = FakeChannelOperations(refreshAllResult = true)
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertEquals(NO_REFRESH, channels.refreshAllCalls)
        assertEquals(
            listOf(
                DeviceDosingCardState.Preparing,
                DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.RUNTIME_CONNECTION_FAILED
                )
            ),
            states
        )
    }

    @Test
    fun `fresh connection leaves bootstrap refresh to production lifecycle`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(refreshAllResult = true)
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertEquals(NO_REFRESH, channels.refreshAllCalls)
        assertEquals(listOf(DeviceDosingCardState.Preparing), states)
    }

    @Test
    fun `unexpected observation failure becomes typed unavailable state`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(
            refreshAllResult = true,
            observations = flow { error(OBSERVATION_FAILURE_MESSAGE) }
        )
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertEquals(
            listOf(
                DeviceDosingCardState.Unavailable(
                    DeviceDosingCardUnavailableReason.OBSERVATION_FAILED
                )
            ),
            states
        )
    }

    @Test
    fun `retained central snapshot is first state during runtime preparation`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(
            refreshAllResult = true,
            observations = flowOf(completeSnapshotSet(RETAINED_CHANNEL_TITLE))
        )
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertSingleReadyState(states, RETAINED_CHANNEL_TITLE)
        assertEquals(NO_REFRESH, channels.refreshAllCalls)
    }

    @Test
    fun `refresh failure keeps retained central snapshot visible`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = authenticatedState(),
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(
            refreshAllResult = false,
            observations = flowOf(completeSnapshotSet(RETAINED_CHANNEL_TITLE))
        )
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertSingleReadyState(states, RETAINED_CHANNEL_TITLE)
        assertEquals(SINGLE_REFRESH, channels.refreshAllCalls)
    }

    @Test
    fun `partial refresh never replaces retained snapshot with preparing`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(
            refreshAllResult = true,
            observations = flow {
                emit(completeSnapshotSet(RETAINED_CHANNEL_TITLE))
                emit(emptyList())
            }
        )
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertSingleReadyState(states, RETAINED_CHANNEL_TITLE)
    }

    @Test
    fun `observation failure after retained snapshot keeps last validated card state`() = runTest {
        val runtime = FakeRuntimePort(
            connectionState = AqlWsConnectionState.Disconnected,
            connectResult = Result.success(Unit)
        )
        val channels = FakeChannelOperations(
            refreshAllResult = true,
            observations = flow {
                emit(completeSnapshotSet(RETAINED_CHANNEL_TITLE))
                error(OBSERVATION_FAILURE_MESSAGE)
            }
        )
        val operations = operations(runtime, channels)

        val states = operations.observe(DEVICE_UID).toList()

        assertSingleReadyState(states, RETAINED_CHANNEL_TITLE)
    }

    private fun operations(
        runtime: DeviceDosingCardRuntimePort,
        channels: DeviceDosingChannelOperations
    ): DeviceDosingV1CardOperations = DeviceDosingV1CardOperations(
        runtimePort = runtime,
        channelOperations = channels,
        connectionDispatcher = Dispatchers.Unconfined
    )

    private fun authenticatedState(): AqlWsConnectionState.Authenticated =
        AqlWsConnectionState.Authenticated(
            deviceUid = DeviceUid(DEVICE_UID),
            authenticatedAtMillis = AUTHENTICATED_AT_MILLIS
        )

    private fun completeSnapshotSet(title: String): List<DeviceDosingChannelSnapshot> = listOf(
        DeviceDosingChannelSnapshot(
            deviceUid = DEVICE_UID,
            slotId = SLOT_ID,
            pumpCount = PUMP_COUNT,
            channelNumber = CHANNEL_NUMBER,
            channelTitle = title,
            revision = REVISION,
            runtimeEnabled = false,
            runtimeReason = DeviceDosingRuntimeReason.PROGRAM_DISABLED,
            deliveryAccountingCertain = true,
            calibrated = true,
            lastCalibratedAtEpochSeconds = LAST_CALIBRATED_AT_EPOCH_SECONDS,
            scheduling = DeviceDosingSchedulingPolicy(),
            program = null,
            progress = DeviceDosingChannelProgress(),
            reservoir = DeviceDosingReservoirSnapshot(),
            activeRun = DeviceDosingActiveRun(),
            controls = DeviceDosingChannelControls(),
            usageToday = DeviceDosingDailyUsageSnapshot()
        )
    )

    private fun assertSingleReadyState(
        states: List<DeviceDosingCardState>,
        expectedChannelTitle: String
    ) {
        assertEquals(SINGLE_STATE, states.size)
        val ready = states.single()
        assertTrue(ready is DeviceDosingCardState.Ready)
        ready as DeviceDosingCardState.Ready
        assertEquals(expectedChannelTitle, ready.summary.channels.single().title)
    }

    private class FakeRuntimePort(
        private val family: DeviceFamily? = DeviceFamily.DOSING,
        private val connectionState: AqlWsConnectionState?,
        private val connectResult: Result<Unit>
    ) : DeviceDosingCardRuntimePort {
        override fun currentDeviceFamily(deviceUid: DeviceUid): DeviceFamily? = family

        override fun currentConnectionState(deviceUid: DeviceUid): AqlWsConnectionState? =
            connectionState

        override fun connectRuntime(deviceUid: DeviceUid): Result<Unit> = connectResult
    }

    private class FakeChannelOperations(
        private val refreshAllResult: Boolean,
        private val observations: Flow<List<DeviceDosingChannelSnapshot>> = flowOf(emptyList())
    ) : DeviceDosingChannelOperations by UnavailableDeviceDosingChannelOperations {
        var refreshAllCalls: Int = NO_REFRESH

        override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
            observations

        override suspend fun refreshAll(deviceUid: String): Boolean {
            refreshAllCalls += SINGLE_REFRESH
            return refreshAllResult
        }
    }

    private companion object {
        const val DEVICE_UID = "dose-pro-4"
        const val SLOT_ID = "dosing:channel1"
        const val RETAINED_CHANNEL_TITLE = "Nitrate"
        const val CONNECTION_FAILURE_MESSAGE = "connection failed"
        const val OBSERVATION_FAILURE_MESSAGE = "observation failed"
        const val AUTHENTICATED_AT_MILLIS = 1L
        const val LAST_CALIBRATED_AT_EPOCH_SECONDS = 1L
        const val REVISION = 7L
        const val PUMP_COUNT = 1
        const val CHANNEL_NUMBER = 1
        const val NO_REFRESH = 0
        const val SINGLE_REFRESH = 1
        const val SINGLE_STATE = 1
    }
}