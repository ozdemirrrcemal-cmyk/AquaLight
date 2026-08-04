package com.aqua.aqualight.ui.tabs.devices.detail.update

import com.aqua.aqualight.application.devices.DeviceFirmwareChannel
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaFailure
import com.aqua.aqualight.application.devices.DeviceOtaFailureReason
import com.aqua.aqualight.application.devices.DeviceOtaProgressPhase
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFirmwareUpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = UpdateMainDispatcherRule()

    @Test
    fun `bind reselects signed plan and probes device status for recovery`() {
        val firmware = FakeFirmwareOperations(preparedPlan())
        val viewModel = DeviceFirmwareUpdateViewModel(
            rootOperations = FakeRootOperations(deviceSnapshot()),
            firmwareUpdateOperations = firmware
        )

        viewModel.bind(DEVICE_UID)

        assertEquals(DeviceFirmwareUpdateMode.AVAILABLE, viewModel.uiState.value.mode)
        assertEquals("2.0.0", viewModel.uiState.value.targetVersion)
        assertEquals("Güvenli güncelleme", viewModel.uiState.value.releaseContent.title)
        assertEquals(1, firmware.checkCalls)
        assertEquals(1, firmware.statusCalls)
        assertEquals(listOf(DeviceFirmwareChannel.STABLE), firmware.checkedChannels)
    }

    @Test
    fun `real progress and release content survive reconnect and success`() {
        val plan = preparedPlan()
        val firmware = FakeFirmwareOperations(plan)
        val viewModel = DeviceFirmwareUpdateViewModel(
            rootOperations = FakeRootOperations(deviceSnapshot()),
            firmwareUpdateOperations = firmware
        )
        viewModel.bind(DEVICE_UID)

        firmware.emit(
            DeviceOtaState.InProgress(
                deviceUid = DEVICE_UID,
                targetVersion = plan.targetVersion,
                phase = DeviceOtaProgressPhase.WRITING,
                progressPermille = 543,
                bytesWritten = 543_000L,
                contentLength = 1_000_000L,
                releaseContent = plan.releaseContent
            )
        )
        assertEquals(54, viewModel.uiState.value.progressPercent)
        assertEquals(543_000L, viewModel.uiState.value.bytesWritten)

        firmware.emit(DeviceOtaState.Recovering(DEVICE_UID, "2.0.0", 543))
        assertEquals(DeviceFirmwareUpdateMode.RECOVERING, viewModel.uiState.value.mode)
        assertEquals("Güvenli güncelleme", viewModel.uiState.value.releaseContent.title)
        assertEquals(54, viewModel.uiState.value.progressPercent)

        firmware.emit(DeviceOtaState.Succeeded(DEVICE_UID, "2.0.0", plan.releaseContent))
        assertEquals(DeviceFirmwareUpdateMode.SUCCEEDED, viewModel.uiState.value.mode)
        assertEquals(100, viewModel.uiState.value.progressPercent)
        assertEquals("2.0.0", viewModel.uiState.value.currentVersion)
    }

    @Test
    fun `typed firmware failure is retained for localized presentation`() {
        val firmware = FakeFirmwareOperations(preparedPlan())
        val viewModel = DeviceFirmwareUpdateViewModel(
            rootOperations = FakeRootOperations(deviceSnapshot()),
            firmwareUpdateOperations = firmware
        )
        viewModel.bind(DEVICE_UID)
        val failure = DeviceOtaFailure(
            reason = DeviceOtaFailureReason.INTEGRITY_CHECK_FAILED,
            recoverable = false,
            field = "sha256",
            diagnosticMessage = "downloaded firmware SHA256 does not match manifest"
        )

        firmware.emit(DeviceOtaState.Failed(DEVICE_UID, failure))

        assertEquals(DeviceFirmwareUpdateMode.FAILED, viewModel.uiState.value.mode)
        assertEquals(failure, viewModel.uiState.value.failure)
        assertFalse(viewModel.uiState.value.failure?.recoverable ?: true)
    }

    @Test
    fun `install dispatches only the selected exact plan`() {
        val plan = preparedPlan()
        val firmware = FakeFirmwareOperations(plan)
        val viewModel = DeviceFirmwareUpdateViewModel(
            rootOperations = FakeRootOperations(deviceSnapshot()),
            firmwareUpdateOperations = firmware
        )
        viewModel.bind(DEVICE_UID)

        viewModel.installUpdate()

        assertEquals(listOf(plan), firmware.startedPlans)
        assertEquals(DeviceFirmwareUpdateMode.STARTING, viewModel.uiState.value.mode)
        assertTrue(viewModel.uiState.value.mode.isActive)
    }

    private class FakeRootOperations(
        snapshot: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeFirmwareOperations(
        private val plan: PreparedDeviceFirmwareUpdate
    ) : DeviceFirmwareUpdateOperations {
        private val state = MutableStateFlow<DeviceOtaState>(DeviceOtaState.Idle(DEVICE_UID))
        val startedPlans = mutableListOf<PreparedDeviceFirmwareUpdate>()
        val checkedChannels = mutableListOf<DeviceFirmwareChannel>()
        var checkCalls = 0
        var statusCalls = 0

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = state

        override suspend fun checkAvailability(
            deviceUid: String,
            channel: DeviceFirmwareChannel,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            checkCalls += 1
            checkedChannels += channel
            state.value = DeviceOtaState.Checking(deviceUid, plan.currentVersion)
            return DeviceOtaState.UpdateAvailable(plan).let { available ->
                state.value = available
                Result.success(available)
            }
        }

        override suspend fun prepareUpdate(
            deviceUid: String,
            channel: DeviceFirmwareChannel,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> = Result.success(plan)

        override suspend fun startUpdate(
            plan: PreparedDeviceFirmwareUpdate
        ): DeviceFirmwareCommandResult {
            startedPlans += plan
            state.value = DeviceOtaState.Starting(plan, "start-1")
            return DeviceFirmwareCommandResult(sent = true, messageId = "start-1")
        }

        override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult {
            statusCalls += 1
            return DeviceFirmwareCommandResult(sent = true, messageId = "status-1")
        }

        override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true, messageId = "clear-1")

        fun emit(value: DeviceOtaState) {
            state.value = value
        }
    }

    class UpdateMainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val DEVICE_UID = "AQL-DP4-UPDATE-UI"

        fun releaseContent() = DeviceFirmwareReleaseContent(
            localeTag = "tr-TR",
            title = "Güvenli güncelleme",
            summary = "Dozaj güvenilirliği geliştirildi.",
            changes = listOf("Kalibrasyon doğrulaması geliştirildi."),
            warnings = listOf("Güncelleme sırasında cihazı kapatmayın."),
            mandatory = false
        )

        fun preparedPlan() = PreparedDeviceFirmwareUpdate(
            deviceUid = DEVICE_UID,
            currentVersion = "1.0.0",
            targetVersion = "2.0.0",
            channel = "stable",
            environment = "dosing_dose_pro_4",
            productKey = "DOSING_DOSE_PRO_4",
            productId = "com.aqualight.dosing.dose_pro_4",
            model = "dose_pro_4",
            hardwareRevision = "2.0",
            displayName = "Dose Pro 4",
            filename = "AquaLight-dosing_dose_pro_4-v2.0.0-ota.bin",
            downloadUrl = "https://example.invalid/AquaLight-ota.bin",
            sha256 = "a".repeat(64),
            sizeBytes = 1_000_000,
            applyNow = true,
            runtimeMetadataGeneration = 7L,
            manifestTag = "dosing_dose_pro_4-v2.0.0",
            releaseContent = releaseContent()
        )

        fun deviceSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Salon dozaj",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.DOSING,
            catalogState = DeviceRootCatalogState.VALID,
            productKey = "DOSING_DOSE_PRO_4",
            productId = "com.aqualight.dosing.dose_pro_4",
            model = "dose_pro_4",
            serialNumber = "AQL-DP4-123456",
            hardwareRevision = "2.0",
            firmwareLabel = "1.0.0"
        )
    }
}
