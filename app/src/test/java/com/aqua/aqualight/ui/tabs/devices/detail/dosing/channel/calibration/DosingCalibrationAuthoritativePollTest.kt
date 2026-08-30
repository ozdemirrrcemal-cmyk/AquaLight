package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.calibration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DosingCalibrationAuthoritativePollTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `expired verification reconciliation does not rearm on unchanged pending state`() =
        runTest(dispatcher) {
            val operations = FakeDosingCalibrationOperations(activeVerificationSnapshot())
            val viewModel = DeviceDosingChannelCalibrationViewModel(
                operations = operations,
                clock = DeviceDosingCalibrationClock { dispatcher.scheduler.currentTime }
            )

            viewModel.bind(
                calibrationRoute().copy(restoredDisplayNameDraft = "Trace Elements")
            )
            runCurrent()

            dispatcher.scheduler.advanceTimeBy(AUTHORITATIVE_POLL_DELAY_MS)
            runCurrent()

            val refreshesAfterDeadline = operations.refreshes
            val verificationStopsAfterDeadline = operations.verificationStops
            assertEquals(EXPECTED_REFRESHES_AFTER_DEADLINE, refreshesAfterDeadline)
            assertEquals(EXPECTED_VERIFICATION_STOPS_AFTER_DEADLINE, verificationStopsAfterDeadline)

            dispatcher.scheduler.advanceTimeBy(NO_REARM_WINDOW_MS)
            runCurrent()

            assertEquals(refreshesAfterDeadline, operations.refreshes)
            assertEquals(verificationStopsAfterDeadline, operations.verificationStops)
        }

    private companion object {
        const val AUTHORITATIVE_POLL_DELAY_MS = 250L
        const val NO_REARM_WINDOW_MS = 1_000L
        const val EXPECTED_REFRESHES_AFTER_DEADLINE = 2
        const val EXPECTED_VERIFICATION_STOPS_AFTER_DEADLINE = 1
    }
}
