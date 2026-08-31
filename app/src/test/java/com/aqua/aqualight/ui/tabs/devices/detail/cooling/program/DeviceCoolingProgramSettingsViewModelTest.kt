package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import com.aqua.aqualight.application.devices.cooling.CoolingProgramCapabilities
import com.aqua.aqualight.application.devices.cooling.CoolingProgramDraftSlotIdFactory
import com.aqua.aqualight.application.devices.cooling.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.CoolingProgramSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingProgramOperations
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
class DeviceCoolingProgramSettingsViewModelTest {
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
    fun unavailableProgramDoesNotExposeEditableFakeState() = runTest(dispatcher) {
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Unavailable
        )

        viewModel.bind(DEVICE_UID)

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingProgramLoadState.UNAVAILABLE, state.loadState)
        assertTrue(state.slots.isEmpty())
        assertFalse(state.hasChanges)
        assertFalse(state.canAddSlot)
        assertFalse(state.canSave)
    }

    @Test
    fun reportedCapabilitiesDriveFanSnappingAndSlotLimit() = runTest(dispatcher) {
        val capabilities = capabilities(maximumSlotCount = 1, fanLimitStepPercent = 10)
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), capabilities)
            )
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        viewModel.updateFanLimit(FIRST_SLOT_ID, 63)
        viewModel.addTimeSlot()

        assertEquals(1, viewModel.uiState.value.slots.size)
        assertEquals(60, viewModel.slot(FIRST_SLOT_ID).fanLimitPercent)
        assertFalse(viewModel.uiState.value.canAddSlot)
    }

    @Test
    fun crossMidnightEditIsRejected() = runTest(dispatcher) {
        val capabilities = capabilities()
        val original = CoolingProgramSlot(
            id = FIRST_SLOT_ID,
            startMinutes = hour(8),
            endMinutes = hour(14),
            fanLimitPercent = 60
        )
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(listOf(original), capabilities)
            )
        )

        viewModel.bind(DEVICE_UID)

        assertFalse(viewModel.updateEndTime(FIRST_SLOT_ID, hour(7)))
        assertEquals(hour(14), viewModel.slot(FIRST_SLOT_ID).endMinutes)
    }

    @Test
    fun unavailableSaveNeverMarksDraftAsPersisted() = runTest(dispatcher) {
        val capabilities = capabilities()
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), capabilities)
            ),
            saveResult = CoolingProgramSaveResult.Unavailable
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        assertTrue(viewModel.uiState.value.hasChanges)

        viewModel.saveDraft()

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingProgramSaveState.ERROR, state.saveState)
        assertTrue(state.hasChanges)
        assertTrue(state.persistedSlots.isEmpty())
    }

    private fun createViewModel(
        readResult: CoolingProgramReadResult,
        saveResult: CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable
    ): DeviceCoolingProgramSettingsViewModel = DeviceCoolingProgramSettingsViewModel(
        operations = FakeCoolingProgramOperations(readResult, saveResult),
        draftSlotIdFactory = SequenceSlotIdFactory()
    )

    private fun DeviceCoolingProgramSettingsViewModel.slot(slotId: String): CoolingProgramSlot =
        uiState.value.slots.first { slot -> slot.id == slotId }

    private fun capabilities(
        maximumSlotCount: Int = 6,
        fanLimitStepPercent: Int = 10
    ): CoolingProgramCapabilities = CoolingProgramCapabilities(
        minimumSlotCount = 0,
        maximumSlotCount = maximumSlotCount,
        minimumFanLimitPercent = 0,
        maximumFanLimitPercent = 100,
        fanLimitStepPercent = fanLimitStepPercent,
        minimumSlotDurationMinutes = 15
    )

    private class FakeCoolingProgramOperations(
        private val readResult: CoolingProgramReadResult,
        private val saveResult: CoolingProgramSaveResult
    ) : DeviceCoolingProgramOperations {
        override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult = readResult

        override suspend fun saveProgram(
            deviceUid: String,
            slots: List<CoolingProgramSlot>
        ): CoolingProgramSaveResult = saveResult
    }

    private class SequenceSlotIdFactory : CoolingProgramDraftSlotIdFactory {
        private var next = 1

        override fun create(): String = "slot-${next++}"
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"
        const val FIRST_SLOT_ID = "slot-1"
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}
