package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMutationOrigin
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramRevisionOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.FakeDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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
class DeviceDosingPlanCutoverTest {
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
    fun `all plan modes preserve one authoritative program intent through save`() =
        runTest(dispatcher) {
            stage10Programs().forEach { program ->
                val operations = FakeDeviceDosingChannelOperations(
                    sampleDosingChannelSnapshot().copy(program = program)
                )
                val viewModel = DeviceDosingPlanViewModel(operations)

                viewModel.bind(DEVICE_UID, SLOT_ID)
                assertEquals(program, viewModel.currentEditorState.programIntent)
                viewModel.setScheduleEnabled(!program.enabled)
                val expectedProgram = program.copy(enabled = !program.enabled)

                viewModel.save()

                assertEquals(expectedProgram, operations.lastProgram)
                assertEquals(expectedProgram, viewModel.currentEditorState.programIntent)
            }
        }

    @Test
    fun `authoritative scheduling limit blocks a stale oversized editor result`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations(
                sampleDosingChannelSnapshot().copy(
                    scheduling = DeviceDosingSchedulingPolicy(maxEventsPerChannel = 1)
                )
            )
            val viewModel = DeviceDosingPlanViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)

            viewModel.applyScheduleUpdate(
                DosingPlanScheduleUpdate.Timer(
                    listOf(
                        DeviceDosingTimerDoseDraft(0L, 1_000L),
                        DeviceDosingTimerDoseDraft(3_600_000L, 1_000L)
                    )
                )
            )

            assertTrue(viewModel.currentEditorState.canSave)
            viewModel.save()
            assertNull(operations.lastProgram)
            assertEquals(
                DeviceDosingPlanEvent.InvalidDraft(DosingPlanValidationIssue.EVENT_LIMIT),
                viewModel.events.first()
            )
        }

    @Test
    fun `persistent data boundary conflict is surfaced after the central retry budget`() =
        runTest(dispatcher) {
            val delegate = FakeDeviceDosingChannelOperations()
            val attempts = mutableListOf<Pair<DeviceDosingProgram, DeviceDosingProgramMutationOrigin>>()
            val operations = object :
                DeviceDosingChannelOperations by delegate,
                DeviceDosingProgramRevisionOperations {
                override suspend fun applyProgramAtOrigin(
                    deviceUid: String,
                    slotId: String,
                    program: DeviceDosingProgram,
                    origin: DeviceDosingProgramMutationOrigin
                ): DeviceDosingChannelOperationResult {
                    check(deviceUid == DEVICE_UID)
                    check(slotId == SLOT_ID)
                    attempts += program to origin
                    return DeviceDosingChannelOperationResult.Rejected(
                        DeviceDosingChannelRejection.CONFLICT
                    )
                }
            }
            val viewModel = DeviceDosingPlanViewModel(operations)

            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setDailyDoseMicroliters(9_000L)
            val expectedBaseRevision = viewModel.currentEditorState.baseRevision
            val expectedBaseProgram = viewModel.currentEditorState.baseProgram
            viewModel.save()

            assertEquals(listOf(expectedBaseRevision), attempts.map { it.second.revision })
            assertEquals(
                listOf(expectedBaseProgram),
                attempts.map { it.second.baseProgram }
            )
            assertEquals(
                DeviceDosingPlanEvent.SaveRejected(DeviceDosingChannelRejection.CONFLICT),
                viewModel.events.first()
            )
            assertFalse(viewModel.currentEditorState.operationInProgress)
        }

    @Test
    fun `exhausted program conflict reloads the final authoritative program`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val authoritativeProgram = requireNotNull(initial.program).copy(
                weekdays = listOf(true, false, true, false, true, false, true),
                schedule = DeviceDosingProgramSchedule.Single(
                    dailyDoseMicroliters = 4_000L,
                    startTimeMillis = 32_400_000L
                )
            )
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingPlanViewModel(operations)

            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setDailyDoseMicroliters(12_000L)
            val staleProgram = viewModel.currentEditorState.programIntent

            operations.snapshot.value = initial.copy(
                revision = 11L,
                program = authoritativeProgram
            )
            operations.forceRevisionConflicts = true

            assertEquals(10L, viewModel.currentEditorState.baseRevision)
            assertEquals(staleProgram, viewModel.currentEditorState.programIntent)
            assertTrue(viewModel.currentEditorState.draftDirty)

            viewModel.save()

            assertEquals(
                DeviceDosingPlanEvent.SaveRejected(DeviceDosingChannelRejection.CONFLICT),
                viewModel.events.first()
            )
            val reconciled = viewModel.currentEditorState
            assertEquals(11L, reconciled.baseRevision)
            assertEquals(authoritativeProgram, reconciled.programIntent)
            assertFalse(reconciled.draftDirty)
            assertFalse(reconciled.operationInProgress)
            assertNull(operations.lastProgram)
        }

    @Test
    fun `unrelated reservoir revision advances plan base and saves in one attempt`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingPlanViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setDailyDoseMicroliters(9_000L)

            operations.snapshot.value = initial.copy(
                revision = 11L,
                reservoir = initial.reservoir.copy(
                    remainingMicroliters = 200_000L
                )
            )

            assertEquals(11L, viewModel.currentEditorState.baseRevision)
            assertTrue(viewModel.currentEditorState.draftDirty)

            viewModel.save()

            assertEquals(DeviceDosingPlanEvent.Saved, viewModel.events.first())
            assertEquals(1, operations.programMutationCount)
            assertEquals(11L, operations.lastProgramOrigin?.revision)
            assertEquals(11L, operations.lastProgramAppliedRevision)
            assertEquals(
                9_000L,
                (requireNotNull(operations.lastProgram).schedule as
                    DeviceDosingProgramSchedule.Single).dailyDoseMicroliters
            )
            assertFalse(viewModel.currentEditorState.operationInProgress)
            assertFalse(viewModel.currentEditorState.draftDirty)
        }

    @Test
    fun `restored dirty draft retains its complete origin across an unrelated revision`() =
        runTest(dispatcher) {
            val original = sampleDosingChannelSnapshot().copy(revision = 10L)
            val current = original.copy(
                revision = 11L,
                reservoir = original.reservoir.copy(remainingMicroliters = 200_000L)
            )
            val baseProgram = requireNotNull(original.program)
            val restoredDraft = baseProgram.toPlanDraft().copy(
                distributedDailyDoseMicroliters = 9_000L
            )
            val operations = FakeDeviceDosingChannelOperations(current)
            val viewModel = DeviceDosingPlanViewModel(operations)

            viewModel.bind(
                deviceUidText = DEVICE_UID,
                slotIdText = SLOT_ID,
                restoredState = DosingPlanRestoreState(
                    draft = restoredDraft,
                    baseRevision = 10L,
                    baseProgram = baseProgram,
                    baseProgramKnown = true,
                    draftDirty = true
                )
            )

            assertTrue(viewModel.currentEditorState.canSave)
            assertTrue(viewModel.currentEditorState.draftDirty)
            assertEquals(11L, viewModel.currentEditorState.baseRevision)
            assertEquals(9_000L, viewModel.currentEditorState.draft.distributedDailyDoseMicroliters)

            viewModel.save()

            assertEquals(DeviceDosingPlanEvent.Saved, viewModel.events.first())
            assertEquals(11L, operations.lastProgramOrigin?.revision)
            assertEquals(
                9_000L,
                (requireNotNull(operations.lastProgram).schedule as
                    DeviceDosingProgramSchedule.Single).dailyDoseMicroliters
            )
        }

    @Test
    fun `legacy dirty restore without an origin reloads instead of deadlocking save`() =
        runTest(dispatcher) {
            val current = sampleDosingChannelSnapshot().copy(revision = 11L)
            val operations = FakeDeviceDosingChannelOperations(current)
            val viewModel = DeviceDosingPlanViewModel(operations)

            viewModel.bind(
                deviceUidText = DEVICE_UID,
                slotIdText = SLOT_ID,
                restoredState = DosingPlanRestoreState(
                    draft = requireNotNull(current.program).toPlanDraft().copy(
                        distributedDailyDoseMicroliters = 9_000L
                    ),
                    baseRevision = 10L,
                    draftDirty = true
                )
            )

            assertTrue(viewModel.currentEditorState.canSave)
            assertFalse(viewModel.currentEditorState.draftDirty)
            assertEquals(11L, viewModel.currentEditorState.baseRevision)
            assertEquals(
                current.program,
                viewModel.currentEditorState.programIntent
            )
        }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel2"
    }
}

