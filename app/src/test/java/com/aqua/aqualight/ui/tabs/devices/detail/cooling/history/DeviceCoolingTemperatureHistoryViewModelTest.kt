package com.aqua.aqualight.ui.tabs.devices.detail.cooling.history

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataFreshness
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.common.CoolingDataState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCoolingTemperatureHistoryViewModelTest {
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
    fun emptyHistoryIsSuccessfulEmptyNotUnavailable() = runTest(dispatcher) {
        val viewModel = viewModelWith(
            DeviceCoolingTemperatureHistoryLoadResult.Loaded(historySnapshot())
        )

        viewModel.bind(DEVICE_UID)

        assertTrue(viewModel.uiState.value.dataState is CoolingDataState.Empty)
        assertEquals(DeviceCoolingTemperatureHistoryLoadState.CONTENT, viewModel.uiState.value.loadState)
        assertNotNull(viewModel.uiState.value.snapshot)
    }

    @Test
    fun unsupportedHistoryRemainsDistinctFromUnavailable() = runTest(dispatcher) {
        val unsupported = viewModelWith(DeviceCoolingTemperatureHistoryLoadResult.Unsupported)
        unsupported.bind(DEVICE_UID)
        assertEquals(CoolingDataState.Unsupported, unsupported.uiState.value.dataState)
        assertNull(unsupported.uiState.value.snapshot)

        val unavailable = viewModelWith(DeviceCoolingTemperatureHistoryLoadResult.Unavailable)
        unavailable.bind(DEVICE_UID)
        assertEquals(CoolingDataState.Unavailable, unavailable.uiState.value.dataState)
        assertNull(unavailable.uiState.value.snapshot)
    }

    @Test
    fun unavailableRangeRefreshPreservesLastAuthoritativeSnapshotStale() = runTest(dispatcher) {
        val first = historySnapshot(
            range = DeviceCoolingTemperatureHistoryRange.HOURS_24,
            temperatures = listOf(24.8, 25.1)
        )
        val operations = SequenceHistoryOperations(
            listOf(
                DeviceCoolingTemperatureHistoryLoadResult.Loaded(first),
                DeviceCoolingTemperatureHistoryLoadResult.Unavailable
            )
        )
        val viewModel = DeviceCoolingTemperatureHistoryViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.selectRange(DeviceCoolingTemperatureHistoryRange.DAYS_7)

        val state = viewModel.uiState.value
        val data = state.dataState as CoolingDataState.Content<
            DeviceCoolingTemperatureHistorySnapshot,
            DeviceCoolingTemperatureHistoryFailure
            >
        assertEquals(CoolingDataFreshness.STALE, data.freshness)
        assertEquals(DeviceCoolingTemperatureHistoryFailure.UNAVAILABLE, data.refreshFailure)
        assertEquals(first, state.snapshot)
        assertEquals(DeviceCoolingTemperatureHistoryRange.HOURS_24, state.selectedRange)
        assertEquals(
            listOf(
                DeviceCoolingTemperatureHistoryRange.HOURS_24,
                DeviceCoolingTemperatureHistoryRange.DAYS_7
            ),
            operations.requestedRanges
        )
    }

    @Test
    fun successfulRangeRefreshAtomicallyReplacesPreviousSnapshot() = runTest(dispatcher) {
        val first = historySnapshot(
            range = DeviceCoolingTemperatureHistoryRange.HOURS_24,
            temperatures = listOf(24.8)
        )
        val second = historySnapshot(
            range = DeviceCoolingTemperatureHistoryRange.DAYS_7,
            temperatures = listOf(24.6, 25.2)
        )
        val viewModel = DeviceCoolingTemperatureHistoryViewModel(
            SequenceHistoryOperations(
                listOf(
                    DeviceCoolingTemperatureHistoryLoadResult.Loaded(first),
                    DeviceCoolingTemperatureHistoryLoadResult.Loaded(second)
                )
            )
        )

        viewModel.bind(DEVICE_UID)
        viewModel.selectRange(DeviceCoolingTemperatureHistoryRange.DAYS_7)

        val state = viewModel.uiState.value
        val data = state.dataState as CoolingDataState.Content<
            DeviceCoolingTemperatureHistorySnapshot,
            DeviceCoolingTemperatureHistoryFailure
            >
        assertEquals(CoolingDataFreshness.CURRENT, data.freshness)
        assertEquals(second, state.snapshot)
        assertEquals(DeviceCoolingTemperatureHistoryRange.DAYS_7, state.selectedRange)
    }

    private fun viewModelWith(
        result: DeviceCoolingTemperatureHistoryLoadResult
    ): DeviceCoolingTemperatureHistoryViewModel = DeviceCoolingTemperatureHistoryViewModel(
        SequenceHistoryOperations(listOf(result))
    )

    private class SequenceHistoryOperations(
        private val results: List<DeviceCoolingTemperatureHistoryLoadResult>
    ) : DeviceCoolingTemperatureHistoryOperations {
        private var resultIndex = 0
        val requestedRanges = mutableListOf<DeviceCoolingTemperatureHistoryRange>()

        override suspend fun loadTemperatureHistory(
            deviceUid: String,
            range: DeviceCoolingTemperatureHistoryRange
        ): DeviceCoolingTemperatureHistoryLoadResult {
            requestedRanges += range
            return results[resultIndex++]
        }
    }

    private companion object {
        const val DEVICE_UID = "cooling-device"

        fun historySnapshot(
            range: DeviceCoolingTemperatureHistoryRange =
                DeviceCoolingTemperatureHistoryRange.HOURS_24,
            temperatures: List<Double> = emptyList()
        ): DeviceCoolingTemperatureHistorySnapshot = DeviceCoolingTemperatureHistorySnapshot(
            range = range,
            generatedAtEpochMillis = 1L,
            minimumTemperatureC = temperatures.minOrNull(),
            averageTemperatureC = if (temperatures.isEmpty()) null else temperatures.average(),
            maximumTemperatureC = temperatures.maxOrNull(),
            points = temperatures.mapIndexed { index, temperature ->
                DeviceCoolingTemperatureHistoryPoint(
                    sampledAtEpochMillis = index.toLong(),
                    temperatureC = temperature
                )
            },
            dailySummaries = emptyList()
        )
    }
}
