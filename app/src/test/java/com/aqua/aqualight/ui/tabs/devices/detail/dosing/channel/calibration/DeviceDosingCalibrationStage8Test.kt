@file:Suppress("MagicNumber", "TooManyFunctions")

package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationConstraints
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationFailure
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceDosingCalibrationStage8Test {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calibration countdown refreshes authoritative completion before measurement`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(runningSnapshot(remainingMs = 1_500L))
            val viewModel = viewModel(operations)

            viewModel.bind(route())
            runCurrent()

            assertEquals(DeviceDosingCalibrationStep.CALIBRATION_RUN, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.isBusy)
            assertTrue(viewModel.uiState.value.isPumpActive)
            assertEquals(1_500L, viewModel.uiState.value.remainingMs)
            assertEquals(2_000L, viewModel.uiState.value.operationDurationMs)
            assertEquals(2_000, viewModel.uiState.value.illustrationOperationDurationMillis())
            val refreshesBeforeDeadline = operations.refreshes
            operations.completeCalibrationOnRefresh = true

            dispatcher.scheduler.advanceTimeBy(1_500L)
            runCurrent()

            assertTrue(operations.refreshes > refreshesBeforeDeadline)
            assertEquals(DeviceDosingCalibrationStep.MEASUREMENT, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isBusy)
            assertFalse(viewModel.uiState.value.isPumpActive)
            assertEquals(0L, viewModel.uiState.value.remainingMs)
        }

    @Test
    fun `reconnect fails closed and recovers only from the new authoritative session`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(
                activeVerificationSnapshot(remainingMs = 750L)
            )
            val viewModel = viewModel(operations)

            viewModel.bind(route())
            runCurrent()
            assertEquals(DeviceDosingCalibrationStep.VERIFICATION, viewModel.uiState.value.step)

            operations.publish(null)
            runCurrent()

            assertTrue(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.isBusy)
            assertFalse(viewModel.uiState.value.isPumpActive)
            assertEquals(0L, viewModel.uiState.value.remainingMs)

            operations.publish(activeVerificationSnapshot(remainingMs = 600L))
            runCurrent()
            assertEquals(DeviceDosingCalibrationStep.VERIFICATION, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.isPumpActive)
            assertEquals(600L, viewModel.uiState.value.remainingMs)

            operations.publish(null)
            runCurrent()
            operations.publish(idleSnapshot())
            runCurrent()

            assertEquals(DeviceDosingCalibrationStep.NAME, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isBusy)
            assertFalse(viewModel.uiState.value.isPumpActive)
        }

    @Test
    fun `prime interrupted by reconnect is safety stopped when authoritative state returns`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(idleSnapshot())
            val viewModel = viewModel(operations)
            advanceToPrime(viewModel)

            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()
            assertTrue(viewModel.uiState.value.isPumpActive)

            operations.publish(null)
            runCurrent()
            assertTrue(viewModel.uiState.value.isLoading)
            assertEquals(0, operations.primeStops)

            operations.publish(idleSnapshot())
            runCurrent()

            assertEquals(1, operations.primeStops)
            assertFalse(viewModel.uiState.value.isPumpActive)
        }

    @Test
    fun `interrupted verification refreshes authoritative completion after its countdown`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(
                activeVerificationSnapshot(remainingMs = 500L)
            )
            val viewModel = viewModel(operations)

            viewModel.bind(route())
            runCurrent()
            assertEquals(DeviceDosingCalibrationStep.VERIFICATION, viewModel.uiState.value.step)
            operations.completeVerificationOnRefresh = true

            dispatcher.scheduler.advanceTimeBy(500L)
            runCurrent()

            assertEquals(DeviceDosingCalibrationStep.CONFIRMATION, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isBusy)
            assertFalse(viewModel.uiState.value.isPumpActive)
            assertTrue(operations.refreshes >= 2)
        }

    @Test
    fun `prime safety timeout stops the output without presentation owned timing`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(idleSnapshot())
            val viewModel = viewModel(operations)
            advanceToPrime(viewModel)

            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()
            assertTrue(viewModel.uiState.value.isPumpActive)

            dispatcher.scheduler.advanceTimeBy(operations.constraints.primeSafetyTimeoutMs)
            runCurrent()

            assertEquals(1, operations.primeStops)
            assertFalse(viewModel.uiState.value.isPumpActive)
        }

    @Test
    fun `host stop cancels the prime safety timer and stops prime exactly once`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(idleSnapshot())
            val viewModel = viewModel(operations)
            advanceToPrime(viewModel)

            viewModel.onAction(DeviceDosingCalibrationAction.PrimePressed)
            runCurrent()
            viewModel.onHostStopped()
            runCurrent()

            assertEquals(1, operations.primeStops)
            assertFalse(viewModel.uiState.value.isPumpActive)

            dispatcher.scheduler.advanceTimeBy(operations.constraints.primeSafetyTimeoutMs)
            runCurrent()
            assertEquals(1, operations.primeStops)
        }

    @Test
    fun `exit from verification stops dose before discarding pending calibration`() =
        runTest(dispatcher) {
            val operations = Stage8CalibrationOperations(
                activeVerificationSnapshot(remainingMs = 1_000L)
            )
            val viewModel = viewModel(operations)

            viewModel.bind(route())
            runCurrent()
            viewModel.requestExit()
            runCurrent()

            assertEquals(0, operations.primeStops)
            assertEquals(1, operations.verificationStops)
            assertEquals(1, operations.cancels)
            assertEquals(listOf("verificationStop", "cancel"), operations.cleanupOrder)
        }

    private fun TestScope.advanceToPrime(viewModel: DeviceDosingChannelCalibrationViewModel) {
        viewModel.bind(route())
        runCurrent()
        viewModel.onAction(DeviceDosingCalibrationAction.DisplayNameChanged("Trace Elements"))
        viewModel.onAction(DeviceDosingCalibrationAction.SaveDisplayName)
        runCurrent()
        assertEquals(DeviceDosingCalibrationStep.PRIME, viewModel.uiState.value.step)
    }

    private fun viewModel(operations: Stage8CalibrationOperations) =
        DeviceDosingChannelCalibrationViewModel(
            operations = operations,
            clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
        )

    private class Stage8CalibrationOperations(
        initial: DeviceDosingCalibrationSnapshot
    ) : DeviceDosingCalibrationOperations {
        private val state = MutableStateFlow<DeviceDosingCalibrationSnapshot?>(initial)

        override val constraints = DeviceDosingCalibrationConstraints(
            primeSafetyTimeoutMs = 1_000L
        )

        var refreshes = 0
        var primeStops = 0
        var verificationStops = 0
        var cancels = 0
        var completeCalibrationOnRefresh = false
        var completeVerificationOnRefresh = false
        val cleanupOrder = mutableListOf<String>()

        fun publish(snapshot: DeviceDosingCalibrationSnapshot?) {
            state.value = snapshot
        }

        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingCalibrationSnapshot?> = state

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult {
            refreshes += 1
            val current = state.value ?: return connectionFailure()
            val refreshed = when {
                completeCalibrationOnRefresh &&
                    current.sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING ->
                    current.copy(
                        deviceUptimeMs = current.startedAtUptimeMs + current.durationMs,
                        manualActive = false
                    )
                completeVerificationOnRefresh && current.hasActiveVerification() ->
                    current.copy(
                        verificationDoseComplete = true,
                        verificationDoseRemainingMs = 0L,
                        manualActive = false
                    )
                else -> current
            }
            state.value = refreshed
            return success(refreshed)
        }

        override suspend fun primeStart(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult = currentOrFailure { current ->
            current.copy(manualActive = true)
        }

        override suspend fun primeStop(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult {
            primeStops += 1
            cleanupOrder += "primeStop"
            return currentOrFailure { current -> current.copy(manualActive = false) }
        }

        override suspend fun start(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult = currentOrFailure { it }

        override suspend fun finish(
            deviceUid: String,
            slotId: String,
            measuredMl: Double
        ): DeviceDosingCalibrationResult = currentOrFailure { it }

        override suspend fun startVerificationDose(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult = currentOrFailure { it }

        override suspend fun stopVerificationDose(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult {
            verificationStops += 1
            cleanupOrder += "verificationStop"
            return currentOrFailure { current -> current.copy(manualActive = false) }
        }

        override suspend fun confirm(
            deviceUid: String,
            slotId: String,
            displayName: String
        ): DeviceDosingCalibrationResult = currentOrFailure { it }

        override suspend fun cancel(
            deviceUid: String,
            slotId: String
        ): DeviceDosingCalibrationResult {
            cancels += 1
            cleanupOrder += "cancel"
            return currentOrFailure { current ->
                current.copy(
                    sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                    verificationDoseStarted = false,
                    verificationDoseComplete = false,
                    verificationDoseRemainingMs = 0L,
                    manualActive = false
                )
            }
        }

        private fun currentOrFailure(
            transform: (DeviceDosingCalibrationSnapshot) -> DeviceDosingCalibrationSnapshot
        ): DeviceDosingCalibrationResult {
            val current = state.value ?: return connectionFailure()
            val transformed = transform(current)
            state.value = transformed
            return success(transformed)
        }

        private fun success(snapshot: DeviceDosingCalibrationSnapshot) =
            DeviceDosingCalibrationResult.Success(snapshot)

        private fun connectionFailure() = DeviceDosingCalibrationResult.Rejected(
            DeviceDosingCalibrationFailure.CONNECTION
        )
    }

    private companion object {
        fun route() = DeviceDosingCalibrationRoute(
            deviceUid = "stage8-device",
            slotId = "dosing:channel1",
            pumpCount = 2,
            channelNumber = 1,
            recalibration = false
        )

        fun idleSnapshot() = snapshot()

        fun runningSnapshot(remainingMs: Long): DeviceDosingCalibrationSnapshot {
            val durationMs = 2_000L
            val uptimeMs = 12_000L
            return snapshot().copy(
                deviceUptimeMs = uptimeMs,
                sessionPhase = DeviceDosingCalibrationSessionPhase.RUNNING,
                startedAtUptimeMs = uptimeMs - (durationMs - remainingMs),
                durationMs = durationMs,
                manualActive = true
            )
        }

        fun activeVerificationSnapshot(remainingMs: Long) = snapshot().copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
            durationMs = 2_000L,
            measuredMl = 4.0,
            pendingDoseMsPerMl = 500L,
            verificationDoseStarted = true,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = remainingMs,
            manualActive = true
        )

        fun snapshot() = DeviceDosingCalibrationSnapshot(
            deviceUid = "stage8-device",
            slotId = "dosing:channel1",
            pumpCount = 2,
            channelNumber = 1,
            channelTitle = "Channel 1",
            deviceUptimeMs = 12_000L,
            calibrated = false,
            lastCalibratedAt = 0L,
            sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
            startedAtUptimeMs = 0L,
            durationMs = 0L,
            measuredMl = 0.0,
            pendingDoseMsPerMl = 0L,
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }
}

private fun DeviceDosingCalibrationSnapshot.hasActiveVerification(): Boolean =
    sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
        verificationDoseStarted &&
        !verificationDoseComplete
