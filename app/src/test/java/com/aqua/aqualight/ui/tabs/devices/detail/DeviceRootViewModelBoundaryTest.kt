package com.aqua.aqualight.ui.tabs.devices.detail

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRootViewModelBoundaryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `overview renders application root snapshot without repository models`() {
        val operations = FakeDeviceRootOperations(rootSnapshot())
        val viewModel = DeviceRootOverviewViewModel(operations)

        viewModel.bind(
            kind = DeviceRootKind.DOSING,
            deviceUidText = "device-1",
            fallbackTitle = "Fallback"
        )

        val state = viewModel.uiState.value
        assertEquals(AquaUiText.Dynamic("AquaLight Dosing"), state.title)
        assertEquals(AquaUiText.Resource(R.string.device_online), state.connectionStatus)
        assertEquals(AquaUiText.Dynamic("4"), state.primaryCountText)
        assertTrue(
            (state.featuresText as AquaUiText.Joined).parts.contains(
                AquaUiText.Resource(R.string.device_feature_dosing)
            )
        )
        assertTrue(state.primarySectionPlaceholder is AquaUiText.Joined)
        assertEquals("device-1", operations.lastObservedUid)
    }

    @Test
    fun `light root delegates ota preparation and commands through firmware boundary`() {
        val rootOperations = FakeDeviceRootOperations(
            rootSnapshot(
                capabilities = setOf(
                    DeviceRootCapability.MANUAL_LIGHT,
                    DeviceRootCapability.LIGHT_PROGRAM,
                    DeviceRootCapability.OTA
                ),
                menuFeatures = setOf(
                    DeviceRootMenuFeature.LIGHT_MANUAL,
                    DeviceRootMenuFeature.LIGHT_PROGRAMS,
                    DeviceRootMenuFeature.DEVICE_SETTINGS
                )
            )
        )
        val firmwareOperations = FakeFirmwareUpdateOperations()
        val viewModel = DeviceLightRootViewModel(
            rootOperations = rootOperations,
            firmwareUpdateOperations = firmwareOperations
        )

        viewModel.bind("device-1", "Light")
        viewModel.checkBetaOtaManifest()
        assertEquals("device-1", firmwareOperations.preparedDeviceUid)
        assertEquals(
            R.string.device_ota_test_plan_summary,
            (viewModel.uiState.value.otaTestText as AquaUiText.Resource).resId
        )

        viewModel.startOtaTestUpdate()
        viewModel.requestOtaTestStatus()
        viewModel.clearOtaTestStatus()

        assertEquals(1, firmwareOperations.startCalls)
        assertEquals("device-1", firmwareOperations.statusDeviceUid)
        assertEquals("device-1", firmwareOperations.clearDeviceUid)
        assertTrue(
            (viewModel.uiState.value.otaTestText as AquaUiText.Resource)
                .args.contains("clear-message")
        )
        assertEquals("device-1", rootOperations.lastConnectedUid)
    }

    private fun rootSnapshot(
        capabilities: Set<DeviceRootCapability> = setOf(DeviceRootCapability.DOSING),
        menuFeatures: Set<DeviceRootMenuFeature> = setOf(
            DeviceRootMenuFeature.DOSING_CHANNELS,
            DeviceRootMenuFeature.DOSING_SCHEDULES
        )
    ): DeviceRootSnapshot {
        val family = if (DeviceRootCapability.DOSING in capabilities) {
            OwnerDeviceFamily.DOSING
        } else {
            OwnerDeviceFamily.LIGHT
        }
        val routes = menuFeatures.mapNotNullTo(linkedSetOf()) { feature ->
            DeviceRootRouteResolver.resolve(family, feature)
        }
        return DeviceRootSnapshot(
            deviceUid = "device-1",
            title = "AquaLight Dosing",
            availability = OwnerDeviceAvailability.REACHABLE,
            family = family,
            catalogState = DeviceRootCatalogState.VALID,
            ipAddress = "192.168.1.20",
            firmwareLabel = "1.0.0 / 100",
            modelLabel = "AQL-DOSING / rev-a",
            lightChannelCount = 6,
            dosingChannelCount = 4,
            capabilities = capabilities,
            menuFeatures = menuFeatures,
            allowedRoutes = routes
        )
    }

    private class FakeDeviceRootOperations(
        initialSnapshot: DeviceRootSnapshot?
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var lastObservedUid: String = ""
        var lastConnectedUid: String = ""

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> {
            lastObservedUid = deviceUid
            return snapshots
        }

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            lastConnectedUid = deviceUid
            return Result.success(Unit)
        }
    }

    private class FakeFirmwareUpdateOperations : DeviceFirmwareUpdateOperations {
        var preparedDeviceUid: String = ""
        var startCalls: Int = 0
        var statusDeviceUid: String = ""
        var clearDeviceUid: String = ""

        override suspend fun prepareUpdate(
            deviceUid: String,
            manifestUrl: String,
            applyNow: Boolean
        ): Result<PreparedDeviceFirmwareUpdate> {
            preparedDeviceUid = deviceUid
            return Result.success(preparedPlan())
        }

        override fun startUpdate(plan: PreparedDeviceFirmwareUpdate): DeviceFirmwareCommandResult {
            startCalls += 1
            return DeviceFirmwareCommandResult(sent = true, messageId = "start-message")
        }

        override fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult {
            statusDeviceUid = deviceUid
            return DeviceFirmwareCommandResult(sent = true, messageId = "status-message")
        }

        override fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult {
            clearDeviceUid = deviceUid
            return DeviceFirmwareCommandResult(sent = true, messageId = "clear-message")
        }

        private fun preparedPlan() = PreparedDeviceFirmwareUpdate(
            deviceUid = "device-1",
            currentVersion = "1.0.0",
            targetVersion = "1.0.1",
            channel = "beta",
            environment = "production",
            productKey = "aqualight",
            productId = "light",
            model = "AQL-Light",
            hardwareRevision = "rev-a",
            displayName = "AquaLight",
            filename = "firmware.bin",
            downloadUrl = "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/releases/download/v1.0.1/firmware.bin",
            sha256 = "a".repeat(64),
            sizeBytes = 1024,
            applyNow = true
        )
    }

    class MainDispatcherRule(
        private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(dispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
