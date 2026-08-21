package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelCommittedResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramRevisionOperations
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingPlanCommittedSaveTest {
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
    fun `committed save advances editor revision without requiring authoritative readback`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val delegate = FakeDeviceDosingChannelOperations(initial)
            val attempts = mutableListOf<Long>()
            val operations = object :
                DeviceDosingChannelOperations by delegate,
                DeviceDosingProgramRevisionOperations {
                override suspend fun applyProgramAtRevision(
                    deviceUid: String,
                    slotId: String,
                    program: DeviceDosingProgram,
                    expectedRevision: Long
                ): DeviceDosingChannelOperationResult {
                    attempts += expectedRevision
                    return DeviceDosingChannelCommittedResult(expectedRevision + 1L)
                }
            }
            val viewModel = DeviceDosingPlanViewModel(operations)

            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)
            viewModel.setDailyDoseMicroliters(9_000L)
            assertEquals(10L, viewModel.currentEditorState.baseRevision)
            assertEquals(true, viewModel.currentEditorState.draftDirty)

            viewModel.save()

            assertEquals(DeviceDosingPlanEvent.Saved, viewModel.events.first())
            assertEquals(listOf(10L), attempts)
            assertEquals(11L, viewModel.currentEditorState.baseRevision)
            assertFalse(viewModel.currentEditorState.draftDirty)
            assertFalse(viewModel.currentEditorState.operationInProgress)
        }

    private companion object {
        const val DEVICE_UID = "device-committed-save"
        const val SLOT_ID = "dosing:channel2"
    }
}
