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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
    fun `preview starts without protection banner or loading`() {
        val viewModel = boundViewModel()

        assertNull(viewModel.uiState.value.protection)
        assertFalse(viewModel.uiState.value.showGlobalLoading)
        assertTrue(viewModel.uiState.value.contentEnabled)
    }

    @Test
    fun `slider change updates requested value without starting loading`() {
        val viewModel = boundViewModel()

        viewModel.updateChannel(DeviceLightManualChannelId.RED, UPDATED_RED_PERCENT)

        assertEquals(
            UPDATED_RED_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.RED)
        )
        assertEquals(
            INITIAL_GREEN_PERCENT,
            viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN)
        )
        assertFalse(viewModel.uiState.value.showGlobalLoading)
        assertNull(viewModel.uiState.value.selectedPreset)
    }

    @Test
    fun `firmware documented built in scenes remain frozen`() {
        val presets = boundViewModel().uiState.value.presets

        assertScene(
            presets,
            DeviceLightManualPresetId.RED,
            listOf(PERCENT_85, PERCENT_50, PERCENT_55, PERCENT_35)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.GREEN,
            listOf(PERCENT_60, PERCENT_85, PERCENT_65, PERCENT_40)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.BLUE,
            listOf(PERCENT_50, PERCENT_60, PERCENT_85, PERCENT_35)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.FISH,
            listOf(PERCENT_80, PERCENT_45, PERCENT_70, PERCENT_45)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.SHRIMP,
            listOf(PERCENT_85, PERCENT_70, PERCENT_65, PERCENT_50)
        )
        assertScene(
            presets,
            DeviceLightManualPresetId.ALL,
            listOf(PERCENT_70, PERCENT_70, PERCENT_70, PERCENT_70)
        )
    }

    @Test
    fun `preset selection copies every requested channel`() {
        val viewModel = boundViewModel()

        viewModel.applyPreset(DeviceLightManualPresetId.FISH)

        assertEquals(PERCENT_80, viewModel.uiState.value.percent(DeviceLightManualChannelId.RED))
        assertEquals(PERCENT_45, viewModel.uiState.value.percent(DeviceLightManualChannelId.GREEN))
        assertEquals(PERCENT_70, viewModel.uiState.value.percent(DeviceLightManualChannelId.BLUE))
        assertEquals(PERCENT_45, viewModel.uiState.value.percent(DeviceLightManualChannelId.WHITE))
        assertEquals(DeviceLightManualPresetId.FISH, viewModel.uiState.value.selectedPreset)
    }

    @Test
    fun `turn off clears every requested channel without loading`() {
        val viewModel = boundViewModel()

        viewModel.turnOff()

        assertTrue(viewModel.uiState.value.channels.all { channel -> channel.percent == 0 })
        assertFalse(viewModel.uiState.value.showGlobalLoading)
    }

    @Test
    fun `rgb slim removes white channel and estimated power section`() {
        val viewModel = boundViewModel(
            FakeLibraryOperations(
                productKey = "LIGHT_RGB_PRO_SLIM",
                channels = listOf(
                    DeviceLightLibraryChannel.RED,
                    DeviceLightLibraryChannel.GREEN,
                    DeviceLightLibraryChannel.BLUE
                ),
                estimatedPowerWatts = null
            )
        )

        assertFalse(
            viewModel.uiState.value.channels.any { channel ->
                channel.id == DeviceLightManualChannelId.WHITE
            }
        )
        assertNull(viewModel.uiState.value.power)
    }

    private fun boundViewModel(
        operations: DeviceLightLibraryOperations = FakeLibraryOperations()
    ) = DeviceLightManualControlViewModel(
        libraryOperations = operations
    ).apply {
        bind(DEVICE_UID)
    }

    private class FakeLibraryOperations(
        productKey: String = "LIGHT_WRGB_PRO_ELITE",
        channels: List<DeviceLightLibraryChannel> = listOf(
            DeviceLightLibraryChannel.RED,
            DeviceLightLibraryChannel.GREEN,
            DeviceLightLibraryChannel.BLUE,
            DeviceLightLibraryChannel.WHITE
        ),
        estimatedPowerWatts: Int? = 46
    ) : DeviceLightLibraryOperations {
        private val result = DeviceLightLibraryResult.Available(
            DeviceLightLibrarySnapshot(
                target = DeviceLightLibraryTarget(
                    deviceUid = DEVICE_UID,
                    productKey = productKey,
                    channels = channels,
                    estimatedPowerWatts = estimatedPowerWatts
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
        val channelOrder = listOf(
            DeviceLightManualChannelId.RED,
            DeviceLightManualChannelId.GREEN,
            DeviceLightManualChannelId.BLUE,
            DeviceLightManualChannelId.WHITE
        )
        channelOrder.zip(expectedPercents).forEach { (channelId, expectedPercent) ->
            assertEquals(expectedPercent, scene[channelId])
        }
    }

    private fun DeviceLightManualControlUiState.percent(
        channelId: DeviceLightManualChannelId
    ): Int = channels.single { channel -> channel.id == channelId }.percent

    private companion object {
        const val DEVICE_UID = "light-manual-preview"
        const val UPDATED_RED_PERCENT = 64
        const val INITIAL_GREEN_PERCENT = 30
        const val PERCENT_35 = 35
        const val PERCENT_40 = 40
        const val PERCENT_45 = 45
        const val PERCENT_50 = 50
        const val PERCENT_55 = 55
        const val PERCENT_60 = 60
        const val PERCENT_65 = 65
        const val PERCENT_70 = 70
        const val PERCENT_80 = 80
        const val PERCENT_85 = 85
    }
}
