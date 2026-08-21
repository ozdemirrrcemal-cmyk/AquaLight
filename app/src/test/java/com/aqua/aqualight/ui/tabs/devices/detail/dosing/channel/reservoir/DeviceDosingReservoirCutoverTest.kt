package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.reservoir

import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirCapacityPolicy
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.FakeDeviceDosingChannelOperations
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.sampleDosingChannelSnapshot
import java.util.Locale
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
    fun `save is enabled only for a real reservoir change and returns disabled when reverted`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations()
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)

            assertFalse(viewModel.currentEditorState().canSave)
            viewModel.setCapacityInput("500", Locale.US)
            assertTrue(viewModel.currentEditorState().canSave)
            assertFalse(viewModel.currentEditorState().canRefill)

            viewModel.setCapacityInput("450", Locale.US)
            assertFalse(viewModel.currentEditorState().canSave)
            assertTrue(viewModel.currentEditorState().canRefill)

            viewModel.setCapacityInput("500", Locale.US)
            viewModel.save()

            assertEquals(1, operations.reservoirConfigMutationCount)
            assertEquals(1L, operations.lastReservoirExpectedRevision)
            assertEquals(500_000L, operations.lastReservoirSettings?.capacityMicroliters)
            assertEquals(500_000L, viewModel.currentEditorState().remainingMicroliters)
            assertFalse(viewModel.currentEditorState().canSave)
            assertFalse(viewModel.currentEditorState().canRefill)
        }

    @Test
    fun `alert only save never reapplies firmware reservoir baseline`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        val viewModel = DeviceDosingReservoirViewModel(operations)
        viewModel.bind(DEVICE_UID, SLOT_ID)

        assertEquals(250_000L, viewModel.currentEditorState().remainingMicroliters)
        viewModel.setLowLevelAlertEnabled(true)
        assertTrue(viewModel.currentEditorState().canSave)
        viewModel.save()

        assertEquals(0, operations.reservoirConfigMutationCount)
        assertEquals(1, operations.lowLevelAlertMutationCount)
        assertEquals(250_000L, viewModel.currentEditorState().remainingMicroliters)
        assertTrue(viewModel.currentEditorState().draft.lowLevelAlertEnabled)
        assertFalse(viewModel.currentEditorState().canSave)
    }

    @Test
    fun `refill requires authoritative tracking clean config and a non-full or uncertain level`() =
        runTest(dispatcher) {
            val operations = FakeDeviceDosingChannelOperations()
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)

            assertTrue(viewModel.currentEditorState().canRefill)
            viewModel.setCapacityInput("500", Locale.US)
            assertFalse(viewModel.currentEditorState().canRefill)
            viewModel.setCapacityInput("450", Locale.US)
            assertTrue(viewModel.currentEditorState().canRefill)

            operations.snapshot.value = requireNotNull(operations.snapshot.value).copy(
                reservoir = requireNotNull(operations.snapshot.value).reservoir.copy(
                    remainingMicroliters = 450_000L,
                    accountingCertain = true
                )
            )
            assertFalse(viewModel.currentEditorState().canRefill)

            operations.snapshot.value = requireNotNull(operations.snapshot.value).copy(
                reservoir = requireNotNull(operations.snapshot.value).reservoir.copy(
                    remainingMicroliters = 300_000L,
                    accountingCertain = false
                )
            )
            assertTrue(viewModel.currentEditorState().canRefill)
            viewModel.refill()

            assertEquals(1, operations.refillMutationCount)
            assertEquals(450_000L, viewModel.currentEditorState().remainingMicroliters)
            assertTrue(viewModel.currentEditorState().remainingAccountingCertain)
            assertFalse(viewModel.currentEditorState().canRefill)
        }

    @Test
    fun `a new reservoir editor discards an unsaved capacity draft`() = runTest(dispatcher) {
        val operations = FakeDeviceDosingChannelOperations()
        DeviceDosingReservoirViewModel(operations).also { first ->
            first.bind(DEVICE_UID, SLOT_ID)
            first.setCapacityInput("700", Locale.US)
            assertEquals(700_000L, first.currentDraft().reservoirCapacityMicroliters)
            assertTrue(first.currentEditorState().canSave)
        }

        val reopened = DeviceDosingReservoirViewModel(operations)
        reopened.bind(DEVICE_UID, SLOT_ID)

        assertEquals(450_000L, reopened.currentDraft().reservoirCapacityMicroliters)
        assertEquals(250_000L, reopened.currentEditorState().remainingMicroliters)
        assertFalse(reopened.currentEditorState().canSave)
    }

    @Test
    fun `authoritative empty reservoir formats as zero instead of default capacity`() {
        assertEquals("0", DeviceDosingReservoirCapacityPolicy.formatRuntimeVolume(0L, Locale.US))
    }

    @Test
    fun `unrelated channel revision is absorbed and reservoir saves with one user action`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setCapacityInput("500", Locale.US)

            operations.snapshot.value = initial.copy(
                revision = 11L,
                program = requireNotNull(initial.program).copy(
                    missedDoseRecoveryEnabled = true
                )
            )

            assertEquals(11L, viewModel.currentEditorState().baseRevision)
            assertTrue(viewModel.currentEditorState().firmwareConfigDirty)

            viewModel.save()

            assertEquals(DeviceDosingReservoirEvent.Saved, viewModel.events.first())
            assertEquals(1, operations.reservoirConfigMutationCount)
            assertEquals(11L, operations.lastReservoirExpectedRevision)
            assertEquals(500_000L, operations.lastReservoirSettings?.capacityMicroliters)
            assertFalse(viewModel.currentEditorState().operationInProgress)
            assertFalse(viewModel.currentEditorState().dirty)
        }

    @Test
    fun `already applied reservoir intent is accepted without a duplicate firmware write`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setCapacityInput("500", Locale.US)

            operations.snapshot.value = initial.copy(
                revision = 11L,
                reservoir = initial.reservoir.copy(
                    capacityMicroliters = 500_000L,
                    remainingMicroliters = 500_000L
                )
            )
            assertEquals(10L, viewModel.currentEditorState().baseRevision)

            viewModel.save()

            assertEquals(DeviceDosingReservoirEvent.Saved, viewModel.events.first())
            assertEquals(0, operations.reservoirConfigMutationCount)
            assertEquals(11L, viewModel.currentEditorState().baseRevision)
            assertFalse(viewModel.currentEditorState().dirty)
        }

    @Test
    fun `real concurrent reservoir change remains a conflict and is never overwritten`() =
        runTest(dispatcher) {
            val initial = sampleDosingChannelSnapshot().copy(revision = 10L)
            val operations = FakeDeviceDosingChannelOperations(initial)
            val viewModel = DeviceDosingReservoirViewModel(operations)
            viewModel.bind(DEVICE_UID, SLOT_ID)
            viewModel.setCapacityInput("500", Locale.US)

            operations.snapshot.value = initial.copy(
                revision = 11L,
                reservoir = initial.reservoir.copy(
                    capacityMicroliters = 600_000L,
                    remainingMicroliters = 600_000L
                )
            )

            viewModel.save()

            assertEquals(
                DeviceDosingReservoirEvent.SaveRejected(DeviceDosingChannelRejection.CONFLICT),
                viewModel.events.first()
            )
            assertEquals(0, operations.reservoirConfigMutationCount)
            assertEquals(500_000L, viewModel.currentDraft().reservoirCapacityMicroliters)
            assertEquals(600_000L, viewModel.currentEditorState().savedDraft
                .reservoirCapacityMicroliters)
            assertEquals(11L, viewModel.currentEditorState().baseRevision)
            assertFalse(viewModel.currentEditorState().operationInProgress)
        }

    private companion object {
        const val DEVICE_UID = "device-1"
        const val SLOT_ID = "dosing:channel2"
    }
}
