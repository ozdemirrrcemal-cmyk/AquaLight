package com.aqua.aqualight.ui.tabs.devices.detail.light

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceLightChannelSlot
import com.aqua.aqualight.application.devices.DeviceMenuUnavailableReason
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.DeviceLightControlSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class DeviceLightRootPreparationTest {

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
    fun `restored Light route prepares the shared authoritative surface`() = runTest {
        val controls = FakeLightControlOperations(availableControl())
        val preparation = FakePreparationOperations()
        val viewModel = DeviceLightRootViewModel(
            rootOperations = FakeRootOperations(lightRoot()),
            lightControlOperations = controls,
            controlSurfacePreparationOperations = preparation
        )

        viewModel.bind(DEVICE_UID)

        assertEquals(1, preparation.prepareCalls)
        assertEquals(OwnerDeviceFamily.LIGHT, preparation.lastRequest?.family)
        assertEquals(DeviceConnectionVisualState.ONLINE, viewModel.uiState.value.connectionVisualState)
        assertTrue(viewModel.uiState.value.contentEnabled)
        assertFalse(viewModel.uiState.value.showBlockingPreparation)
    }

    @Test
    fun `fresh handoff never bypasses current authoritative Light state`() = runTest {
        val controls = FakeLightControlOperations(unavailableControl())
        val preparation = FakePreparationOperations(
            freshHandoff = true,
            onPrepare = { controls.publish(availableControl()) }
        )
        val viewModel = DeviceLightRootViewModel(
            rootOperations = FakeRootOperations(lightRoot()),
            lightControlOperations = controls,
            controlSurfacePreparationOperations = preparation
        )

        viewModel.bind(DEVICE_UID)

        assertEquals(1, preparation.prepareCalls)
        assertTrue(viewModel.uiState.value.contentEnabled)
    }

    @Test
    fun `mismatched Light product fails closed after a ready preparation result`() = runTest {
        val controls = FakeLightControlOperations(
            availableControl(productKey = "LIGHT_RGB_PRO_SLIM")
        )
        val preparation = FakePreparationOperations(freshHandoff = true)
        val viewModel = DeviceLightRootViewModel(
            rootOperations = FakeRootOperations(lightRoot()),
            lightControlOperations = controls,
            controlSurfacePreparationOperations = preparation
        )

        viewModel.bind(DEVICE_UID)

        assertEquals(
            DeviceMenuUnavailableReason.COMMERCIAL_PRODUCT_MISMATCH,
            viewModel.surfaceUnavailableEvents.first()
        )
        assertFalse(viewModel.uiState.value.contentEnabled)
        assertEquals(DeviceConnectionVisualState.OFFLINE, viewModel.uiState.value.connectionVisualState)
    }

    private class FakeRootOperations(
        initial: DeviceRootSnapshot
    ) : DeviceRootOperations {
        private val snapshots = MutableStateFlow<DeviceRootSnapshot?>(initial)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class FakeLightControlOperations(
        initial: DeviceLightControlResult
    ) : DeviceLightControlOperations {
        private val results = MutableStateFlow(initial)

        override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> = results

        override fun currentControl(deviceUid: String): DeviceLightControlResult = results.value

        override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult = results.value

        fun publish(result: DeviceLightControlResult) {
            results.value = result
        }
    }

    private class FakePreparationOperations(
        freshHandoff: Boolean = false,
        private val onPrepare: () -> Unit = {}
    ) : DeviceControlSurfacePreparationOperations {
        private var fresh = freshHandoff
        var prepareCalls = 0
        var lastRequest: DeviceControlSurfacePreparationRequest? = null

        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult {
            prepareCalls += 1
            lastRequest = request
            onPrepare()
            fresh = true
            return DeviceControlSurfacePreparationResult.Ready
        }

        override fun consumeFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ): Boolean {
            val result = fresh && deviceUid == DEVICE_UID && family == OwnerDeviceFamily.LIGHT
            fresh = false
            return result
        }
    }

    private companion object {
        const val DEVICE_UID = "light-pro"
    }
}

private fun lightRoot() = DeviceRootSnapshot(
    deviceUid = "light-pro",
    title = "WRGB Pro Elite",
    availability = OwnerDeviceAvailability.REACHABLE,
    family = OwnerDeviceFamily.LIGHT,
    catalogState = DeviceRootCatalogState.VALID,
    productKey = "LIGHT_WRGB_PRO_ELITE",
    lightChannelCount = 4,
    channelSlots = DeviceChannelSlots(
        lightChannels = listOf("white", "red", "green", "blue").mapIndexed { index, key ->
            DeviceLightChannelSlot(
                index = DeviceSlotIndex(index),
                wireKey = DeviceChannelWireKey(key),
                defaultDisplayName = key.replaceFirstChar(Char::uppercase)
            )
        },
        timerChannels = emptyList(),
        dosingChannels = emptyList(),
        fanOutputs = emptyList(),
        temperatureSensors = emptyList()
    )
)

private fun availableControl(
    productKey: String = "LIGHT_WRGB_PRO_ELITE"
): DeviceLightControlResult = DeviceLightControlResult.Available(
    DeviceLightControlSnapshot(
        deviceUid = "light-pro",
        productKey = productKey,
        physicalChannelCount = 4,
        channelKeys = listOf("red", "green", "blue", "white")
    )
)

private fun unavailableControl(): DeviceLightControlResult =
    DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
