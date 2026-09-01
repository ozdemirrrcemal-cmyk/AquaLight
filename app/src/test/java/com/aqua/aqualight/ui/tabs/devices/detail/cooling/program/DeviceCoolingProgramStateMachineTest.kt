package com.aqua.aqualight.ui.tabs.devices.detail.cooling.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanOnTemperaturePolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSnapshot
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingMutationState
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
class DeviceCoolingProgramStateMachineTest {
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
    fun loadedProgramWithNoPeriodsIsAuthoritativeEmptyAndStillEditable() = runTest(dispatcher) {
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy())
            )
        )

        viewModel.bind(DEVICE_UID)

        val state = viewModel.uiState.value
        assertTrue(state.dataState is CoolingDataState.Empty)
        assertEquals(DeviceCoolingProgramLoadState.CONTENT, state.loadState)
        assertTrue(state.canAddSlot)
    }

    @Test
    fun unavailableProgramNeverMasqueradesAsEmpty() = runTest(dispatcher) {
        val viewModel = createViewModel(CoolingProgramReadResult.Unavailable)

        viewModel.bind(DEVICE_UID)

        assertEquals(CoolingDataState.Unavailable, viewModel.uiState.value.dataState)
    }

    @Test
    fun invalidConfigurationSaveUsesValidationStateNotOperationError() = runTest(dispatcher) {
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy())
            ),
            saveResult = CoolingProgramSaveResult.InvalidConfiguration
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        viewModel.saveDraft()

        assertEquals(CoolingMutationState.ValidationError, viewModel.uiState.value.mutationState)
    }

    @Test
    fun unavailableSaveIsOperationErrorAndDoesNotAdvanceBaseline() = runTest(dispatcher) {
        val viewModel = createViewModel(
            readResult = CoolingProgramReadResult.Loaded(
                CoolingProgramSnapshot(emptyList(), policy())
            ),
            saveResult = CoolingProgramSaveResult.Unavailable
        )

        viewModel.bind(DEVICE_UID)
        viewModel.addTimeSlot()
        viewModel.saveDraft()

        val state = viewModel.uiState.value
        val failure = state.mutationState as CoolingMutationState.OperationError<
            DeviceCoolingProgramSaveFailure
            >
        assertEquals(DeviceCoolingProgramSaveFailure.UNAVAILABLE, failure.failure)
        assertTrue(state.baselineSlots.isEmpty())
        assertTrue(state.hasChanges)
    }

    private fun createViewModel(
        readResult: CoolingProgramReadResult,
        saveResult: CoolingProgramSaveResult = CoolingProgramSaveResult.Unavailable
    ): DeviceCoolingProgramSettingsViewModel = DeviceCoolingProgramSettingsViewModel(
        object : DeviceCoolingProgramOperations {
            override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult = readResult

            override suspend fun saveProgram(
                deviceUid: String,
                slots: List<CoolingProgramSlot>
            ): CoolingProgramSaveResult = saveResult
        }
    )

    private fun policy(): CoolingProgramPolicy = CoolingProgramPolicy(
        maximumSlotCount = 6,
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

    private companion object {
        const val DEVICE_UID = "cooling-device"
    }
}
