package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.preset

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetCatalog
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightPresetId
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.DeviceLightAutomaticEditorDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic.editor.DeviceLightAutomaticEditorSource
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightAutomaticPresetDraftTest {

    @Test
    fun naturalPresetFillsTheEditorDraftWithoutApplyingToTheDevice() {
        val source = source(DeviceLightAutomaticChannel.entries)
        val preset = requireNotNull(DeviceLightPresetCatalog.find(DeviceLightPresetId.NATURAL_AQUARIUM))

        val draft = DeviceLightAutomaticEditorDraft.empty(source.channels).withPreset(preset, source)

        assertEquals(EVERY_DAY_MASK, draft.weekdaysMask)
        assertEquals(minutes(10 * MINUTES_PER_HOUR), draft.startTimeMs)
        assertEquals(minutes(17 * MINUTES_PER_HOUR), draft.endTimeMs)
        assertEquals(minutes(60), draft.rampDurationMs)
        assertEquals(
            mapOf(
                DeviceLightAutomaticChannel.RED to 45,
                DeviceLightAutomaticChannel.GREEN to 50,
                DeviceLightAutomaticChannel.BLUE to 50,
                DeviceLightAutomaticChannel.WHITE to 60
            ),
            draft.channels
        )
    }

    @Test
    fun presetOnlyWritesChannelsSupportedByTheConnectedProduct() {
        val channels = listOf(
            DeviceLightAutomaticChannel.RED,
            DeviceLightAutomaticChannel.GREEN,
            DeviceLightAutomaticChannel.BLUE
        )
        val source = source(channels)
        val preset = requireNotNull(DeviceLightPresetCatalog.find(DeviceLightPresetId.AQUASCAPE))

        val draft = DeviceLightAutomaticEditorDraft.empty(channels).withPreset(preset, source)

        assertEquals(channels.toSet(), draft.channels.keys)
        assertEquals(55, draft.channels[DeviceLightAutomaticChannel.RED])
        assertEquals(55, draft.channels[DeviceLightAutomaticChannel.GREEN])
        assertEquals(60, draft.channels[DeviceLightAutomaticChannel.BLUE])
    }

    @Test
    fun unsupportedRampUsesTheNearestFirmwareDuration() {
        val source = source(
            DeviceLightAutomaticChannel.entries,
            rampMinutes = listOf(0, 30, 60, 90)
        )
        val preset = requireNotNull(DeviceLightPresetCatalog.find(DeviceLightPresetId.NEW_SETUP))

        val draft = DeviceLightAutomaticEditorDraft.empty(source.channels).withPreset(preset, source)

        assertEquals(minutes(90), draft.rampDurationMs)
    }

    private fun source(
        channels: List<DeviceLightAutomaticChannel>,
        rampMinutes: List<Int> = listOf(0, 30, 60, 90, 120, 150)
    ) = DeviceLightAutomaticEditorSource(
        deviceUid = "preset-draft-device",
        revision = 3L,
        programCount = 0,
        policy = DeviceLightAutomaticPolicy(
            capacity = 16,
            timeStepMs = minutes(1),
            rampDurationsMs = rampMinutes.map(::minutes)
        ),
        channels = channels,
        baselineDraft = DeviceLightAutomaticEditorDraft.empty(channels)
    )
}

private fun minutes(value: Int): Long = value * MILLIS_PER_MINUTE

private const val EVERY_DAY_MASK = 0x7f
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_MINUTE = 60_000L
