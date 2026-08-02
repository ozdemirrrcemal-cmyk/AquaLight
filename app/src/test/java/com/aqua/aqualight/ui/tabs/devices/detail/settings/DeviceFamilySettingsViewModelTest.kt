package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareFailure
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureKind
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureSource
import com.aqua.aqualight.application.devices.DeviceFirmwareFailureStage
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
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
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFamilySettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = SettingsMainDispatcherRule()

    @Test
    fun `retains verified hardware revision through transient invalid snapshots`() {
        val operations = FakeDeviceRootOperations(invalidSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)

        assertEquals(
            DeviceSettingsInformationLoadState.LOADING,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("", viewModel.uiState.value.hardwareRevision)

        operations.emit(validSnapshot())

        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("2.0", viewModel.uiState.value.hardwareRevision)

        operations.emit(invalidSnapshot())

        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("2.0", viewModel.uiState.value.hardwareRevision)
    }

    @Test
    fun `keeps device name editing inside presentation state`() {
        val operations = FakeDeviceRootOperations(validSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)
        viewModel.previewDeviceName("  Display aquarium  ")
        operations.emit(validSnapshot().copy(title = "Firmware name"))

        assertEquals("Display aquarium", viewModel.uiState.value.deviceName)
        assertEquals(1, operations.connectCalls)
    }

    @Test
    fun `checks signed availability without probing runtime status from settings`() {
        val firmware = FakeFirmwareOperations(preparedPlan())
        val viewModel = DeviceFamilySettingsViewModel(
            rootOperations = FakeDeviceRootOperations(validSnapshot()),
            firmwareUpdateOperations = firmware,
            manifestUrl = MANIFEST_URL
        )

        viewModel.bind(DEVICE_UID)
        viewModel.checkForUpdates()

        assertEquals(
            DeviceSettingsUpdateActionState.UpdateAvailable("2.0.0"),
            viewModel.uiState.value.updateActionState
        )
        assertEquals(1, firmware.checkCalls)
        assertEquals(0, firmware.statusCalls)
    }

    @Test
    fun `settings action retains complete structured failure`() {
        val failure = failure()
        val firmware = FakeFirmwareOperations(preparedPlan())
        val viewModel = DeviceFamilySettingsViewModel(
            rootOperations = FakeDeviceRootOperations(validSnapshot()),
            firmwareUpdateOperations = firmware,
            manifestUrl = MANIFEST_URL
        )
        viewModel.bind(DEVICE_UID)

        firmware.emit(DeviceOtaState.Failed(DEVICE_UID, failure))

        val action = viewModel.uiState.value.updateActionState
            as DeviceSettingsUpdateActionState.Failed
        assertSame(failure, action.failure)
    }

    private class FakeDeviceRootOperations(
        initialSnapshot: DeviceRootSnapshot?
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var connectCalls: Int = 0

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            connectCalls += 1
            return Result.success(Unit)
        }

        fun emit(snapshot: DeviceRootSnapshot?) {
            snapshots.value = snapshot
        }
    }

    private class FakeFirmwareOperations(
        private val plan: PreparedDeviceFirmwareUpdate? = null
    ) : DeviceFirmwareUpdateOperations {
        private val state = MutableStateFlow<DeviceOtaState>(DeviceOtaState.Idle(DEVICE_UID))
        var checkCalls = 0
        var statusCalls = 0

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = state

        override suspend fun checkAvailability(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            checkCalls += 1
            val result = plan?.let { selected -> DeviceOtaState.UpdateAvailable(selected) }
                ?: DeviceOtaState.UpToDate(
                    deviceUid,
                    "1.2.3",
                    "1.2.3",
                    DeviceFirmwareReleaseContent.EMPTY
                )
            state.value = result
            return Result.success(result)
        }

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> = Result.success(requireNotNull(plan))

        override suspend fun startUpdate(
            plan: PreparedDeviceFirmwareUpdate
        ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = true)

        override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult {
            statusCalls += 1
            return DeviceFirmwareCommandResult(sent = true)
        }

        override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true)

        fun emit(value: DeviceOtaState) {
            state.value = value
        }
    }

    class SettingsMainDispatcherRule(
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
        const val DEVICE_UID = "device-wrgb-settings"
        const val MANIFEST_URL = "https://example.invalid/manifest-stable.json"

        fun failure() = DeviceFirmwareFailure(
            kind = DeviceFirmwareFailureKind.CONNECTION,
            source = DeviceFirmwareFailureSource.RUNTIME,
            stage = DeviceFirmwareFailureStage.STATUS,
            technicalMessage = "runtime disconnected",
            code = "runtime_not_connected",
            requestId = "status-1",
            recoverable = true
        )

        fun preparedPlan() = PreparedDeviceFirmwareUpdate(
            deviceUid = DEVICE_UID,
            currentVersion = "1.2.3",
            targetVersion = "2.0.0",
            channel = "stable",
            environment = "light_wrgb_pro_elite",
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productId = "com.aqualight.light.wrgb_pro_elite",
            model = "wrgb_pro_elite_120",
            hardwareRevision = "2.0",
            filename = "AquaLight-light_wrgb_pro_elite-v2.0.0-ota.bin",
            downloadUrl = "https://example.invalid/firmware.bin",
            sha256 = "a".repeat(64),
            sizeBytes = 1_048_576,
            applyNow = true
        )

        fun invalidSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "WRGB Pro Elite 120",
            availability = OwnerDeviceAvailability.UNREACHABLE,
            catalogState = DeviceRootCatalogState.INVALID,
            serialNumber = "AQL-WPE-123456"
        )

        fun validSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "WRGB Pro Elite 120",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.LIGHT,
            catalogState = DeviceRootCatalogState.VALID,
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productId = "com.aqualight.light.wrgb_pro_elite",
            model = "wrgb_pro_elite_120",
            serialNumber = "AQL-WPE-123456",
            hardwareRevision = "2.0",
            firmwareLabel = "1.2.3 / build 42",
            temperatureSensorCount = 1,
            supportedFeatures = listOf("LIGHT_TEMPERATURE_PROTECTION")
        )
    }
}
