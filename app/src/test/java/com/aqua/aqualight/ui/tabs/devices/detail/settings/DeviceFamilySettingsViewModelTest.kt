package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
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
        val viewModel = DeviceFamilySettingsViewModel(operations)

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
        val viewModel = DeviceFamilySettingsViewModel(operations)

        viewModel.bind(DEVICE_UID)
        viewModel.previewDeviceName("  Display aquarium  ")
        operations.emit(validSnapshot().copy(title = "Firmware name"))

        assertEquals("Display aquarium", viewModel.uiState.value.deviceName)
        assertEquals(1, operations.connectCalls)
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

    private class SettingsMainDispatcherRule(
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
