package com.aqua.aqualight.ui.tabs.devices.detail.settings

import com.aqua.aqualight.application.devices.DeviceFamilySettingsOperations
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceLightProtectionSnapshot
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
class DeviceFamilySettingsDeviceNameResetTest {

    @get:Rule
    val mainDispatcherRule = DeviceNameResetMainDispatcherRule()

    @Test
    fun `resets custom name to the firmware product display name`() {
        val operations = FakeSettingsOperations(customNameSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(
            settingsOperations = operations,
            firmwareUpdateOperations = FakeFirmwareOperations()
        )

        viewModel.bind(DEVICE_UID)
        assertTrue(viewModel.uiState.value.hasCustomDeviceName)

        viewModel.resetDeviceNameToDefault()

        assertEquals(listOf(""), operations.updatedNames)
        assertEquals(PRODUCT_DISPLAY_NAME, viewModel.uiState.value.deviceName)
        assertFalse(viewModel.uiState.value.hasCustomDeviceName)
        assertFalse(viewModel.uiState.value.deviceNameSaving)
    }

    @Test
    fun `does not write a reset when the device already uses its default name`() {
        val operations = FakeSettingsOperations(defaultNameSnapshot())
        val viewModel = DeviceFamilySettingsViewModel(
            settingsOperations = operations,
            firmwareUpdateOperations = FakeFirmwareOperations()
        )

        viewModel.bind(DEVICE_UID)
        viewModel.resetDeviceNameToDefault()

        assertTrue(operations.updatedNames.isEmpty())
        assertEquals(PRODUCT_DISPLAY_NAME, viewModel.uiState.value.deviceName)
    }

    @Test
    fun `retains custom name and emits failure when reset persistence fails`() = runTest {
        val operations = FakeSettingsOperations(customNameSnapshot()).apply {
            updateResult = Result.failure(IllegalStateException("not persisted"))
        }
        val viewModel = DeviceFamilySettingsViewModel(
            settingsOperations = operations,
            firmwareUpdateOperations = FakeFirmwareOperations()
        )

        viewModel.bind(DEVICE_UID)
        viewModel.resetDeviceNameToDefault()

        assertEquals(CUSTOM_NAME, viewModel.uiState.value.deviceName)
        assertTrue(viewModel.uiState.value.hasCustomDeviceName)
        assertEquals(
            DeviceFamilySettingsEvent.DeviceNameUpdateFailed,
            viewModel.events.first()
        )
    }

    private class FakeSettingsOperations(
        initialSnapshot: DeviceRootSnapshot
    ) : DeviceFamilySettingsOperations {
        private val snapshot = MutableStateFlow<DeviceRootSnapshot?>(initialSnapshot)
        private val lightProtection = MutableStateFlow(DeviceLightProtectionSnapshot())
        val updatedNames = mutableListOf<String>()
        var updateResult: Result<Unit> = Result.success(Unit)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshot

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshot.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateCustomName(
            deviceUid: String,
            customName: String
        ): Result<Unit> {
            updatedNames += customName
            if (updateResult.isSuccess) {
                snapshot.value = snapshot.value?.copy(
                    title = customName.ifBlank { PRODUCT_DISPLAY_NAME },
                    hasCustomName = customName.isNotBlank()
                )
            }
            return updateResult
        }

        override fun observeLightProtection(
            deviceUid: String
        ): Flow<DeviceLightProtectionSnapshot> = lightProtection

        override fun currentLightProtection(
            deviceUid: String
        ): DeviceLightProtectionSnapshot = lightProtection.value

        override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun updateLightProtectionThreshold(
            deviceUid: String,
            thresholdCelsius: Int
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeFirmwareOperations : DeviceFirmwareUpdateOperations {
        private val state = MutableStateFlow<DeviceOtaState>(DeviceOtaState.Idle(DEVICE_UID))

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = state

        override suspend fun checkAvailability(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> = Result.success(state.value)

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> = Result.failure(
            UnsupportedOperationException("Not required by this test.")
        )

        override suspend fun startUpdate(
            plan: PreparedDeviceFirmwareUpdate
        ): DeviceFirmwareCommandResult = DeviceFirmwareCommandResult(sent = true)

        override suspend fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true)

        override suspend fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult =
            DeviceFirmwareCommandResult(sent = true)
    }

    private class DeviceNameResetMainDispatcherRule(
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
        const val PRODUCT_DISPLAY_NAME = "WRGB Pro Elite 120"
        const val CUSTOM_NAME = "Bebeğimmm"

        fun customNameSnapshot() = baseSnapshot().copy(
            title = CUSTOM_NAME,
            hasCustomName = true
        )

        fun defaultNameSnapshot() = baseSnapshot()

        fun baseSnapshot() = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = PRODUCT_DISPLAY_NAME,
            availability = OwnerDeviceAvailability.REACHABLE,
            family = OwnerDeviceFamily.LIGHT,
            catalogState = DeviceRootCatalogState.VALID,
            serialNumber = "AQL-WPE-336172",
            hardwareRevision = "2.0",
            productDisplayName = PRODUCT_DISPLAY_NAME,
            hasCustomName = false
        )
    }
}