private fun stage10Programs(): List<DeviceDosingProgram> {
    val weekdays = listOf(true, false, true, false, true, false, true)
    return listOf(
        DeviceDosingProgram(
            enabled = true,
            weekdays = weekdays,
            schedule = DeviceDosingProgramSchedule.Single(3_000L, 28_800_000L),
            missedDoseRecoveryEnabled = true
        ),
        DeviceDosingProgram(
            enabled = false,
            weekdays = weekdays,
            schedule = DeviceDosingProgramSchedule.Hourly24(24_000L, 3_600_000L),
            missedDoseRecoveryEnabled = false
        ),
        DeviceDosingProgram(
            enabled = true,
            weekdays = weekdays,
            schedule = DeviceDosingProgramSchedule.CustomPeriods(
                dailyDoseMicroliters = 4_000L,
                periods = listOf(
                    DeviceDosingCustomPeriodDraft(0L, 3_600_000L, 2),
                    DeviceDosingCustomPeriodDraft(7_200_000L, 10_800_000L, 2)
                )
            ),
            missedDoseRecoveryEnabled = true
        ),
        DeviceDosingProgram(
            enabled = true,
            weekdays = weekdays,
            schedule = DeviceDosingProgramSchedule.Timer(
                listOf(
                    DeviceDosingTimerDoseDraft(0L, 1_000L),
                    DeviceDosingTimerDoseDraft(3_600_000L, 2_000L)
                )
            ),
            missedDoseRecoveryEnabled = false
        )
    )
}
