package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.FakeDeviceDosingChannelOperations
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DeviceDosingReservoirCutoverTest {
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
    fun `authoritative reservoir state loads and save uses central channel mutation`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations()
            val viewModel = DeviceDosingReservoirViewModel(operations)

            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)

            val initial = viewModel.currentEditorState()
            assertTrue(initial.initialized)
            assertTrue(initial.draft.trackingEnabled)
            assertEquals(450_000L, initial.draft.reservoirCapacityMicroliters)
            assertEquals(250_000L, initial.remainingMicroliters)
            assertFalse(initial.dirty)

            viewModel.setCapacityInput("500", Locale.US)
            viewModel.setLowLevelAlertEnabled(true)
            assertTrue(viewModel.currentEditorState().canSave)

            viewModel.save()

            assertEquals(500_000L, operations.lastReservoirSettings?.capacityMicroliters)
            assertEquals(true, operations.lastReservoirSettings?.lowLevelAlertEnabled)
            assertFalse(viewModel.currentEditorState().dirty)
        }

    @Test
    fun `refill delegates to central runtime mutation and refreshes remaining volume`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations()
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = null)

            assertEquals(250_000L, viewModel.currentEditorState().remainingMicroliters)
            viewModel.refill()

            assertEquals(450_000L, viewModel.currentEditorState().remainingMicroliters)
            assertFalse(viewModel.currentEditorState().reservoirNeedsAttention)
        }

    @Test
    fun `process recreation restores unsaved draft without replacing authoritative remaining`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations()
            val restored = DeviceDosingReservoirDraft(
                reservoirCapacityMicroliters = 700_000L,
                trackingEnabled = true,
                lowLevelAlertEnabled = true
            )
            val viewModel = DeviceDosingReservoirViewModel(operations)

            viewModel.bind(DEVICE_UID, SLOT_ID, restoredDraft = restored)

            val state = viewModel.currentEditorState()
            assertEquals(restored, state.draft)
            assertEquals(250_000L, state.remainingMicroliters)
            assertTrue(state.dirty)
            assertTrue(state.canSave)
        }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel2"
    }
}
