package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaFailureStage
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class DeviceFamilySettingsFirmwareFailureActionTest {

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
    fun `recoverable availability failure retries availability check`() {
        val firmware = FakeFirmwareOperations()
        val viewModel = createViewModel(firmware)
        viewModel.bind(DEVICE_UID)
        firmware.emit(
            DeviceOtaState.Failed(
                DEVICE_UID,
                availabilityFailure()
            )
        )

        assertTrue(availabilityFailure().canRetryAvailabilityCheck)

        viewModel.onFirmwareUpdateAction()

        assertEquals(1, firmware.checkCalls)
        assertEquals(
            DeviceSettingsUpdateActionState.UpToDate,
            viewModel.uiState.value.updateActionState
        )
    }

    @Test
    fun `recoverable execution failure opens update details instead of checking again`() = runTest {
        val firmware = FakeFirmwareOperations()
        val viewModel = createViewModel(firmware)
        viewModel.bind(DEVICE_UID)
        val failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.DOWNLOAD_TIMEOUT,
            recoverable = true,
            stage = DeviceOtaFailureStage.UPDATE_EXECUTION
        )
        firmware.emit(DeviceOtaState.Failed(DEVICE_UID, failure))

        assertFalse(failure.canRetryAvailabilityCheck)

        viewModel.onFirmwareUpdateAction()

        assertEquals(0, firmware.checkCalls)
        assertEquals(
            DeviceFamilySettingsEvent.OpenFirmwareUpdate,
            viewModel.events.first()
        )
    }

    private fun createViewModel(firmware: FakeFirmwareOperations) =
        DeviceFamilySettingsViewModel(
            settingsOperations = FakeSettingsOperations(),
            firmwareUpdateOperations = firmware,
            manifestUrl = MANIFEST_URL
        )

    private fun availabilityFailure() = DeviceOtaFailure(
        reason = DeviceOtaFailureReason.CONNECTION,
        recoverable = true,
        stage = DeviceOtaFailureStage.AVAILABILITY_CHECK
    )

    private class FakeSettingsOperations : DeviceFamilySettingsOperations {
        private val snapshot = MutableStateFlow<DeviceRootSnapshot?>(validSnapshot())
        private val light = MutableStateFlow(DeviceLightProtectionSnapshot())

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshot
        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshot.value
        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
        override suspend fun updateCustomName(
            deviceUid: String,
            customName: String
        ): Result<Unit> = Result.success(Unit)

        override fun observeLightProtection(
            deviceUid: String
        ): Flow<DeviceLightProtectionSnapshot> = light

        override fun currentLightProtection(deviceUid: String): DeviceLightProtectionSnapshot =
            light.value

        override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun updateLightProtectionThreshold(
            deviceUid: String,
            thresholdCelsius: Int
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeFirmwareOperations : DeviceFirmwareUpdateOperations {
        private val state = MutableStateFlow<DeviceOtaState>(DeviceOtaState.Idle(DEVICE_UID))
        var checkCalls: Int = 0

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = state

        override suspend fun checkAvailability(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            checkCalls += 1
            val upToDate = DeviceOtaState.UpToDate(
                deviceUid = deviceUid,
                currentVersion = CURRENT_VERSION,
                latestVersion = CURRENT_VERSION,
                releaseContent = DeviceFirmwareReleaseContent.EMPTY
            )
            state.value = upToDate
            return Result.success(upToDate)
        }

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> =
            Result.failure(IllegalStateException("No plan expected in this test."))

        override suspend fun startUpdate(
            plan: PreparedDeviceFirmwareUpdate
        ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = true)

        override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true)

        override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true)

        fun emit(value: DeviceOtaState) {
            state.value = value
        }
    }

    private companion object {
        const val DEVICE_UID = "device-dose-pro-4-settings"
        const val CURRENT_VERSION = "1.0.0"
        const val MANIFEST_URL = "https://example.invalid/manifest-stable.json"

        fun validSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Dose Pro 4",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.DOSING,
            catalogState = DeviceRootCatalogState.VALID,
            productKey = "DOSING_DOSE_PRO_4",
            productId = "com.aqualight.dosing.dose_pro_4",
            model = "dose_pro_4",
            serialNumber = "AQL-DP4-FAILURE-TEST",
            hardwareRevision = "2.0",
            firmwareLabel = CURRENT_VERSION
        )
    }
}
