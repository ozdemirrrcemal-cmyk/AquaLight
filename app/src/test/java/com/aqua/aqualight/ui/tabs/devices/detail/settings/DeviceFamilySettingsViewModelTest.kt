package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareReleaseContent
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceOtaState
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        val operations = FakeDeviceFamilySettingsOperations(invalidSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)

        assertEquals(
            DeviceSettingsInformationLoadState.LOADING,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("", viewModel.uiState.value.hardwareRevision)

        operations.emitDevice(validSnapshot())

        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("2.0", viewModel.uiState.value.hardwareRevision)

        operations.emitDevice(invalidSnapshot())

        assertEquals(
            DeviceSettingsInformationLoadState.READY,
            viewModel.uiState.value.informationLoadState
        )
        assertEquals("2.0", viewModel.uiState.value.hardwareRevision)
    }

    @Test
    fun `shows only supplied serial and retains it through transient blank snapshots`() {
        val operations = FakeDeviceFamilySettingsOperations(null)
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)

        assertEquals("", viewModel.uiState.value.serialNumber)

        operations.emitDevice(invalidSnapshot())
        assertEquals("AQL-WPE-123456", viewModel.uiState.value.serialNumber)

        operations.emitDevice(invalidSnapshot().copy(serialNumber = ""))
        assertEquals("AQL-WPE-123456", viewModel.uiState.value.serialNumber)

        operations.emitDevice(validSnapshot().copy(serialNumber = "AQL-WPE-654321"))
        assertEquals("AQL-WPE-654321", viewModel.uiState.value.serialNumber)
    }

    @Test
    fun `persists device name through owner scoped settings operations`() {
        val operations = FakeDeviceFamilySettingsOperations(validSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)
        viewModel.updateDeviceName("  Display aquarium  ")

        assertEquals("Display aquarium", viewModel.uiState.value.deviceName)
        assertEquals(listOf("Display aquarium"), operations.updatedNames)
        assertEquals(1, operations.connectCalls)
    }

    @Test
    fun `restores device name and emits failure when persistence fails`() = runTest {
        val operations = FakeDeviceFamilySettingsOperations(validSnapshot()).apply {
            deviceNameResult = Result.failure(IllegalStateException("not persisted"))
        }
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        viewModel.bind(DEVICE_UID)
        viewModel.updateDeviceName("Rejected name")

        assertEquals("WRGB Pro Elite 120", viewModel.uiState.value.deviceName)
        assertFalse(viewModel.uiState.value.deviceNameSaving)
        assertEquals(
            DeviceFamilySettingsEvent.DeviceNameUpdateFailed,
            viewModel.events.first()
        )
    }

    @Test
    fun `ignores duplicate writes while name persistence is pending`() {
        val operations = FakeDeviceFamilySettingsOperations(validSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(operations, FakeFirmwareOperations())

        operations.deviceNameGate = CompletableDeferred()
        viewModel.bind(DEVICE_UID)
        viewModel.updateDeviceName("First name")
        viewModel.updateDeviceName("Second name")

        assertEquals(listOf("First name"), operations.updatedNames)
        assertTrue(viewModel.uiState.value.deviceNameSaving)
        operations.deviceNameGate?.complete(Unit)
        mainDispatcherRule.runCurrent()
        assertFalse(viewModel.uiState.value.deviceNameSaving)
    }

    @Test
    fun `checks signed availability and probes status without losing the selected plan`() {
        val firmware = FakeFirmwareOperations(preparedPlan())
        val viewModel = DeviceFamilySettingsViewModel(
            settingsOperations = FakeDeviceFamilySettingsOperations(validSnapshot()),
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
        assertEquals(1, firmware.statusCalls)
    }

    private class FakeDeviceFamilySettingsOperations(
        initialSnapshot: DeviceRootSnapshot?
    ) : DeviceFamilySettingsOperations {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var connectCalls: Int = 0
        var deviceNameResult: Result<Unit> = Result.success(Unit)
        var deviceNameGate: CompletableDeferred<Unit>? = null
        val updatedNames = mutableListOf<String>()

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            connectCalls += 1
            return Result.success(Unit)
        }

        override suspend fun updateCustomName(
            deviceUid: String,
            customName: String
        ): Result<Unit> {
            updatedNames += customName
            deviceNameGate?.await()
            val result = deviceNameResult
            if (result.isSuccess) {
                snapshots.value = snapshots.value?.copy(title = customName)
            }
            return result
        }

        fun emitDevice(snapshot: DeviceRootSnapshot?) {
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
    }

    class SettingsMainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        fun runCurrent() {
            dispatcher.scheduler.runCurrent()
        }

        fun advanceTimeBy(delayMillis: Long) {
            dispatcher.scheduler.advanceTimeBy(delayMillis)
            dispatcher.scheduler.runCurrent()
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val DEVICE_UID = "device-wrgb-settings"
        const val MANIFEST_URL = "https://example.invalid/manifest-stable.json"

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
            displayName = "WRGB Pro Elite 120",
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
