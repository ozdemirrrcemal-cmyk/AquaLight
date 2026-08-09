package com.aqua.aqualight.ui.tabs.devices.detail.dosing.calibration

import com.aqua.aqualight.application.devices.DeviceDosingCalibrationCandidate
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationChannelSnapshot
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.DeviceDosingCalibrationRun
import com.aqua.aqualight.application.devices.DeviceDosingVerificationRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class DeviceDosingCalibrationViewModelTest {

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
    fun `six step calibration uses firmware operations in order`() = runTest(dispatcher) {
        val operations = FakeCalibrationOperations()
        val viewModel = DeviceDosingCalibrationViewModel(operations)

        viewModel.bind(DEVICE_UID, CHANNEL_KEY)
        advanceUntilIdle()

        assertEquals(DosingCalibrationStep.NAME, viewModel.uiState.value.step)
        assertEquals(CHANNEL_NAME, viewModel.uiState.value.displayNameInput)

        viewModel.dispatch(DosingCalibrationAction.NameChanged(LIQUID_NAME))
        viewModel.dispatch(DosingCalibrationAction.ContinueName)
        advanceUntilIdle()
        assertEquals(DosingCalibrationStep.PRIME, viewModel.uiState.value.step)
        assertEquals(1, operations.displayNameUpdates)

        viewModel.dispatch(DosingCalibrationAction.PrimePressed)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.primeActive)
        assertEquals(1, operations.primeStarts)

        viewModel.dispatch(DosingCalibrationAction.PrimeReleased)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.primeActive)
        assertEquals(1, operations.primeStops)

        viewModel.dispatch(DosingCalibrationAction.ContinuePrime)
        assertEquals(DosingCalibrationStep.CALIBRATION_DOSE, viewModel.uiState.value.step)

        viewModel.dispatch(DosingCalibrationAction.StartCalibrationDose)
        advanceUntilIdle()
        assertEquals(DosingCalibrationStep.MEASURE, viewModel.uiState.value.step)
        assertEquals(1, operations.calibrationStarts)

        viewModel.dispatch(DosingCalibrationAction.MeasuredVolumeChanged("3,25"))
        viewModel.dispatch(DosingCalibrationAction.SubmitMeasuredVolume)
        advanceUntilIdle()
        assertEquals(DosingCalibrationStep.VERIFY_DOSE, viewModel.uiState.value.step)
        assertEquals(3.25, operations.measuredMl, DOUBLE_TOLERANCE)

        viewModel.dispatch(DosingCalibrationAction.StartVerificationDose)
        advanceUntilIdle()
        assertEquals(DosingCalibrationStep.CONFIRM, viewModel.uiState.value.step)
        assertEquals(
            DosingCalibrationPolicy.VERIFICATION_DOSE_ML,
            operations.verificationMl,
            DOUBLE_TOLERANCE
        )
        assertEquals(1, operations.verificationStarts)

        val completedEvent = async { viewModel.events.first() }
        viewModel.dispatch(DosingCalibrationAction.ConfirmCalibration)
        advanceUntilIdle()

        assertEquals(DosingCalibrationEvent.Completed, completedEvent.await())
        assertEquals(1, operations.confirms)
    }

    @Test
    fun `release while prime start is pending still sends firmware stop`() = runTest(dispatcher) {
        val operations = FakeCalibrationOperations()
        val viewModel = DeviceDosingCalibrationViewModel(operations)

        viewModel.bind(DEVICE_UID, CHANNEL_KEY)
        advanceUntilIdle()
        viewModel.dispatch(DosingCalibrationAction.ContinueName)
        advanceUntilIdle()

        viewModel.dispatch(DosingCalibrationAction.PrimePressed)
        viewModel.dispatch(DosingCalibrationAction.PrimeReleased)
        advanceUntilIdle()

        assertEquals(1, operations.primeStarts)
        assertEquals(1, operations.primeStops)
        assertFalse(viewModel.uiState.value.primeActive)
    }

    @Test
    fun `invalid measurement never reaches firmware`() = runTest(dispatcher) {
        val operations = FakeCalibrationOperations()
        val viewModel = DeviceDosingCalibrationViewModel(operations)

        viewModel.bind(DEVICE_UID, CHANNEL_KEY)
        advanceUntilIdle()
        viewModel.dispatch(DosingCalibrationAction.ContinueName)
        advanceUntilIdle()
        viewModel.dispatch(DosingCalibrationAction.ContinuePrime)
        viewModel.dispatch(DosingCalibrationAction.StartCalibrationDose)
        advanceUntilIdle()

        viewModel.dispatch(DosingCalibrationAction.MeasuredVolumeChanged("0"))
        viewModel.dispatch(DosingCalibrationAction.SubmitMeasuredVolume)
        advanceUntilIdle()

        assertEquals(0, operations.calibrationFinishes)
        assertEquals(DosingCalibrationOperation.ERROR, viewModel.uiState.value.operation)
    }

    @Test
    fun `decimal parser accepts comma and rejects non finite values`() {
        assertEquals(3.25, parseCalibrationDecimal(" 3,25 ") ?: 0.0, DOUBLE_TOLERANCE)
        assertEquals(4.5, parseCalibrationDecimal("4.5") ?: 0.0, DOUBLE_TOLERANCE)
        assertEquals(null, parseCalibrationDecimal("NaN"))
        assertEquals(null, parseCalibrationDecimal("Infinity"))
        assertEquals(null, parseCalibrationDecimal(""))
    }

    private companion object {
        const val DEVICE_UID = "dose-pro-test"
        const val CHANNEL_KEY = "pump_1"
        const val CHANNEL_NAME = "Channel 1"
        const val LIQUID_NAME = "All For Reef"
        const val DOUBLE_TOLERANCE = 0.0001
    }
}

