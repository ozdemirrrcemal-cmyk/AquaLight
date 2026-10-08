package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightCustomCurvePreviewValuesTest {

    @Test
    fun `preview values follow playhead without changing selected edit point`() {
        val selected = point(8, 0, ChannelValues(0, 0, 0, 0))
        val plateauStart = point(10, 0, ChannelValues(30, 52, 60, 50))
        val plateauEnd = point(16, 0, ChannelValues(30, 52, 60, 50))
        val state = DeviceLightCustomCurveUiState(
            channels = CHANNELS,
            draft = DeviceLightCustomDraft(
                points = listOf(
                    selected,
                    plateauStart,
                    plateauEnd,
                    point(18, 0, ChannelValues(0, 0, 0, 0))
                )
            ),
            selectedTimeMs = selected.timeMs,
            previewTimeMs = timeMs(10, 39),
            playheadMode = DeviceLightCustomPlayheadMode.PREVIEW
        )

        assertEquals(selected, state.selectedPoint)
        assertEquals(timeMs(10, 39), state.valuesPoint?.timeMs)
        assertEquals(
            mapOf(
                DeviceLightCustomChannelId.RED to 30,
                DeviceLightCustomChannelId.GREEN to 52,
                DeviceLightCustomChannelId.BLUE to 60,
                DeviceLightCustomChannelId.WHITE to 50
            ),
            state.valuesPoint?.channels
        )
    }

    @Test
    fun `preview values interpolate ramp at playhead`() {
        val state = DeviceLightCustomCurveUiState(
            channels = CHANNELS,
            draft = DeviceLightCustomDraft(
                points = listOf(
                    point(8, 0, ChannelValues(0, 0, 0, 0)),
                    point(10, 0, ChannelValues(30, 50, 60, 40))
                )
            ),
            selectedTimeMs = timeMs(8, 0),
            previewTimeMs = timeMs(9, 0),
            playheadMode = DeviceLightCustomPlayheadMode.PREVIEW
        )

        assertEquals(
            mapOf(
                DeviceLightCustomChannelId.RED to 15,
                DeviceLightCustomChannelId.GREEN to 25,
                DeviceLightCustomChannelId.BLUE to 30,
                DeviceLightCustomChannelId.WHITE to 20
            ),
            state.valuesPoint?.channels
        )
    }

    @Test
    fun `editor values remain bound to selected point outside preview`() {
        val selected = point(8, 0, ChannelValues(0, 0, 0, 0))
        val state = DeviceLightCustomCurveUiState(
            channels = CHANNELS,
            draft = DeviceLightCustomDraft(
                points = listOf(
                    selected,
                    point(10, 0, ChannelValues(30, 50, 60, 40))
                )
            ),
            selectedTimeMs = selected.timeMs,
            previewTimeMs = timeMs(9, 0),
            playheadMode = DeviceLightCustomPlayheadMode.EDIT
        )

        assertEquals(selected.timeMs, state.valuesPoint?.timeMs)
        assertEquals(selected.channels, state.valuesPoint?.channels)
    }

    private fun point(
        hour: Int,
        minute: Int,
        values: ChannelValues
    ) = DeviceLightCustomPointUiState(
        timeMs = timeMs(hour, minute),
        channels = mapOf(
            DeviceLightCustomChannelId.RED to values.red,
            DeviceLightCustomChannelId.GREEN to values.green,
            DeviceLightCustomChannelId.BLUE to values.blue,
            DeviceLightCustomChannelId.WHITE to values.white
        )
    )

    private data class ChannelValues(
        val red: Int,
        val green: Int,
        val blue: Int,
        val white: Int
    )

    private fun timeMs(hour: Int, minute: Int): Long =
        (hour * 60L + minute) * MILLIS_PER_MINUTE

    private companion object {
        val CHANNELS = listOf(
            DeviceLightCustomChannelId.RED,
            DeviceLightCustomChannelId.GREEN,
            DeviceLightCustomChannelId.BLUE,
            DeviceLightCustomChannelId.WHITE
        )
    }
}
