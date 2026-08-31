package com.aqua.aqualight.ui.tabs.devices.detail

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRouteResolver
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.common.text.AquaUiText
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootKind
import com.aqua.aqualight.ui.tabs.devices.detail.common.DeviceRootOverviewViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.cooling.root.DeviceCoolingRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.DeviceLightRootViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.timer.DeviceTimerRootViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
class DeviceRootViewModelBoundaryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `overview renders application root snapshot without repository models`() {
        val operations = FakeDeviceRootOperations(rootSnapshot())
        val viewModel = DeviceRootOverviewViewModel(operations)
        viewModel.bind(DeviceRootKind.DOSING, "device-1", "Fallback")
        val state = viewModel.uiState.value
        assertEquals(AquaUiText.Dynamic("AquaLight Dosing"), state.title)
        assertEquals(AquaUiText.Resource(R.string.device_online), state.connectionStatus)
        assertEquals(AquaUiText.Dynamic("4"), state.primaryCountText)
        val featureParts = (state.featuresText as AquaUiText.Joined).parts
        assertTrue(featureParts.contains(AquaUiText.Resource(R.string.device_feature_dosing)))
        assertTrue(state.primarySectionPlaceholder is AquaUiText.Joined)
        assertEquals("device-1", operations.lastObservedUid)
    }

    @Test
    fun `light root exposes only the device title state`() {
        val operations = FakeDeviceRootOperations(rootSnapshot(
            capabilities = setOf(DeviceRootCapability.MANUAL_LIGHT),
            menuFeatures = setOf(DeviceRootMenuFeature.DEVICE_SETTINGS)
        ))
        val viewModel = DeviceLightRootViewModel(operations)
        viewModel.bind("device-1")
        assertEquals("AquaLight Dosing", viewModel.uiState.value.title)
        assertEquals("device-1", operations.lastObservedUid)
        assertEquals("device-1", operations.lastConnectedUid)
    }

    @Test
    fun `supported root titles follow the same dynamic device snapshot`() {
        val operations = FakeDeviceRootOperations(rootSnapshot())
        val light = DeviceLightRootViewModel(operations)
        val timer = DeviceTimerRootViewModel(operations)
        val cooling = coolingViewModel(operations)
        light.bind("device-1")
        timer.bind("device-1")
        cooling.bind("device-1")
        assertEquals("AquaLight Dosing", light.uiState.value.title)
        assertEquals("AquaLight Dosing", timer.uiState.value.title)
        assertEquals("AquaLight Dosing", cooling.uiState.value.title)
        operations.publishTitle("My Aquarium Controller")
        assertEquals("My Aquarium Controller", light.uiState.value.title)
        assertEquals("My Aquarium Controller", timer.uiState.value.title)
        assertEquals("My Aquarium Controller", cooling.uiState.value.title)
    }

    @Test
    fun `cooling root uses shared fail closed connection gate`() {
        val operations = FakeDeviceRootOperations(
            DeviceRootSnapshot(
                deviceUid = "device-1",
                title = "Cooling Pro",
                availability = OwnerDeviceAvailability.REACHABLE,
                family = OwnerDeviceFamily.COOLING,
                catalogState = DeviceRootCatalogState.VALID,
                capabilities = setOf(
                    DeviceRootCapability.COOLING,
                    DeviceRootCapability.FAN,
                    DeviceRootCapability.TEMPERATURE
                )
            )
        )
        val viewModel = coolingViewModel(operations)
        viewModel.bind("device-1")
        assertEquals(DeviceConnectionVisualState.ONLINE, viewModel.uiState.value.connectionVisualState)
        assertTrue(viewModel.uiState.value.contentEnabled)
        operations.publishAvailability(OwnerDeviceAvailability.UNREACHABLE)
        assertEquals(DeviceConnectionVisualState.OFFLINE, viewModel.uiState.value.connectionVisualState)
        assertFalse(viewModel.uiState.value.contentEnabled)
    }

    private fun coolingViewModel(operations: DeviceRootOperations): DeviceCoolingRootViewModel =
        DeviceCoolingRootViewModel(
            operations = operations,
            controlOperations = UnavailableCoolingControlOperations
        )

    private fun rootSnapshot(
        capabilities: Set<DeviceRootCapability> = setOf(DeviceRootCapability.DOSING),
        menuFeatures: Set<DeviceRootMenuFeature> = setOf(
            DeviceRootMenuFeature.DOSING_CHANNELS,
            DeviceRootMenuFeature.DOSING_SCHEDULES
        ),
        title: String = "AquaLight Dosing"
    ): DeviceRootSnapshot {
        val family = if (DeviceRootCapability.DOSING in capabilities) {
            OwnerDeviceFamily.DOSING
        } else {
            OwnerDeviceFamily.LIGHT
        }
        val routes = menuFeatures.mapNotNullTo(linkedSetOf()) { DeviceRootRouteResolver.resolve(family, it) }
        return DeviceRootSnapshot(
            deviceUid = "device-1",
            title = title,
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

    private inner class FakeDeviceRootOperations(initialSnapshot: DeviceRootSnapshot?) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initialSnapshot)
        var lastObservedUid = ""
        var lastConnectedUid = ""
        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> {
            lastObservedUid = deviceUid
            return snapshots
        }
        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value
        override fun connect(deviceUid: String): Result<Unit> {
            lastConnectedUid = deviceUid
            return Result.success(Unit)
        }
        fun publishTitle(title: String) {
            snapshots.value = snapshots.value?.copy(title = title)
        }
        fun publishAvailability(availability: OwnerDeviceAvailability) {
            snapshots.value = snapshots.value?.copy(availability = availability)
        }
    }

    class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
        override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
        override fun finished(description: Description) = Dispatchers.resetMain()
    }

    private object UnavailableCoolingControlOperations : DeviceCoolingControlOperations {
        private val unavailable = DeviceCoolingControlResult.Failed(
            DeviceCoolingControlFailure.Unavailable
        )

        override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> =
            flowOf(unavailable)

        override fun currentControl(deviceUid: String): DeviceCoolingControlResult = unavailable

        override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult = unavailable

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceCoolingControlMode
        ): DeviceCoolingControlResult = unavailable

        override suspend fun setManualFanPercent(
            deviceUid: String,
            percent: Int
        ): DeviceCoolingControlResult = unavailable
    }
}