private class FakeCalibrationOperations : DeviceDosingCalibrationOperations {
    var displayNameUpdates = 0
    var primeStarts = 0
    var primeStops = 0
    var calibrationStarts = 0
    var calibrationFinishes = 0
    var verificationStarts = 0
    var confirms = 0
    var measuredMl = 0.0
    var verificationMl = 0.0

    private var displayName = "Channel 1"

    override suspend fun loadChannel(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = Result.success(snapshot())

    override suspend fun updateDisplayName(
        deviceUid: String,
        channelKey: String,
        displayName: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> {
        displayNameUpdates += 1
        this.displayName = displayName
        return Result.success(snapshot())
    }

    override suspend fun startPrime(deviceUid: String, channelKey: String): Result<Unit> {
        primeStarts += 1
        return Result.success(Unit)
    }

    override suspend fun stopPrime(deviceUid: String, channelKey: String): Result<Unit> {
        primeStops += 1
        return Result.success(Unit)
    }

    override suspend fun startCalibrationDose(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationRun> {
        calibrationStarts += 1
        return Result.success(DeviceDosingCalibrationRun(durationMs = CALIBRATION_DURATION_MS))
    }

    override suspend fun finishCalibrationDose(
        deviceUid: String,
        channelKey: String,
        measuredMl: Double
    ): Result<DeviceDosingCalibrationCandidate> {
        calibrationFinishes += 1
        this.measuredMl = measuredMl
        return Result.success(
            DeviceDosingCalibrationCandidate(
                measuredMl = measuredMl,
                durationMs = CALIBRATION_DURATION_MS,
                pendingDoseMsPerMl = PENDING_DOSE_MS_PER_ML
            )
        )
    }

    override suspend fun startVerificationDose(
        deviceUid: String,
        channelKey: String,
        amountMl: Double
    ): Result<DeviceDosingVerificationRun> {
        verificationStarts += 1
        verificationMl = amountMl
        return Result.success(
            DeviceDosingVerificationRun(
                amountMl = amountMl,
                durationMs = VERIFICATION_DURATION_MS
            )
        )
    }

    override suspend fun stopVerificationDose(deviceUid: String, channelKey: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun confirmCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> {
        confirms += 1
        return Result.success(snapshot(calibrated = true))
    }

    override suspend fun cancelCalibration(
        deviceUid: String,
        channelKey: String
    ): Result<DeviceDosingCalibrationChannelSnapshot> = Result.success(snapshot())

    private fun snapshot(calibrated: Boolean = false) = DeviceDosingCalibrationChannelSnapshot(
        pumpCount = 4,
        channelNumber = 1,
        channelKey = "pump_1",
        displayName = displayName,
        calibrated = calibrated,
        calibrationEditable = true,
        supportsPrime = true,
        supportsManualDose = true,
        minimumMeasuredMl = 0.05,
        maximumMeasuredMl = 1000.0,
        maximumVerificationDoseMl = 1000.0
    )

    private companion object {
        const val CALIBRATION_DURATION_MS = 5_000L
        const val VERIFICATION_DURATION_MS = 4_000L
        const val PENDING_DOSE_MS_PER_ML = 1_538L
    }
}
