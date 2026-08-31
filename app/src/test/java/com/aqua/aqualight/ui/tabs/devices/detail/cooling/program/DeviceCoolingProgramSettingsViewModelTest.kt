package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSnapshot
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
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
    fun unsupportedProgramPreservesCapabilitySemantics() = runTest(dispatcher) {
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Unsupported
        )

        viewModel.bind(DEVICE_UID)

        assertEquals(DeviceCoolingProgramLoadState.UNSUPPORTED, viewModel.uiState.value.loadState)
    }

    @Test
    fun reportedPolicyDrivesFanSnappingAndSlotLimit() = runTest(dispatcher) {
        val policy = policy(maximumSlotCount = 1, fanPercentStep = 10)
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy)
            )
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        viewModel.updateFanLimit(FIRST_SLOT_INDEX, 63)
        viewModel.addTimeSlot()

        assertEquals(1, viewModel.uiState.value.slots.size)
        assertEquals(60, viewModel.slot(FIRST_SLOT_INDEX).fanLimitPercent)
        assertFalse(viewModel.uiState.value.canAddSlot)
    }

    @Test
    fun sameDayEditRejectsEndBeforeStart() = runTest(dispatcher) {
        val original = CoolingProgramSlot(
            startMinutes = hour(8),
            endMinutes = hour(14),
            fanLimitPercent = 60
        )
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(listOf(original), policy())
            )
        )

        viewModel.bind(DEVICE_UID)

        assertFalse(viewModel.updateEndTime(FIRST_SLOT_INDEX, hour(7)))
        assertEquals(hour(14), viewModel.slot(FIRST_SLOT_INDEX).endMinutes)
    }

    @Test
    fun unavailableSaveNeverMarksDraftAsPersisted() = runTest(dispatcher) {
        val policy = policy()
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy)
            ),
            saveResult = CoolingProgramSaveResult.Unavailable
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        assertTrue(viewModel.uiState.value.hasChanges)

        viewModel.saveDraft()

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingProgramSaveState.UNAVAILABLE, state.saveState)
        assertTrue(state.hasChanges)
        assertTrue(state.baselineSlots.isEmpty())
    }

    @Test
    fun savedResultIsTheOnlyPathThatAdvancesPersistenceBaseline() = runTest(dispatcher) {
        val policy = policy()
        val persisted = CoolingProgramSlot(
            startMinutes = hour(8),
            endMinutes = hour(14),
            fanLimitPercent = 70
        )
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy)
            ),
            saveResult = CoolingProgramSaveResult.Saved(
                CoolingProgramSnapshot(listOf(persisted), policy)
            )
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        viewModel.saveDraft()

        val state = viewModel.uiState.value
        assertEquals(DeviceCoolingProgramSaveState.SAVED, state.saveState)
        assertEquals(listOf(persisted), state.slots)
        assertEquals(listOf(persisted), state.baselineSlots)
        assertFalse(state.hasChanges)
    }

    private fun createViewModel(
        readResult: CoolingProgramReadResult,
        saveResult: CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable
    ): DeviceCoolingProgramSettingsViewModel = DeviceCoolingProgramSettingsViewModel(
        operations = FakeCoolingProgramOperations(readResult, saveResult)
    )

    private fun DeviceCoolingProgramSettingsViewModel.slot(slotIndex: Int): CoolingProgramSlot =
        uiState.value.slots[slotIndex]

    private fun policy(
        maximumSlotCount: Int = 6,
        fanPercentStep: Int = 10
    ): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = maximumSlotCount,
        minimumFanPercent = 0,
        maximumFanPercent = 100,
        fanPercentStep = fanPercentStep,
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

    private companion object {
        const val DEVICE_UID = "cooling-device"
        const val FIRST_SLOT_INDEX = 0
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}
