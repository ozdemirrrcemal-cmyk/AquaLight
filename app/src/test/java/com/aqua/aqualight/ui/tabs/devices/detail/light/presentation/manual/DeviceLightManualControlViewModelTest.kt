package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLightManualControlViewModelTest {

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

    private fun boundViewModel() = DeviceLightManualControlViewModel().apply {
        bind(DEVICE_UID)
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
