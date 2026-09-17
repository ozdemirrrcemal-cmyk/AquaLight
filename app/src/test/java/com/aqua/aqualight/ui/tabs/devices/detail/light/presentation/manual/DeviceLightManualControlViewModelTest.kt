package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryChannel
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryCustomPoint
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryKind
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryMutationResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryOperations
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryResult
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryScene
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibrarySnapshot
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryTarget
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualChannel
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualSnapshot
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

        viewModel.applyPreset(DeviceLightManualPresetId.VIVID_COLORS)

        assertEquals(PERCENT_65, viewModel.uiState.value.percent(DeviceLightManualChannelId.RED))
        assertEquals(PERCENT_50, viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN))
        assertEquals(PERCENT_65, viewModel.uiState.value.percent(DeviceLightManualChannelId.BLUE))
        assertEquals(PERCENT_60, viewModel.uiState.value.percent(DeviceLightManualChannelId.WHITE))
        assertEquals(
            DeviceLightManualPresetId.VIVID_COLORS,
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
    fun `curated aquarium scenes remain frozen`() {
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
        libraryOperations: DeviceLightLibraryOperations = FakeLibraryOperations()
    ) = DeviceLightManualControlViewModel(
        manualOperations = manualOperations,
        libraryOperations = libraryOperations
    ).apply {
        bind(DEVICE_UID)
    }

    private class FakeManualOperations(
        channels: List<DeviceLightManualChannel> = DeviceLightManualChannel.entries
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

        private fun snapshot(
            channels: Map<DeviceLightManualChannel, Int>
        ): DeviceLightManualSnapshot {
            val supportsWhite = DeviceLightManualChannel.WHITE in channels
            return DeviceLightManualSnapshot(
                deviceUid = DEVICE_UID,
                productKey = if (supportsWhite) WRGB_PRODUCT_KEY else RGB_PRODUCT_KEY,
                scene = DeviceLightManualScene(channels),
                estimatedPowerWatts = ESTIMATED_POWER_WATTS.takeIf { supportsWhite },
                estimatedPowerRatio = ESTIMATED_POWER_RATIO.takeIf { supportsWhite },
                protection = null
            )
        }
    }

    private class FakeLibraryOperations : DeviceLightLibraryOperations {
        private val result = DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(
                target = DeviceLightLibraryTarget(
                    deviceUid = DEVICE_UID,
                    productKey = WRGB_PRODUCT_KEY,
                    channels = DeviceLightLibraryChannel.entries,
                    estimatedPowerWatts = ESTIMATED_POWER_WATTS
                ),
                entries = emptyList()
            )
        )

        override fun observeLibrary(deviceUid: String): Flow<DeviceLightLibraryResult> =
            flowOf(result)

        override suspend fun usedNames(kind: DeviceLightLibraryKind): List<String> = emptyList()

        override suspend fun refreshInstalledCustom(deviceUid: String) = Unit

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

        override suspend fun load(deviceUid: String, entryId: String) =
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
        const val PERCENT_30 = 30
        const val PERCENT_35 = 35
        const val PERCENT_45 = 45
        const val PERCENT_50 = 50
        const val PERCENT_55 = 55
        const val PERCENT_60 = 60
        const val PERCENT_65 = 65
        const val PERCENT_70 = 70
    }
}
