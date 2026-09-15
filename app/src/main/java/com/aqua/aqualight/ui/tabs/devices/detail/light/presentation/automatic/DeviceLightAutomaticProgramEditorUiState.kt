package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.automatic

import android.os.Bundle
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgramDraft
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.preset.DeviceLightPresetId
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal sealed interface DeviceLightAutomaticEditorMode {
    data object Create : DeviceLightAutomaticEditorMode
    data class Edit(val programId: String) : DeviceLightAutomaticEditorMode
    data class Duplicate(val sourceProgramId: String) : DeviceLightAutomaticEditorMode
}

internal enum class DeviceLightAutomaticTimeField {
    START,
    END
}

internal data class DeviceLightAutomaticEditorDraft(
    val weekdaysMask: Int = EMPTY_WEEKDAYS_MASK,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val rampDurationMs: Long? = null,
    val channels: Map<DeviceLightAutomaticChannel, Int> = emptyMap(),
    val enabled: Boolean = true
) {
    fun toMutationDraftOrNull(
        source: DeviceLightAutomaticEditorSource
    ): DeviceLightAutomaticProgramDraft? {
        val start = startTimeMs
        val end = endTimeMs
        val ramp = rampDurationMs
        val complete = start != null && end != null && ramp != null &&
            channels.keys == source.channels.toSet() &&
            ramp in source.policy.rampDurationsMs &&
            start % source.policy.timeStepMs == 0L &&
            end % source.policy.timeStepMs == 0L
        if (!complete) return null
        return runCatching {
            DeviceLightAutomaticProgramDraft(
                weekdaysMask = weekdaysMask,
                startTimeMs = requireNotNull(start),
                endTimeMs = requireNotNull(end),
                rampDurationMs = requireNotNull(ramp),
                scene = DeviceLightAutomaticScene(channels)
            )
        }.getOrNull()
    }

    fun writeTo(outState: Bundle) {
        outState.putInt(STATE_WEEKDAYS_MASK, weekdaysMask)
        startTimeMs?.let { value -> outState.putLong(STATE_START_TIME, value) }
        endTimeMs?.let { value -> outState.putLong(STATE_END_TIME, value) }
        rampDurationMs?.let { value -> outState.putLong(STATE_RAMP_DURATION, value) }
        outState.putBoolean(STATE_ENABLED, enabled)
        DeviceLightAutomaticChannel.entries.forEach { channel ->
            outState.putInt(STATE_CHANNEL_PREFIX + channel.name, channels[channel] ?: ABSENT_CHANNEL)
        }
    }

    companion object {
        fun empty(channels: List<DeviceLightAutomaticChannel>) =
            DeviceLightAutomaticEditorDraft(
                channels = channels.associateWith { EMPTY_CHANNEL_PERCENT }
            )

        fun forNewProgram(channels: List<DeviceLightAutomaticChannel>) =
            empty(channels).copy(
                startTimeMs = DEFAULT_START_TIME_MS,
                endTimeMs = DEFAULT_END_TIME_MS
            )

        fun fromProgram(program: DeviceLightAutomaticProgram) =
            DeviceLightAutomaticEditorDraft(
                weekdaysMask = program.weekdaysMask,
                startTimeMs = program.startTimeMs,
                endTimeMs = program.endTimeMs,
                rampDurationMs = program.rampDurationMs,
                channels = program.scene.channels,
                enabled = program.enabled
            )

        fun restore(state: Bundle): DeviceLightAutomaticEditorDraft? = runCatching {
            val channelValues = DeviceLightAutomaticChannel.entries.mapNotNull { channel ->
                state.getInt(STATE_CHANNEL_PREFIX + channel.name, ABSENT_CHANNEL)
                    .takeUnless { value -> value == ABSENT_CHANNEL }
                    ?.let { value -> channel to value }
            }.toMap()
            DeviceLightAutomaticEditorDraft(
                weekdaysMask = state.getInt(STATE_WEEKDAYS_MASK, EMPTY_WEEKDAYS_MASK),
                startTimeMs = state.optionalLong(STATE_START_TIME),
                endTimeMs = state.optionalLong(STATE_END_TIME),
                rampDurationMs = state.optionalLong(STATE_RAMP_DURATION),
                channels = channelValues,
                enabled = state.getBoolean(STATE_ENABLED, true)
            )
        }.getOrNull()
    }
}

internal data class DeviceLightAutomaticEditorSource(
    val deviceUid: String,
    val revision: Long,
    val programCount: Int,
    val policy: DeviceLightAutomaticPolicy,
    val channels: List<DeviceLightAutomaticChannel>,
    val baselineDraft: DeviceLightAutomaticEditorDraft
)

internal data class DeviceLightAutomaticProgramEditorUiState(
    val mode: DeviceLightAutomaticEditorMode = DeviceLightAutomaticEditorMode.Create,
    val source: DeviceLightAutomaticEditorSource? = null,
    val draft: DeviceLightAutomaticEditorDraft = DeviceLightAutomaticEditorDraft(),
    val selectedPresetId: DeviceLightPresetId? = null,
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false,
    val loadFailed: Boolean = false
) {
    val contentEnabled: Boolean
        get() = source != null && !loadFailed && !operationInProgress

    val hasUnsavedChanges: Boolean
        get() = source?.baselineDraft?.let { baseline -> draft != baseline } == true

    val canSave: Boolean
        get() {
            val currentSource = source ?: return false
            val hasCapacity = mode is DeviceLightAutomaticEditorMode.Edit ||
                currentSource.programCount < currentSource.policy.capacity
            return contentEnabled && hasCapacity && hasUnsavedChanges &&
                draft.toMutationDraftOrNull(currentSource) != null
        }
}

private fun Bundle.optionalLong(key: String): Long? =
    if (containsKey(key)) getLong(key) else null

private const val EMPTY_WEEKDAYS_MASK = 0
private const val EMPTY_CHANNEL_PERCENT = 0
private const val MILLIS_PER_HOUR = 3_600_000L
private const val DEFAULT_START_HOUR = 10L
private const val DEFAULT_END_HOUR = 17L
private const val DEFAULT_START_TIME_MS = DEFAULT_START_HOUR * MILLIS_PER_HOUR
private const val DEFAULT_END_TIME_MS = DEFAULT_END_HOUR * MILLIS_PER_HOUR
private const val ABSENT_CHANNEL = -1
private const val STATE_WEEKDAYS_MASK = "light_auto_editor_weekdays_mask"
private const val STATE_START_TIME = "light_auto_editor_start_time"
private const val STATE_END_TIME = "light_auto_editor_end_time"
private const val STATE_RAMP_DURATION = "light_auto_editor_ramp_duration"
private const val STATE_ENABLED = "light_auto_editor_enabled"
private const val STATE_CHANNEL_PREFIX = "light_auto_editor_channel_"
