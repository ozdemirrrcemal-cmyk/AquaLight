package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
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

                viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)
                assertEquals(program, viewModel.currentEditorState.programIntent)

                viewModel.save()

                assertEquals(program, operations.lastProgram)
                assertEquals(program, viewModel.currentEditorState.programIntent)
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
            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)

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
    fun `persistent program conflict performs only one bounded reapply`() = runTest(dispatcher) {
        val delegate = FakeDeviceDosingChannelOperations()
        val attempts = mutableListOf<Pair<DeviceDosingProgram, Long>>()
        val operations = object :
            DeviceDosingChannelOperations by delegate,
            DeviceDosingProgramRevisionOperations {
            override suspend fun applyProgramAtRevision(
                deviceUid: String,
                slotId: String,
                program: DeviceDosingProgram,
                expectedRevision: Long
            ): DeviceDosingChannelOperationResult {
                check(deviceUid == DEVICE_UID)
                check(slotId == SLOT_ID)
                attempts += program to expectedRevision
                return DeviceDosingChannelOperationResult.Rejected(
                    DeviceDosingChannelRejection.CONFLICT
                )
            }
        }
        val viewModel = DeviceDosingPlanViewModel(operations)

        viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)
        viewModel.setDailyDoseMicroliters(9_000L)
        val expectedBaseRevision = viewModel.currentEditorState.baseRevision
        viewModel.save()

        assertEquals(2, attempts.size)
        assertEquals(listOf(expectedBaseRevision, expectedBaseRevision), attempts.map { it.second })
        assertEquals(
            DeviceDosingPlanEvent.SaveRejected(DeviceDosingChannelRejection.CONFLICT),
            viewModel.events.first()
        )
        assertFalse(viewModel.currentEditorState.operationInProgress)
    }

    @Test
    fun `program conflict reloads authoritative program instead of rebasing stale draft`() =
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

            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)
            viewModel.setDailyDoseMicroliters(12_000L)
            val staleProgram = viewModel.currentEditorState.programIntent

            operations.snapshot.value = initial.copy(
                revision = 11L,
                program = authoritativeProgram
            )

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

            viewModel.save()

            assertEquals(authoritativeProgram, operations.lastProgram)
        }

    @Test
    fun `unrelated reservoir revision advances plan base and saves in one attempt`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingPlanViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)
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
            assertEquals(11L, operations.lastProgramExpectedRevision)
            assertEquals(
                9_000L,
                (requireNotNull(operations.lastProgram).schedule as
                    DeviceDosingProgramSchedule.Single).dailyDoseMicroliters
            )
            assertFalse(viewModel.currentEditorState.operationInProgress)
            assertFalse(viewModel.currentEditorState.draftDirty)
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
