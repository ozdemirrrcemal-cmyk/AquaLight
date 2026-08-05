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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceFamilySettingsAutomaticFirmwareCheckTest {

    @Test
    fun `binding requests one stale-aware automatic check and preserves actionable state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val firmware = FakeFirmwareOperations()
            val viewModel = DeviceFamilySettingsViewModel(
                settingsOperations = FakeSettingsOperations(),
                firmwareUpdateOperations = firmware,
                manifestUrl = MANIFEST_URL
            )

            viewModel.bind(DEVICE_UID)
            viewModel.bind(DEVICE_UID)

            assertEquals(1, firmware.automaticCheckCalls)
            assertEquals(0, firmware.manualCheckCalls)
            assertEquals(1, firmware.statusCalls)
            assertEquals(
                DeviceSettingsUpdateActionState.UpdateAvailable("1.1.0"),
                viewModel.uiState.value.updateActionState
            )

            viewModel.onFirmwareUpdateAction()
            assertEquals(
                DeviceFamilySettingsEvent.OpenFirmwareUpdate,
                viewModel.events.first()
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `automatic check waits for a catalog validated device and runs only once`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val settings = FakeSettingsOperations(
                initialSnapshot = validSnapshot().copy(
                    catalogState = DeviceRootCatalogState.INVALID
                )
            )
            val firmware = FakeFirmwareOperations()
            val viewModel = DeviceFamilySettingsViewModel(
                settingsOperations = settings,
                firmwareUpdateOperations = firmware,
                manifestUrl = MANIFEST_URL
            )

            viewModel.bind(DEVICE_UID)
            assertEquals(0, firmware.automaticCheckCalls)

            settings.emit(validSnapshot())
            settings.emit(
                validSnapshot().copy(catalogState = DeviceRootCatalogState.INVALID)
            )
            settings.emit(validSnapshot())

            assertEquals(1, firmware.automaticCheckCalls)
            assertEquals(1, firmware.statusCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeSettingsOperations(
        initialSnapshot: DeviceRootSnapshot = validSnapshot()
    ) : DeviceFamilySettingsOperations {
        private val devices = MutableStateFlow(initialSnapshot)
        private val lightProtection = MutableStateFlow(DeviceLightProtectionSnapshot())

        fun emit(snapshot: DeviceRootSnapshot) {
            devices.value = snapshot
        }

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = devices

        override fun current(deviceUid: String): DeviceRootSnapshot = devices.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)

        override suspend fun updateCustomName(
            deviceUid: String,
            customName: String
        ): Result<Unit> = Result.success(Unit)

        override fun observeLightProtection(
            deviceUid: String
        ): Flow<DeviceLightProtectionSnapshot> = lightProtection

        override fun currentLightProtection(deviceUid: String): DeviceLightProtectionSnapshot =
            lightProtection.value

        override suspend fun refreshLightProtection(deviceUid: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun updateLightProtectionThreshold(
            deviceUid: String,
            thresholdCelsius: Int
        ): Result<Unit> = Result.success(Unit)
    }

    private class FakeFirmwareOperations : DeviceFirmwareUpdateOperations {
        private val states = MutableStateFlow<DeviceOtaState>(DeviceOtaState.Idle(DEVICE_UID))
        var automaticCheckCalls = 0
        var manualCheckCalls = 0
        var statusCalls = 0

        override fun observe(deviceUid: String): StateFlow<DeviceOtaState> = states

        override suspend fun refreshAvailabilityIfStale(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            automaticCheckCalls += 1
            return DeviceOtaState.UpdateAvailable(preparedPlan()).let { available ->
                states.value = available
                Result.success(available)
            }
        }

        override suspend fun checkAvailability(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<DeviceOtaState> {
            manualCheckCalls += 1
            return Result.success(states.value)
        }

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> = Result.success(preparedPlan())

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

    private companion object {
        const val DEVICE_UID = "device-wrgb-settings"
        const val MANIFEST_URL = "https://example.invalid/manifest-stable.json"

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
            firmwareLabel = "1.0.3"
        )

        fun preparedPlan() = PreparedDeviceFirmwareUpdate(
            deviceUid = DEVICE_UID,
            currentVersion = "1.0.3",
            targetVersion = "1.1.0",
            channel = "stable",
            environment = "light_wrgb_pro_elite",
            productKey = "LIGHT_WRGB_PRO_ELITE",
            productId = "com.aqualight.light.wrgb_pro_elite",
            model = "wrgb_pro_elite_120",
            hardwareRevision = "2.0",
            displayName = "WRGB Pro Elite 120",
            filename = "firmware.bin",
            downloadUrl = "https://example.invalid/firmware.bin",
            sha256 = "a".repeat(64),
            sizeBytes = 1_024,
            applyNow = true
        )
    }
}
