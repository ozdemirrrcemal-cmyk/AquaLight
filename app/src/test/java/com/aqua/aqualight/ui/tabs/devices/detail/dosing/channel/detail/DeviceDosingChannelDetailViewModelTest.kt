package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.detail

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDosingChannelDetailViewModelTest {
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
    fun `route remains unresolved until authoritative firmware snapshot arrives`() {
        val viewModel = DeviceDosingChannelDetailViewModel(
            FakeOperations(snapshot(calibrated = true))
        )

        viewModel.bind(DEVICE_UID, SLOT_ID)

        assertFalse(viewModel.currentDraft().authoritativeStateAvailable)
        assertFalse(viewModel.currentDraft().routeValid)

        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentDraft().authoritativeStateAvailable)
        assertTrue(viewModel.currentDraft().routeValid)
    }

    @Test
    fun `authoritative uncalibrated snapshot rejects detail route`() {
        val viewModel = DeviceDosingChannelDetailViewModel(
            FakeOperations(snapshot(calibrated = false))
        )

        viewModel.bind(DEVICE_UID, SLOT_ID)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.currentDraft().authoritativeStateAvailable)
        assertFalse(viewModel.currentDraft().routeValid)
    }

    private class FakeOperations(
        private val refreshSnapshot: DeviceDosingChannelSnapshot
    ) : DeviceDosingChannelOperations {
        private val state = MutableStateFlow<DeviceDosingChannelSnapshot?>(null)

        override fun observe(
            deviceUid: String,
            slotId: String
        ): Flow<DeviceDosingChannelSnapshot?> = state

        override suspend fun refresh(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult {
            state.value = refreshSnapshot
            return DeviceDosingChannelOperationResult.Success(refreshSnapshot)
        }

        override suspend fun applyProgram(
            deviceUid: String,
            slotId: String,
            program: DeviceDosingProgram
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun setMissedDoseRecoveryEnabled(
            deviceUid: String,
            slotId: String,
            enabled: Boolean
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun applyReservoirSettings(
            deviceUid: String,
            slotId: String,
            settings: DeviceDosingReservoirSettings
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun refillReservoir(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseNow(
            deviceUid: String,
            slotId: String,
            amountMicroliters: Long
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun doseStop(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed

        override suspend fun reset(
            deviceUid: String,
            slotId: String
        ): DeviceDosingChannelOperationResult = DeviceDosingChannelOperationResult.Failed
    }

    private companion object {
        const val DEVICE_UID = "AQL-DOSING-DETAIL-ROUTE-TEST"
        const val SLOT_ID = "dosing:channel1"
        const val CALIBRATED_AT = 1_786_320_000L

        fun snapshot(calibrated: Boolean): DeviceDosingChannelSnapshot =
            DeviceDosingChannelSnapshot(
                deviceUid = DEVICE_UID,
                slotId = SLOT_ID,
                pumpCount = 1,
                channelNumber = 1,
                channelTitle = "Macro",
                revision = 1L,
                runtimeEnabled = true,
                runtimeReason = DeviceDosingRuntimeReason.NONE,
                deliveryAccountingCertain = true,
                calibrated = calibrated,
                lastCalibratedAtEpochSeconds = if (calibrated) CALIBRATED_AT else 0L,
                scheduling = DeviceDosingSchedulingPolicy(),
                program = null,
                progress = DeviceDosingChannelProgress(),
                reservoir = DeviceDosingReservoirSnapshot(),
                activeRun = DeviceDosingActiveRun(),
                controls = DeviceDosingChannelControls()
            )
    }
}
