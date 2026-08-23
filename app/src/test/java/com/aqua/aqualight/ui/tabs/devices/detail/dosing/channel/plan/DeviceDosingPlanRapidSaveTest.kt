package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramMutationOrigin
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramRevisionOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.FakeDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
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
class DeviceDosingPlanRapidSaveTest {
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
    fun `editor accepts a newer draft and save while an older waiter is syncing`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val initialProgram = requireNotNull(initial.program)
            val delegate = FakeDeviceDosingChannelOperations(initial)
            val firstEntered = CompletableDeferred<Unit>()
            val attempts = mutableListOf<Boolean>()
            val operations = object :
                DeviceDosingChannelOperations by delegate,
                DeviceDosingProgramRevisionOperations {
                override suspend fun applyProgramAtOrigin(
                    deviceUid: String,
                    slotId: String,
                    program: DeviceDosingProgram,
                    origin: DeviceDosingProgramMutationOrigin
                ): DeviceDosingChannelOperationResult {
                    attempts += program.enabled
                    if (attempts.size == 1) {
                        firstEntered.complete(Unit)
                        awaitCancellation()
                    }
                    return DeviceDosingChannelCommittedResult(origin.revision + 1L)
                }
            }
            val viewModel = DeviceDosingPlanViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)

            viewModel.setScheduleEnabled(!initialProgram.enabled)
            viewModel.save()
            firstEntered.await()

            assertTrue(viewModel.currentEditorState.operationInProgress)
            assertTrue(viewModel.currentEditorState.canSave)

            viewModel.setScheduleEnabled(initialProgram.enabled)
            assertEquals(initialProgram.enabled, viewModel.currentEditorState.programIntent.enabled)
            assertTrue(viewModel.currentEditorState.draftDirty)
            viewModel.save()

            assertEquals(DeviceDosingPlanEvent.Saved, viewModel.events.first())
            assertEquals(listOf(!initialProgram.enabled, initialProgram.enabled), attempts)
            assertFalse(viewModel.currentEditorState.operationInProgress)
            assertFalse(viewModel.currentEditorState.draftDirty)
        }

    private companion object {
        const val DEVICE_UID = "device-rapid-save"
        const val SLOT_ID = "dosing:channel2"
    }
}
