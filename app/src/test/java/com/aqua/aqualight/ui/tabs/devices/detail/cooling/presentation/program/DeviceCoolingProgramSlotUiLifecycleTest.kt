package com.aqua.aqualight.ui.tabs.devices.detail.cooling.presentation.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanOnTemperaturePolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanPolicy
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingProgramSlotUiLifecycleTest {
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
    fun presentationKeysSurviveEditAddAndDeleteIndexChanges() = runTest(dispatcher) {
        val first = slot(hour(8), hour(10), targetFanPercent = 50)
        val second = slot(hour(12), hour(14), targetFanPercent = 60)
        val viewModel = DeviceCoolingProgramSettingsViewModel(
            operations = RecordingCoolingProgramOperations(
                readResult = CoolingProgramReadResult.Loaded(
                    CoolingProgramSnapshot(listOf(first, second), policy())
                )
            )
        )

        viewModel.bind(DEVICE_UID)
        val initialItems = viewModel.uiState.value.slotItems
        val firstKey = initialItems[0].uiKey
        val secondKey = initialItems[1].uiKey

        viewModel.updateTargetFanPercent(SECOND_SLOT_INDEX, 73)
        viewModel.updateFanOnTemperature(SECOND_SLOT_INDEX, 25.6)
        val editedSecond = second.copy(
            targetFanPercent = 70,
            fanOnTemperatureC = 25.5
        )
        assertEquals(secondKey, viewModel.uiState.value.slotItems[SECOND_SLOT_INDEX].uiKey)

        viewModel.addTimeSlot()
        val addedItems = viewModel.uiState.value.slotItems
        assertEquals(firstKey, addedItems.first { item -> item.slot == first }.uiKey)
        assertEquals(secondKey, addedItems.first { item -> item.slot == editedSecond }.uiKey)
        assertEquals(addedItems.size, addedItems.map { item -> item.uiKey }.toSet().size)

        val addedIndex = addedItems.indexOfFirst { item ->
            item.uiKey != firstKey && item.uiKey != secondKey
        }
        assertTrue(addedIndex >= 0)
        assertTrue(viewModel.deleteTimeSlot(addedIndex))

        val remainingItems = viewModel.uiState.value.slotItems
        assertEquals(firstKey, remainingItems.first { item -> item.slot == first }.uiKey)
        assertEquals(secondKey, remainingItems.first { item -> item.slot == editedSecond }.uiKey)
    }

    @Test
    fun saveContractReceivesOnlyDomainSlotValues() = runTest(dispatcher) {
        val operations = RecordingCoolingProgramOperations(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy())
            )
        )
        val viewModel = DeviceCoolingProgramSettingsViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        val draftSlots = viewModel.uiState.value.slots
        assertTrue(viewModel.uiState.value.slotItems.isNotEmpty())

        viewModel.saveDraft()

        assertEquals(draftSlots, operations.lastSavedSlots)
    }

    private fun slot(
        startMinutes: Int,
        endMinutes: Int,
        targetFanPercent: Int
    ): CoolingProgramSlot = CoolingProgramSlot(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        fanOnTemperatureC = 25.0,
        targetFanPercent = targetFanPercent
    )

    private fun policy(): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = 6,
        timeStepMinutes = 15,
        minimumSlotDurationMinutes = 15,
        fan = CoolingProgramFanPolicy(
            minimumPercent = 0,
            maximumPercent = 100,
            stepPercent = 10
        ),
        fanOnTemperature = CoolingProgramFanOnTemperaturePolicy(
            minimumC = 15.0,
            maximumC = 40.0,
            stepC = 0.5,
            defaultC = 25.0
        )
    )

    private class RecordingCoolingProgramOperations(
        private val readResult: CoolingProgramReadResult,
        private val saveResult: CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable
    ) : DeviceCoolingProgramOperations {
        var lastSavedSlots: List<CoolingProgramSlot>? = null
            private set

        override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult = readResult

        override suspend fun saveProgram(
            deviceUid: String,
            slots: List<CoolingProgramSlot>
        ): CoolingProgramSaveResult {
            lastSavedSlots = slots
            return saveResult
        }
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"
        const val SECOND_SLOT_INDEX = 1
        const val MINUTES_PER_HOUR = 60

        fun hour(value: Int): Int = value * MINUTES_PER_HOUR
    }
}
