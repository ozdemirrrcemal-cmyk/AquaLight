package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.root

import com.aqua.aqualight.application.devices.DeviceChannelSlots
import com.aqua.aqualight.application.devices.DeviceChannelWireKey
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationOperations
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationRequest
import com.aqua.aqualight.application.devices.DeviceControlSurfacePreparationResult
import com.aqua.aqualight.application.devices.DeviceLightChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.DeviceSlotIndex
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightChannelOutputSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightHeroSnapshot
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeMutationResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanReason
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightPlanSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceLightRootModeLatencyTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mode selection renders before firmware ack`() {
        val lightOperations = DelayedLightControlOperations()
        val viewModel = createViewModel(lightOperations)

        viewModel.bind(DEVICE_UID)
        assertEquals(DeviceLightControlMode.MANUAL, viewModel.uiState.value.selectedMode)

        viewModel.setMode(DeviceLightControlMode.AUTOMATIC)

        assertEquals(DeviceLightControlMode.AUTOMATIC, viewModel.uiState.value.selectedMode)
        assertEquals(DeviceLightControlMode.AUTOMATIC, lightOperations.requestedMode)

        lightOperations.complete(
            DeviceLightModeMutationResult.Committed(DeviceLightControlMode.AUTOMATIC)
        )

        assertEquals(DeviceLightControlMode.AUTOMATIC, viewModel.uiState.value.selectedMode)
    }

    @Test
    fun `rejected mode selection rolls back to authoritative mode`() {
        val lightOperations = DelayedLightControlOperations()
        val viewModel = createViewModel(lightOperations)

        viewModel.bind(DEVICE_UID)
        viewModel.setMode(DeviceLightControlMode.CUSTOM)

        assertEquals(DeviceLightControlMode.CUSTOM, viewModel.uiState.value.selectedMode)

        lightOperations.complete(
            DeviceLightModeMutationResult.Failed(DeviceLightControlFailure.REJECTED)
        )

        assertEquals(DeviceLightControlMode.MANUAL, viewModel.uiState.value.selectedMode)
    }

    private fun createViewModel(
        lightOperations: DeviceLightControlOperations
    ): DeviceLightRootViewModel = DeviceLightRootViewModel(
        rootOperations = FakeRootOperations(),
        lightControlOperations = lightOperations,
        controlSurfacePreparationOperations = PreparedLightSurfaceOperations
    )

    private class FakeRootOperations : DeviceRootOperations {
        private val snapshot = lightRootSnapshot()
        private val state = MutableStateFlow<DeviceRootSnapshot?>(snapshot)

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = state

        override fun current(deviceUid: String): DeviceRootSnapshot? = state.value

        override fun connect(deviceUid: String): Result<Unit> = Result.success(Unit)
    }

    private class DelayedLightControlOperations : DeviceLightControlOperations {
        private val available = DeviceLightControlResult.Available(lightControlSnapshot())
        private val state = MutableStateFlow<DeviceLightControlResult>(available)
        private val completion = CompletableDeferred<DeviceLightModeMutationResult>()

        var requestedMode: DeviceLightControlMode? = null
            private set

        override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> = state

        override fun currentControl(deviceUid: String): DeviceLightControlResult = state.value

        override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult = state.value

        override suspend fun setMode(
            deviceUid: String,
            mode: DeviceLightControlMode
        ): DeviceLightModeMutationResult {
            requestedMode = mode
            return completion.await()
        }

        fun complete(result: DeviceLightModeMutationResult) {
            completion.complete(result)
        }
    }

    private object PreparedLightSurfaceOperations : DeviceControlSurfacePreparationOperations {
        override suspend fun prepare(
            request: DeviceControlSurfacePreparationRequest
        ): DeviceControlSurfacePreparationResult = DeviceControlSurfacePreparationResult.Ready

        override fun consumeFreshPreparation(
            deviceUid: String,
            family: OwnerDeviceFamily
        ): Boolean = deviceUid == DEVICE_UID && family == OwnerDeviceFamily.LIGHT
    }

    private companion object {
        const val DEVICE_UID = "device-light-1"
        val CHANNEL_KEYS = listOf("red", "green", "blue", "white")

        fun lightRootSnapshot(): DeviceRootSnapshot {
            val channels = CHANNEL_KEYS.mapIndexed { index, key ->
                DeviceLightChannelSlot(
                    index = DeviceSlotIndex(index),
                    wireKey = DeviceChannelWireKey(key),
                    defaultDisplayName = key.replaceFirstChar(Char::uppercase)
                )
            }
            return DeviceRootSnapshot(
                deviceUid = DEVICE_UID,
                title = "AquaLight",
                availability = OwnerDeviceAvailability.REACHABLE,
                family = OwnerDeviceFamily.LIGHT,
                catalogState = DeviceRootCatalogState.VALID,
                productKey = "LIGHT_WRGB_PRO_ELITE",
                lightChannelCount = channels.size,
                channelSlots = DeviceChannelSlots(
                    lightChannels = channels,
                    timerChannels = emptyList(),
                    dosingChannels = emptyList(),
                    fanOutputs = emptyList(),
                    temperatureSensors = emptyList()
                )
            )
        }

        fun lightControlSnapshot(): DeviceLightControlSnapshot =
            DeviceLightControlSnapshot(
                deviceUid = DEVICE_UID,
                productKey = "LIGHT_WRGB_PRO_ELITE",
                physicalChannelCount = CHANNEL_KEYS.size,
                channelKeys = CHANNEL_KEYS,
                channels = CHANNEL_KEYS.map { key ->
                    DeviceLightChannelOutputSnapshot(
                        key = key,
                        displayName = key,
                        displayColorRgb = 0,
                        effectivePercent = 0
                    )
                },
                plan = DeviceLightPlanSnapshot(
                    available = false,
                    reason = DeviceLightPlanReason.MODE_HAS_NO_SCHEDULE,
                    nowTimeMs = null,
                    channelScale = 1_000,
                    hasScheduleToday = false,
                    points = emptyList()
                ),
                automaticProgramCount = 0,
                customCurvePointCount = 0,
                hero = DeviceLightHeroSnapshot(mode = DeviceLightControlMode.MANUAL)
            )
    }
}
