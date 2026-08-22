package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingChannelDetailViewModelTest {
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
    fun `route remains unresolved until authoritative firmware snapshot arrives`() {
        val viewModel = DeviceDosingChannelDetailViewModel(
            FakeOperations(snapshot(calibrated = true))
        )

        viewModel.bind(DEVICE_UID, SLOT_ID)

        assertFalse(viewModel.currentDraft().authoritativeStateAvailable)
        assertFalse(viewModel.currentDraft().routeValid)

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentDraft().authoritativeStateAvailable)
        assertTrue(viewModel.currentDraft().routeValid)
    }

    @Test
    fun `authoritative uncalibrated snapshot rejects detail route`() {
        val viewModel = DeviceDosingChannelDetailViewModel(
            FakeOperations(snapshot(calibrated = false))
        )

        viewModel.bind(DEVICE_UID, SLOT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentDraft().authoritativeStateAvailable)
        assertFalse(viewModel.currentDraft().routeValid)
    }

    @Test
    fun `cached authoritative snapshot makes calibrated detail immediately interactive`() {
        val current = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 8L
        )
        val operations = FakeOperations(
            refreshSnapshot = current,
            currentSnapshot = current,
            missedDoseRecoveryResults = listOf(
                DeviceDosingChannelCommittedResult(revision = 9L)
            )
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)

        viewModel.bind(DEVICE_UID, SLOT_ID)

        assertTrue(viewModel.currentDraft().authoritativeStateAvailable)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEditable)
        assertEquals(0, operations.refreshCount)

        viewModel.setMissedDoseRecoveryEnabled(true)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(true), operations.missedDoseRecoveryTargets)
        assertEquals(0, operations.refreshCount)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
    }

    @Test
    fun `superseded committed result cannot settle the latest switch intent`() {
        val intent = DeviceDosingMissedDoseRecoveryIntentState()
        val initial = DeviceDosingMissedDoseRecoveryAuthority(
            enabled = false,
            editable = true,
            revision = 1L
        )
        val confirmed = DeviceDosingMissedDoseRecoveryAuthority(
            enabled = true,
            editable = true,
            revision = 2L
        )

        val firstRequest = intent.request(true)
        val latestRequest = intent.request(false)
        assertEquals(
            DeviceDosingMissedDoseRecoveryFeedback.None,
            intent.onCommitted(
                requestId = firstRequest,
                targetEnabled = true,
                committedRevision = 2L
            )
        )
        intent.onAuthorityChanged(confirmed)
        val pending = intent.present(DeviceDosingChannelDetailDraft(), initial)
        assertFalse(pending.missedDoseRecoveryEnabled)
        assertTrue(pending.missedDoseRecoverySyncing)

        assertEquals(
            DeviceDosingMissedDoseRecoveryFeedback.Saved,
            intent.onCommitted(
                requestId = latestRequest,
                targetEnabled = false,
                committedRevision = 3L
            )
        )
        val settled = intent.present(DeviceDosingChannelDetailDraft(), confirmed)
        assertFalse(settled.missedDoseRecoveryEnabled)
        assertFalse(settled.missedDoseRecoverySyncing)
    }

    @Test
    fun `rapid reversal proactively reconciles and completes without passive switch freeze`() {
        val initial = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 1L
        )
        val operations = FakeOperations(
            refreshSnapshot = initial,
            missedDoseRecoveryResults = listOf(
                DeviceDosingChannelCommittedResult(revision = 2L),
                DeviceDosingChannelCommittedResult(revision = 3L)
            ),
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)

        viewModel.bind(DEVICE_UID, SLOT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setMissedDoseRecoveryEnabled(true)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertTrue(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertFalse(viewModel.currentDraft().operationInProgress)

        viewModel.setMissedDoseRecoveryEnabled(false)
        assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEditable)
        assertTrue(viewModel.currentDraft().missedDoseRecoverySyncing)

        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(true, false), operations.missedDoseRecoveryTargets)
        assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertFalse(viewModel.currentDraft().operationInProgress)
        assertEquals(1, operations.refreshCount)
    }

    @Test
    fun `committed write settles without waiting for readback and survives stale presentation`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelCommittedResult(revision = 2L)
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()

            viewModel.setMissedDoseRecoveryEnabled(true)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(listOf(true), operations.missedDoseRecoveryTargets)
            assertEquals(1, operations.refreshCount)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)

            operations.emit(initial.copy(channelTitle = "Macro stale"))
            testScheduler.advanceUntilIdle()

            assertEquals("Macro stale", viewModel.currentDraft().channelTitle)
            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)

            operations.emit(
                snapshot(
                    calibrated = true,
                    missedDoseRecoveryEnabled = true,
                    revision = 2L
                )
            )
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
        }

    @Test
    fun `rapid reversal emits one saved event only after authoritative revision unlocks final intent`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelCommittedResult(revision = 2L),
                    DeviceDosingChannelCommittedResult(revision = 3L)
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()

            viewModel.setMissedDoseRecoveryEnabled(true)
            viewModel.setMissedDoseRecoveryEnabled(false)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(true, false), operations.missedDoseRecoveryTargets)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
            assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        }

    @Test
    fun `rapid repeated UI results settle only the latest user intent`() = runTest(dispatcher) {
        val initial = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 1L
        )
        val operations = FakeOperations(
            refreshSnapshot = initial,
            missedDoseRecoveryResults = listOf(
                DeviceDosingChannelCommittedResult(revision = 2L),
                DeviceDosingChannelCommittedResult(revision = 3L),
                DeviceDosingChannelCommittedResult(revision = 4L)
            )
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)
        val events = mutableListOf<DeviceDosingChannelDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.bind(DEVICE_UID, SLOT_ID)
        testScheduler.advanceUntilIdle()

        viewModel.setMissedDoseRecoveryEnabled(true)
        viewModel.setMissedDoseRecoveryEnabled(false)
        viewModel.setMissedDoseRecoveryEnabled(true)

        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertTrue(viewModel.currentDraft().missedDoseRecoverySyncing)

        testScheduler.advanceUntilIdle()

        assertEquals(listOf(true, false, true), operations.missedDoseRecoveryTargets)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
    }

    @Test
    fun `stale snapshot cannot revert committed switch but keeps base non switch projection behavior`() {
        val initial = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 1L
        )
        val operations = FakeOperations(
            refreshSnapshot = initial,
            missedDoseRecoveryResults = listOf(
                DeviceDosingChannelCommittedResult(revision = 2L)
            )
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)

        viewModel.bind(DEVICE_UID, SLOT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setMissedDoseRecoveryEnabled(true)
        dispatcher.scheduler.advanceUntilIdle()

        operations.emit(initial.copy(channelTitle = "Macro stale"))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Macro stale", viewModel.currentDraft().channelTitle)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertEquals(listOf(true), operations.missedDoseRecoveryTargets)
        assertEquals(1, operations.refreshCount)

        operations.emit(
            snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = true,
                revision = 2L
            )
        )
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
    }

    @Test
    fun `superseded failed mutation cannot emit a red error for the latest intent`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelOperationResult.Failed,
                    DeviceDosingChannelOperationResult.Success(initial)
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()

            viewModel.setMissedDoseRecoveryEnabled(true)
            viewModel.setMissedDoseRecoveryEnabled(false)
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(listOf(true, false), operations.missedDoseRecoveryTargets)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
        }

    @Test
    fun `latest failed reversal emits one real error without any helper readback`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelCommittedResult(revision = 2L),
                    DeviceDosingChannelOperationResult.Failed
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()

            viewModel.setMissedDoseRecoveryEnabled(true)
            viewModel.setMissedDoseRecoveryEnabled(false)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(true, false), operations.missedDoseRecoveryTargets)
            assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(
                listOf(
                    DeviceDosingChannelDetailEvent.OperationFailed(
                        DeviceDosingChannelDetailFailure.TRY_AGAIN
                    )
                ),
                events
            )
        }

    @Test
    fun `latest intent failure rolls back once and emits one real error`() = runTest(dispatcher) {
        val initial = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 1L
        )
        val operations = FakeOperations(
            refreshSnapshot = initial,
            missedDoseRecoveryResults = listOf(DeviceDosingChannelOperationResult.Failed)
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)
        val events = mutableListOf<DeviceDosingChannelDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(events)
        }

        viewModel.bind(DEVICE_UID, SLOT_ID)
        testScheduler.advanceUntilIdle()

        viewModel.setMissedDoseRecoveryEnabled(true)
        assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)

        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.currentDraft().missedDoseRecoveryEnabled)
        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertEquals(
            listOf(
                DeviceDosingChannelDetailEvent.OperationFailed(
                    DeviceDosingChannelDetailFailure.TRY_AGAIN
                )
            ),
            events
        )
    }

    @Test
    fun `successful mutation snapshot settles without any switch triggered readback`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val accepted = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = true,
                revision = 2L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelOperationResult.Success(accepted)
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()

            viewModel.setMissedDoseRecoveryEnabled(true)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(1, operations.refreshCount)
            assertEquals(listOf(true), operations.missedDoseRecoveryTargets)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
        }

    @Test
    fun `other channel mutations remain blocked only while switch write is actually in flight`() {
        val initial = snapshot(
            calibrated = true,
            missedDoseRecoveryEnabled = false,
            revision = 1L,
            resetSupported = true
        )
        val operations = FakeOperations(
            refreshSnapshot = initial,
            missedDoseRecoveryResults = listOf(
                DeviceDosingChannelCommittedResult(revision = 2L)
            )
        )
        val viewModel = DeviceDosingChannelDetailViewModel(operations)

        viewModel.bind(DEVICE_UID, SLOT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.setMissedDoseRecoveryEnabled(true)
        assertTrue(viewModel.currentDraft().missedDoseRecoverySyncing)

        viewModel.resetChannel()
        assertEquals(0, operations.resetCallCount)

        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
        assertFalse(viewModel.currentDraft().operationInProgress)
        assertTrue(viewModel.currentDraft().resetEnabled)
    }

    @Test
    fun `centrally reconciled switch commit settles without a UI mutation replay`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = false,
                revision = 1L
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelCommittedResult(revision = 3L)
                )
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()
            viewModel.setMissedDoseRecoveryEnabled(true)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(true), operations.missedDoseRecoveryTargets)
            assertEquals(1, operations.refreshCount)
            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
        }

    @Test
    fun `helper readback failure cannot roll back a rapid switch reversal`() =
        runTest(dispatcher) {
            val initial = snapshot(
                calibrated = true,
                missedDoseRecoveryEnabled = true,
                revision = 1L,
                resetSupported = true
            )
            val operations = FakeOperations(
                refreshSnapshot = initial,
                missedDoseRecoveryResults = listOf(
                    DeviceDosingChannelCommittedResult(revision = 2L),
                    DeviceDosingChannelCommittedResult(revision = 3L)
                ),
                subsequentRefreshResults = listOf(DeviceDosingChannelOperationResult.Failed)
            )
            val viewModel = DeviceDosingChannelDetailViewModel(operations)
            val events = mutableListOf<DeviceDosingChannelDetailEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.events.toList(events)
            }

            viewModel.bind(DEVICE_UID, SLOT_ID)
            testScheduler.advanceUntilIdle()
            viewModel.setMissedDoseRecoveryEnabled(false)
            viewModel.setMissedDoseRecoveryEnabled(true)
            testScheduler.advanceUntilIdle()

            assertEquals(listOf(false, true), operations.missedDoseRecoveryTargets)
            assertTrue(viewModel.currentDraft().missedDoseRecoveryEnabled)
            assertFalse(viewModel.currentDraft().missedDoseRecoverySyncing)
            assertTrue(viewModel.currentDraft().resetEnabled)
            assertEquals(listOf(DeviceDosingChannelDetailEvent.MissedDoseRecoverySaved), events)
            assertEquals(1, operations.refreshCount)

            viewModel.resetChannel()
            testScheduler.advanceUntilIdle()
            assertEquals(1, operations.resetCallCount)
        }

    private class FakeOperations(
        private val refreshSnapshot: DeviceDosingChannelSnapshot,
        private val currentSnapshot: DeviceDosingChannelSnapshot? = null,
        missedDoseRecoveryResults: List<DeviceDosingChannelOperationResult> = emptyList(),
        subsequentRefreshResults: List<DeviceDosingChannelOperationResult> = emptyList()
    ) : DeviceDosingChannelOperations {
        private val state = MutableStateFlow(currentSnapshot)
        private val queuedMissedDoseRecoveryResults = missedDoseRecoveryResults.toMutableList()
        private val queuedSubsequentRefreshResults = subsequentRefreshResults.toMutableList()

        var refreshCount: Int = 0
            private set

        var resetCallCount: Int = 0
            private set

        val missedDoseRecoveryTargets = mutableListOf<Boolean>()

        override fun current(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelSnapshot? = currentSnapshot

        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingChannelSnapshot?> = state

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult {
            refreshCount += 1
            val result = if (refreshCount == 1 || queuedSubsequentRefreshResults.isEmpty()) {
                DeviceDosingChannelOperationResult.Success(refreshSnapshot)
            } else {
                queuedSubsequentRefreshResults.removeAt(0)
            }
            if (result is DeviceDosingChannelOperationResult.Success) {
                state.value = result.snapshot
            }
            return result
        }

        override suspend fun applyProgram(
            deviceUid: String,
            slotId: String,
            program: DeviceDosingProgram
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun setMissedDoseRecoveryEnabled(
            deviceUid: String,
            slotId: String,
            enabled: Boolean
        ): DeviceDosingChannelOperationResult {
            missedDoseRecoveryTargets += enabled
            return if (queuedMissedDoseRecoveryResults.isEmpty()) {
                DeviceDosingChannelOperationResult.Failed
            } else {
                queuedMissedDoseRecoveryResults.removeAt(0)
            }
        }

        override suspend fun applyReservoirSettings(
            deviceUid: String,
            slotId: String,
            settings: DeviceDosingReservoirSettings
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun refillReservoir(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseNow(
            deviceUid: String,
            slotId: String,
            amountMicroliters: Long
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseStop(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun reset(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult {
            resetCallCount += 1
            return DeviceDosingChannelOperationResult.Failed
        }

        fun emit(snapshot: DeviceDosingChannelSnapshot?) {
            state.value = snapshot
        }
    }

    private companion object {
        const val DEVICE_UID = "AQL-DOSING-DETAIL-ROUTE-TEST"
        const val SLOT_ID = "dosing:channel1"
        const val CALIBRATED_AT = 1_786_320_000L

        fun snapshot(
            calibrated: Boolean,
            missedDoseRecoveryEnabled: Boolean? = null,
            revision: Long = 1L,
            resetSupported: Boolean = false
        ): DeviceDosingChannelSnapshot =
            DeviceDosingChannelSnapshot(
                deviceUid = DEVICE_UID,
                slotId = SLOT_ID,
                pumpCount = 1,
                channelNumber = 1,
                channelTitle = "Macro",
                revision = revision,
                runtimeEnabled = true,
                runtimeReason = DeviceDosingRuntimeReason.NONE,
                deliveryAccountingCertain = true,
                calibrated = calibrated,
                lastCalibratedAtEpochSeconds = if (calibrated) CALIBRATED_AT else 0L,
                scheduling = DeviceDosingSchedulingPolicy(),
                program = missedDoseRecoveryEnabled?.let(::program),
                progress = DeviceDosingChannelProgress(),
                reservoir = DeviceDosingReservoirSnapshot(),
                activeRun = DeviceDosingActiveRun(),
                controls = DeviceDosingChannelControls(
                    programEditable = missedDoseRecoveryEnabled != null,
                    resetSupported = resetSupported
                )
            )

        fun program(missedDoseRecoveryEnabled: Boolean): DeviceDosingProgram =
            DeviceDosingProgram(
                enabled = true,
                weekdays = List(7) { true },
                schedule = DeviceDosingProgramSchedule.Single(
                    dailyDoseMicroliters = 1_000L,
                    startTimeMillis = 0L
                ),
                missedDoseRecoveryEnabled = missedDoseRecoveryEnabled
            )
    }
}
