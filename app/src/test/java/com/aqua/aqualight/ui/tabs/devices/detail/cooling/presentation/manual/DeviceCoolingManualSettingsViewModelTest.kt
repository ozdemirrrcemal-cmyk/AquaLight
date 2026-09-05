package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.manual

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.common.CoolingMutationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingManualSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun manualTargetWriteRequiresManualMode() = runTest(dispatcher) {
        val operations = FakeControlOperations(
            initial = available(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 40
            ),
            mutation = available(
                mode = DeviceCoolingControlMode.AUTOMATIC,
                manualFanPercent = 70
            )
        )
        val viewModel = DeviceCoolingManualSettingsViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.updateTargetPercent(73)
        viewModel.commitTargetPercent()

        assertNull(operations.lastRequestedPercent)
        assertEquals(40, viewModel.uiState.value.targetPercent)
        assertFalse(viewModel.uiState.value.canWrite)
    }

    @Test
    fun failedWriteKeepsTheDraftStableWithoutChangingAuthoritativeData() = runTest(dispatcher) {
        val operations = FakeControlOperations(
            initial = available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 40
            ),
            mutation = DeviceCoolingControlResult.Failed(
                DeviceCoolingControlFailure.Rejected(DeviceCoolingCommandFailure.HARDWARE_FAILURE)
            )
        )
        val viewModel = DeviceCoolingManualSettingsViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.updateTargetPercent(65)

        assertNull(operations.lastRequestedPercent)
        assertEquals(65, viewModel.uiState.value.targetPercent)
        assertEquals(40, viewModel.uiState.value.authoritativeTargetPercent)

        viewModel.commitTargetPercent()

        assertEquals(65, operations.lastRequestedPercent)
        assertEquals(65, viewModel.uiState.value.targetPercent)
        assertEquals(40, viewModel.uiState.value.authoritativeTargetPercent)
        assertTrue(
            viewModel.uiState.value.mutationState is CoolingMutationState.OperationError
        )
    }

    @Test
    fun rapidGesturesStayInteractiveAndConflateToTheLatestReleasedValue() = runTest(dispatcher) {
        val operations = QueuedControlOperations(
            initial = available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 40
            )
        )
        val viewModel = DeviceCoolingManualSettingsViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.updateTargetPercent(60)

        assertTrue(operations.requestedPercents.isEmpty())
        assertEquals(60, viewModel.uiState.value.targetPercent)

        viewModel.commitTargetPercent()

        assertEquals(listOf(60), operations.requestedPercents)
        assertTrue(viewModel.uiState.value.canWrite)
        assertTrue(viewModel.uiState.value.mutationState is CoolingMutationState.Saving)

        viewModel.updateTargetPercent(80)
        viewModel.commitTargetPercent()
        operations.respond(
            available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 60
            )
        )
        runCurrent()

        assertEquals(listOf(60, 80), operations.requestedPercents)
        assertEquals(80, viewModel.uiState.value.targetPercent)
        assertEquals(60, viewModel.uiState.value.authoritativeTargetPercent)

        operations.respond(
            available(
                mode = DeviceCoolingControlMode.MANUAL,
                manualFanPercent = 80
            )
        )
        runCurrent()

        assertEquals(80, viewModel.uiState.value.targetPercent)
        assertEquals(80, viewModel.uiState.value.authoritativeTargetPercent)
        assertNull(viewModel.uiState.value.draftTargetPercent)
        assertTrue(viewModel.uiState.value.canWrite)
        assertTrue(viewModel.uiState.value.mutationState is CoolingMutationState.Saved)
    }

    private class FakeControlOperations(
        private val initial: DeviceCoolingControlResult,
        private val mutation: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        var lastRequestedPercent: Int? = null
            private set

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
            flowOf(initial)

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = initial

        override suspend fun refreshControl(
            deviceUid: String
        ): DeviceCoolingControlResult = initial

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = initial

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult {
            lastRequestedPercent = percent
            return mutation
        }
    }

    private class QueuedControlOperations(
        private val initial: DeviceCoolingControlResult
    ) : DeviceCoolingControlOperations {
        private val responses = Channel<DeviceCoolingControlResult>(Channel.UNLIMITED)
        val requestedPercents = mutableListOf<Int>()

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
            flowOf(initial)

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = initial

        override suspend fun refreshControl(
            deviceUid: String
        ): DeviceCoolingControlResult = initial

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = initial

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult {
            requestedPercents += percent
            return responses.receive()
        }

        suspend fun respond(result: DeviceCoolingControlResult) {
            responses.send(result)
        }
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"

        fun available(
            mode: DeviceCoolingControlMode,
            manualFanPercent: Int
        ): DeviceCoolingControlResult = DeviceCoolingControlResult.Available(
            DeviceCoolingControlSnapshot(
                mode = mode,
                manualFanPercent = manualFanPercent,
                actualFanPercent = 35,
                tankTemperatureC = 25.4,
                capabilities = DeviceCoolingControlCapabilities(
                    supportedModes = setOf(
                        DeviceCoolingControlMode.AUTOMATIC,
                        DeviceCoolingControlMode.MANUAL
                    ),
                    modeSelectionWritable = true,
                    manualFan = DeviceCoolingManualFanCapabilities(
                        minimumPercent = 0,
                        maximumPercent = 100,
                        stepPercent = 1,
                        writable = true
                    )
                )
            )
        )
    }
}
