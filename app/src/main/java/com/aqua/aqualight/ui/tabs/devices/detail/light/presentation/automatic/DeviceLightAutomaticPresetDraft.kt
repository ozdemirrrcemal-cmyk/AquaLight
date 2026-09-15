package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.preset.DeviceLightBuiltInPreset
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetScene
import kotlin.math.abs

internal fun DeviceLightAutomaticEditorDraft.withPreset(
    preset: DeviceLightBuiltInPreset,
    source: DeviceLightAutomaticEditorSource
): DeviceLightAutomaticEditorDraft {
    val schedule = preset.automaticSchedule
    val requestedRampMs = schedule.rampMinutes * MILLIS_PER_MINUTE
    val supportedRampMs = source.policy.rampDurationsMs.minByOrNull { duration ->
        abs(duration - requestedRampMs)
    } ?: return this
    return copy(
        weekdaysMask = DEVICE_LIGHT_AUTOMATIC_EVERY_DAY_MASK,
        startTimeMs = snapAutomaticCycleTime(
            schedule.startMinuteOfDay * MILLIS_PER_MINUTE,
            source.policy.timeStepMs
        ),
        endTimeMs = snapAutomaticCycleTime(
            schedule.endMinuteOfDay * MILLIS_PER_MINUTE,
            source.policy.timeStepMs
        ),
        rampDurationMs = supportedRampMs,
        channels = source.channels.associateWith { channel -> preset.scene.value(channel) }
    )
}

private fun DeviceLightPresetScene.value(channel: DeviceLightAutomaticChannel): Int =
    when (channel) {
        DeviceLightAutomaticChannel.RED -> red
        DeviceLightAutomaticChannel.GREEN -> green
        DeviceLightAutomaticChannel.BLUE -> blue
        DeviceLightAutomaticChannel.WHITE -> white
    }

private const val MILLIS_PER_MINUTE = 60_000L
