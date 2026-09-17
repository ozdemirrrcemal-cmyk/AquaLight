package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationPolicy
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightAdaptationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `disabled firmware state opens setup with policy defaults`() {
        val viewModel = DeviceLightAdaptationViewModel(FakeAdaptationOperations()).apply {
            bind(DEVICE_UID)
        }

        assertEquals(DeviceLightAdaptationScreenState.SETUP, viewModel.uiState.value.screenState)
        assertEquals(DEFAULT_START_PERCENT, viewModel.uiState.value.selectedStartPercent)
        assertEquals(DEFAULT_DURATION_DAYS, viewModel.uiState.value.selectedDurationDays)
        assertTrue(viewModel.uiState.value.canStart)
    }

    @Test
    fun `start sends current revision and stepped user settings`() {
        val operations = FakeAdaptationOperations()
        val viewModel = DeviceLightAdaptationViewModel(operations).apply { bind(DEVICE_UID) }

        viewModel.updateStartPercent(63)
        viewModel.updateDurationDays(21)
        viewModel.start()

        assertEquals(START_REVISION, operations.startedRevision)
        assertEquals(65, operations.startedPercent)
        assertEquals(21, operations.startedDurationDays)
        assertEquals(DeviceLightAdaptationScreenState.ACTIVE, viewModel.uiState.value.screenState)
    }

    @Test
    fun `clock not ready blocks start but active program remains stoppable`() {
        val operations = FakeAdaptationOperations(snapshot(clockReady = false))
        val viewModel = DeviceLightAdaptationViewModel(operations).apply { bind(DEVICE_UID) }

        assertFalse(viewModel.uiState.value.canStart)
        viewModel.start()
        assertEquals(null, operations.startedRevision)

        operations.publish(activeSnapshot(clockReady = false))

        assertTrue(viewModel.uiState.value.canStop)
        viewModel.stop()
        assertEquals(ACTIVE_REVISION, operations.stoppedRevision)
    }

    private class FakeAdaptationOperations(
        initial: DeviceLightAdaptationSnapshot = snapshot()
    ) : DeviceLightAdaptationOperations {
        private val state = MutableStateFlow<DeviceLightAdaptationReadResult>(
            DeviceLightAdaptationReadResult.Available(initial)
        )
        var startedRevision: Long? = null
        var startedPercent: Int? = null
        var startedDurationDays: Int? = null
        var stoppedRevision: Long? = null

        override fun observe(deviceUid: String): Flow<DeviceLightAdaptationReadResult> = state

        override fun current(deviceUid: String): DeviceLightAdaptationReadResult = state.value

        override suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult = state.value

        override suspend fun start(
            deviceUid: String,
            expectedRevision: Long,
            startPercent: Int,
            durationDays: Int
        ): DeviceLightAdaptationMutationResult {
            startedRevision = expectedRevision
            startedPercent = startPercent
            startedDurationDays = durationDays
            val active = activeSnapshot().copy(
                startPercent = startPercent,
                durationDays = durationDays
            )
            publish(active)
            return DeviceLightAdaptationMutationResult.Success(active)
        }

        override suspend fun stop(
            deviceUid: String,
            expectedRevision: Long
        ): DeviceLightAdaptationMutationResult {
            stoppedRevision = expectedRevision
            val disabled = snapshot().copy(revision = expectedRevision + 1L)
            publish(disabled)
            return DeviceLightAdaptationMutationResult.Success(disabled)
        }

        fun publish(snapshot: DeviceLightAdaptationSnapshot) {
            state.value = DeviceLightAdaptationReadResult.Available(snapshot)
        }
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private companion object {
        const val DEVICE_UID = "adaptation-light"
        const val START_REVISION = 8L
        const val ACTIVE_REVISION = 9L
        const val DEFAULT_START_PERCENT = 50
        const val DEFAULT_DURATION_DAYS = 30

        val POLICY = DeviceLightAdaptationPolicy(
            startPercentMin = 20,
            startPercentMax = 90,
            startPercentStep = 5,
            defaultStartPercent = DEFAULT_START_PERCENT,
            durationDaysMin = 7,
            durationDaysMax = 90,
            durationDaysStep = 1,
            defaultDurationDays = DEFAULT_DURATION_DAYS,
            targetPercent = 100
        )

        fun snapshot(clockReady: Boolean = true) = DeviceLightAdaptationSnapshot(
            deviceUid = DEVICE_UID,
            revision = START_REVISION,
            state = DeviceLightAdaptationState.DISABLED,
            clockReady = clockReady,
            startPercent = DEFAULT_START_PERCENT,
            currentPermille = 1_000,
            targetPercent = 100,
            durationDays = DEFAULT_DURATION_DAYS,
            startedAtEpochSeconds = 0,
            endsAtEpochSeconds = 0,
            remainingSeconds = 0,
            policy = POLICY,
            firmwareWriteAuthoritative = true
        )

        fun activeSnapshot(clockReady: Boolean = true) = snapshot(clockReady).copy(
            revision = ACTIVE_REVISION,
            state = DeviceLightAdaptationState.ACTIVE,
            currentPermille = 684,
            startedAtEpochSeconds = 1_788_710_400,
            endsAtEpochSeconds = 1_791_302_400,
            remainingSeconds = 19 * 86_400L
        )
    }
}
