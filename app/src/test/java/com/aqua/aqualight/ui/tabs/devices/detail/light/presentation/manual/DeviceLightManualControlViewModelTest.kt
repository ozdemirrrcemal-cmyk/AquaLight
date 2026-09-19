package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootOperations
import com.aqua.aqualight.application.devices.DeviceRootRoute
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannelDescriptor
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannelDescriptor
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMode
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceLightManualControlViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `authoritative runtime snapshot enables content without global loading`() {
        val viewModel = boundViewModel()

        assertNull(viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.showGlobalLoading)
        assertTrue(viewModel.uiState.value.contentEnabled)
        assertEquals(
            INITIAL_GREEN_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN)
        )
    }

    @Test
    fun `slider changes stay local until commit then send complete scene`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manual)

        viewModel.updateChannel(DeviceLightManualChannelId.RED, UPDATED_RED_PERCENT)

        assertTrue(manual.setScenes.isEmpty())
        assertEquals(
            UPDATED_RED_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.RED)
        )
        assertEquals(
            INITIAL_GREEN_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN)
        )

        viewModel.commitScene()

        assertEquals(UPDATED_RED_PERCENT, manual.setScenes.single().value(DeviceLightManualChannel.RED))
        assertFalse(viewModel.uiState.value.showGlobalLoading)
    }

    @Test
    fun `in flight slider commit never opens blocking loading`() {
        val mutationGate = CompletableDeferred<Unit>()
        val manual = FakeManualOperations(mutationGate = mutationGate)
        val viewModel = boundViewModel(manual)

        viewModel.updateChannel(DeviceLightManualChannelId.RED, UPDATED_RED_PERCENT)
        viewModel.commitScene()

        assertEquals(1, manual.setScenes.size)
        assertFalse(viewModel.uiState.value.showGlobalLoading)

        mutationGate.complete(Unit)

        assertFalse(viewModel.uiState.value.showGlobalLoading)
    }

    @Test
    fun `channel step commits the resulting scene`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manual)

        viewModel.stepChannel(DeviceLightManualChannelId.GREEN, CHANNEL_STEP)

        assertEquals(
            INITIAL_GREEN_PERCENT + CHANNEL_STEP,
            manual.setScenes.single().value(DeviceLightManualChannel.GREEN)
        )
    }

    @Test
    fun `preset selection copies and commits every supported channel`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manual)

        viewModel.applyPreset(DeviceLightManualPresetId.RED_PLANTS)

        assertEquals(PERCENT_65, viewModel.uiState.value.percent(DeviceLightManualChannelId.RED))
        assertEquals(PERCENT_45, viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN))
        assertEquals(PERCENT_70, viewModel.uiState.value.percent(DeviceLightManualChannelId.BLUE))
        assertEquals(PERCENT_45, viewModel.uiState.value.percent(DeviceLightManualChannelId.WHITE))
        assertEquals(
            DeviceLightManualPresetId.RED_PLANTS,
            viewModel.uiState.value.selectedPreset
        )
        assertEquals(PERCENT_65, manual.setScenes.single().value(DeviceLightManualChannel.RED))
    }

    @Test
    fun `turn off invokes firmware operation and reconciles every channel`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manual)

        viewModel.turnOff()

        assertEquals(1, manual.turnOffCalls)
        assertTrue(viewModel.uiState.value.channels.all { channel -> channel.percent == 0 })
        assertFalse(viewModel.uiState.value.showGlobalLoading)
    }

    @Test
    fun `active non-manual mode is retained for the output-context notice`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manual)

        manual.publishMode(DeviceLightManualMode.AUTOMATIC)
        assertEquals(DeviceLightManualMode.AUTOMATIC, viewModel.uiState.value.activeMode)

        manual.publishMode(DeviceLightManualMode.CUSTOM)
        assertEquals(DeviceLightManualMode.CUSTOM, viewModel.uiState.value.activeMode)
    }

    @Test
    fun `rgb slim authority removes white channel and estimated power`() {
        val manual = FakeManualOperations(
            channels = listOf(
                DeviceLightManualChannel.RED,
                DeviceLightManualChannel.GREEN,
                DeviceLightManualChannel.BLUE
            )
        )
        val viewModel = boundViewModel(manual)

        assertFalse(
            viewModel.uiState.value.channels.any { channel ->
                channel.id == DeviceLightManualChannelId.WHITE
            }
        )
        assertNull(viewModel.uiState.value.power)
    }

    @Test
    fun `firmware channel presentation metadata is rendered without local fallback`() {
        val state = boundViewModel().uiState.value

        assertEquals(
            listOf("Firmware RED", "Firmware GREEN", "Firmware BLUE", "Firmware WHITE"),
            state.channels.map(DeviceLightManualChannelUiState::label)
        )
        assertEquals(
            listOf(RED_RGB, GREEN_RGB, BLUE_RGB, WHITE_RGB),
            state.channels.map(DeviceLightManualChannelUiState::displayColorRgb)
        )
    }

    @Test
    fun `central offline state retains presentation and never reconnects from Manual`() {
        val root = FakeRootOperations(onlineRoot())
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manualOperations = manual, rootOperations = root)

        root.publish(onlineRoot(OwnerDeviceAvailability.UNREACHABLE))
        manual.publishFailure(DeviceLightManualFailure.UNAVAILABLE)

        assertTrue(viewModel.uiState.value.contentEnabled)
        assertTrue(viewModel.uiState.value.libraryActionsEnabled)
        assertFalse(viewModel.uiState.value.controlsEnabled)
        assertEquals(
            DeviceConnectionVisualState.OFFLINE,
            viewModel.uiState.value.connectionVisualState
        )
        assertEquals(
            INITIAL_RED_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.RED)
        )
        assertEquals(0, root.connectCalls)

        viewModel.stepChannel(DeviceLightManualChannelId.RED, CHANNEL_STEP)
        assertTrue(manual.setScenes.isEmpty())
    }

    @Test
    fun `presentation remains visible while current generation write authority is pending`() {
        val manual = FakeManualOperations()
        val viewModel = boundViewModel(manualOperations = manual)

        manual.publishWriteAuthority(authoritative = false)

        assertTrue(viewModel.uiState.value.contentEnabled)
        assertFalse(viewModel.uiState.value.controlsEnabled)
        assertEquals(
            INITIAL_BLUE_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.BLUE)
        )

        manual.publishWriteAuthority(authoritative = true)

        assertTrue(viewModel.uiState.value.controlsEnabled)
    }

    @Test
    fun `manual scenes use the central curated aquarium catalog`() {
        val presets = boundViewModel().uiState.value.presets

        assertEquals(
            listOf(
                DeviceLightManualPresetId.NATURAL_AQUARIUM,
                DeviceLightManualPresetId.PLANTED_AQUARIUM,
                DeviceLightManualPresetId.RED_PLANTS,
                DeviceLightManualPresetId.VIVID_COLORS,
                DeviceLightManualPresetId.LOW_TECH,
                DeviceLightManualPresetId.AQUASCAPE
            ),
            presets.map(DeviceLightManualPresetUiState::id)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.NATURAL_AQUARIUM,
            listOf(PERCENT_45, PERCENT_50, PERCENT_50, PERCENT_60)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.PLANTED_AQUARIUM,
            listOf(PERCENT_60, PERCENT_50, PERCENT_65, PERCENT_55)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.RED_PLANTS,
            listOf(PERCENT_65, PERCENT_45, PERCENT_70, PERCENT_45)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.VIVID_COLORS,
            listOf(PERCENT_65, PERCENT_50, PERCENT_65, PERCENT_60)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.LOW_TECH,
            listOf(PERCENT_30, PERCENT_30, PERCENT_30, PERCENT_35)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.AQUASCAPE,
            listOf(PERCENT_55, PERCENT_55, PERCENT_60, PERCENT_65)
        )
    }

    private fun boundViewModel(
        manualOperations: DeviceLightManualOperations = FakeManualOperations(),
        libraryOperations: DeviceLightLibraryOperations = FakeLibraryOperations(),
        rootOperations: DeviceRootOperations = FakeRootOperations(onlineRoot())
    ) = DeviceLightManualControlViewModel(
        manualOperations = manualOperations,
        libraryOperations = libraryOperations,
        rootOperations = rootOperations
    ).apply {
        bind(DEVICE_UID)
    }

    private class FakeManualOperations(
        channels: List<DeviceLightManualChannel> = DeviceLightManualChannel.entries,
        private val mutationGate: CompletableDeferred<Unit>? = null
    ) : DeviceLightManualOperations {
        private val initialSnapshot = snapshot(
            channels.associateWith { channel ->
                when (channel) {
                    DeviceLightManualChannel.RED -> INITIAL_RED_PERCENT
                    DeviceLightManualChannel.GREEN -> INITIAL_GREEN_PERCENT
                    DeviceLightManualChannel.BLUE -> INITIAL_BLUE_PERCENT
                    DeviceLightManualChannel.WHITE -> INITIAL_WHITE_PERCENT
                }
            }
        )
        private val results = MutableStateFlow<DeviceLightManualReadResult>(
            DeviceLightManualReadResult.Available(initialSnapshot)
        )
        val setScenes = mutableListOf<DeviceLightManualScene>()
        var turnOffCalls = 0

        override fun observe(deviceUid: String): Flow<DeviceLightManualReadResult> = results

        override suspend fun setScene(
            deviceUid: String,
            scene: DeviceLightManualScene
        ): DeviceLightManualMutationResult {
            setScenes += scene
            mutationGate?.await()
            return publish(snapshot(scene.channels))
        }

        override suspend fun turnOff(deviceUid: String): DeviceLightManualMutationResult {
            turnOffCalls += 1
            val current = (results.value as DeviceLightManualReadResult.Available).snapshot
            return publish(snapshot(current.scene.channels.mapValues { 0 }))
        }

        private fun publish(snapshot: DeviceLightManualSnapshot): DeviceLightManualMutationResult {
            results.value = DeviceLightManualReadResult.Available(snapshot)
            return DeviceLightManualMutationResult.Success(snapshot)
        }

        fun publishWriteAuthority(authoritative: Boolean) {
            val current = (results.value as DeviceLightManualReadResult.Available).snapshot
            results.value = DeviceLightManualReadResult.Available(
                current.copy(firmwareWriteAuthoritative = authoritative)
            )
        }

        fun publishMode(mode: DeviceLightManualMode) {
            val current = (results.value as DeviceLightManualReadResult.Available).snapshot
            results.value = DeviceLightManualReadResult.Available(current.copy(activeMode = mode))
        }

        fun publishFailure(failure: DeviceLightManualFailure) {
            results.value = DeviceLightManualReadResult.Failed(failure)
        }

        private fun snapshot(
            channels: Map<DeviceLightManualChannel, Int>
        ): DeviceLightManualSnapshot {
            val supportsWhite = DeviceLightManualChannel.WHITE in channels
            val descriptors = channels.keys.mapIndexed { index, channel ->
                DeviceLightManualChannelDescriptor(
                    channel = channel,
                    key = channel.name.lowercase(),
                    displayName = "Firmware ${channel.name}",
                    displayColorRgb = channel.displayColorRgb(),
                    order = index
                )
            }
            return DeviceLightManualSnapshot(
                deviceUid = DEVICE_UID,
                productKey = if (supportsWhite) WRGB_PRODUCT_KEY else RGB_PRODUCT_KEY,
                activeMode = DeviceLightManualMode.MANUAL,
                channelDescriptors = descriptors,
                scene = DeviceLightManualScene(channels),
                estimatedLedPowerWatts = ESTIMATED_POWER_WATTS.takeIf { supportsWhite },
                estimatedLedPowerRatio = ESTIMATED_POWER_RATIO.takeIf { supportsWhite },
                effectiveOutputDisplayColorRgb = ESTIMATED_POWER_DISPLAY_RGB
                    .takeIf { supportsWhite },
                protection = null,
                firmwareWriteAuthoritative = true
            )
        }
    }

    private class FakeRootOperations(initial: DeviceRootSnapshot?) : DeviceRootOperations {
        private val snapshots = MutableStateFlow(initial)
        var connectCalls = 0

        override fun observe(deviceUid: String): Flow<DeviceRootSnapshot?> = snapshots

        override fun current(deviceUid: String): DeviceRootSnapshot? = snapshots.value

        override fun connect(deviceUid: String): Result<Unit> {
            connectCalls += 1
            return Result.success(Unit)
        }

        override fun authorizeRoute(deviceUid: String, route: DeviceRootRoute): Boolean = true

        fun publish(snapshot: DeviceRootSnapshot?) {
            snapshots.value = snapshot
        }
    }

    private class FakeLibraryOperations : DeviceLightLibraryOperations {
        private val result = DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(
                target = DeviceLightLibraryTarget(
                    deviceUid = DEVICE_UID,
                    productKey = WRGB_PRODUCT_KEY,
                    channelDescriptors = DeviceLightLibraryChannel.entries.mapIndexed { index, channel ->
                        DeviceLightLibraryChannelDescriptor(
                            channel = channel,
                            key = channel.name.lowercase(),
                            displayName = channel.name,
                            displayColorRgb = index,
                            order = index
                        )
                    },
                    estimatedPowerWatts = ESTIMATED_POWER_WATTS
                ),
                entries = emptyList()
            )
        )

        override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> =
            flowOf(result)

        override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()


        override suspend fun saveManual(
            deviceUid: String,
            name: String,
            scene: DeviceLightLibraryScene
        ) = DeviceLightLibraryMutationResult.Success("manual")

        override suspend fun saveCustom(
            deviceUid: String,
            name: String,
            weekdaysMask: Int,
            points: List<DeviceLightLibraryCustomPoint>
        ) = DeviceLightLibraryMutationResult.Success("custom")

        override suspend fun rename(entryId: String, name: String) =
            DeviceLightLibraryMutationResult.Success(entryId)

        override suspend fun delete(entryId: String) =
            DeviceLightLibraryMutationResult.Success(entryId)
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

    private fun assertScene(
        presets: List<DeviceLightManualPresetUiState>,
        presetId: DeviceLightManualPresetId,
        expectedPercents: List<Int>
    ) {
        val scene = presets.single { preset -> preset.id == presetId }.scene
        DeviceLightManualChannelId.entries.zip(expectedPercents).forEach { (channel, percent) ->
            assertEquals(percent, scene[channel])
        }
    }

    private fun DeviceLightManualControlUiState.percent(
        channelId: DeviceLightManualChannelId
    ): Int = channels.single { channel -> channel.id == channelId }.percent

    private fun DeviceLightManualScene.value(channel: DeviceLightManualChannel): Int =
        channels.getValue(channel)

    private companion object {
        const val DEVICE_UID = "light-manual-device"
        const val WRGB_PRODUCT_KEY = "LIGHT_WRGB_PRO_ELITE"
        const val RGB_PRODUCT_KEY = "LIGHT_RGB_PRO_SLIM"
        const val INITIAL_RED_PERCENT = 20
        const val INITIAL_GREEN_PERCENT = 30
        const val INITIAL_BLUE_PERCENT = 40
        const val INITIAL_WHITE_PERCENT = 50
        const val UPDATED_RED_PERCENT = 64
        const val CHANNEL_STEP = 1
        const val ESTIMATED_POWER_WATTS = 46
        const val ESTIMATED_POWER_RATIO = 0.46f
        const val ESTIMATED_POWER_DISPLAY_RGB = 0xD8D1FF
        const val RED_RGB = 0xFF0000
        const val GREEN_RGB = 0x00FF00
        const val BLUE_RGB = 0x0000FF
        const val WHITE_RGB = 0xFFFFFF
        const val PERCENT_30 = 30
        const val PERCENT_35 = 35
        const val PERCENT_40 = 40
        const val PERCENT_45 = 45
        const val PERCENT_50 = 50
        const val PERCENT_55 = 55
        const val PERCENT_60 = 60
        const val PERCENT_65 = 65
        const val PERCENT_70 = 70

        fun onlineRoot(
            availability: OwnerDeviceAvailability = OwnerDeviceAvailability.REACHABLE
        ) = DeviceRootSnapshot(
            deviceUid = DEVICE_UID,
            title = "Manual Light",
            availability = availability,
            family = OwnerDeviceFamily.LIGHT,
            catalogState = DeviceRootCatalogState.VALID,
            productKey = WRGB_PRODUCT_KEY
        )
    }
}

private fun DeviceLightManualChannel.displayColorRgb(): Int = when (this) {
    DeviceLightManualChannel.RED -> 0xFF0000
    DeviceLightManualChannel.GREEN -> 0x00FF00
    DeviceLightManualChannel.BLUE -> 0x0000FF
    DeviceLightManualChannel.WHITE -> 0xFFFFFF
}
