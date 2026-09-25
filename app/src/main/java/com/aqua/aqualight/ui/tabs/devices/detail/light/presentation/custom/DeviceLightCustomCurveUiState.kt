package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import android.os.Bundle
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.common.DeviceLightOperationLoadingState

internal enum class DeviceLightCustomChannelId(val wireKey: String) {
    RED("red"),
    GREEN("green"),
    BLUE("blue"),
    WHITE("white")
}

internal enum class DeviceLightCustomPlayheadMode {
    CLOCK,
    EDIT,
    PREVIEW
}

internal data class DeviceLightCustomPointUiState(
    val timeMs: Long,
    val channels: Map<DeviceLightCustomChannelId, Int>
) {
    init {
        require(timeMs in 0 until MILLIS_PER_DAY)
        require(timeMs % MILLIS_PER_MINUTE == 0L)
        require(
            channels.isNotEmpty() && channels.values.all { value ->
                value in MIN_LIGHT_CHANNEL_PERCENT..MAX_LIGHT_CHANNEL_PERCENT
            }
        )
    }
}

internal data class DeviceLightCustomValuesUiState(
    val timeMs: Long,
    val channels: Map<DeviceLightCustomChannelId, Int>
) {
    init {
        require(timeMs in 0 until MILLIS_PER_DAY)
        require(
            channels.isNotEmpty() && channels.values.all { value ->
                value in MIN_LIGHT_CHANNEL_PERCENT..MAX_LIGHT_CHANNEL_PERCENT
            }
        )
    }
}

internal data class DeviceLightCustomDraft(
    val weekdaysMask: Int = EVERY_DAY_MASK,
    val points: List<DeviceLightCustomPointUiState> = emptyList()
) {
    init {
        require(weekdaysMask in 1..EVERY_DAY_MASK)
        require(points.size <= MAX_POINT_CAPACITY)
        require(points.zipWithNext().all { (left, right) -> left.timeMs < right.timeMs })
        require(points.map { point -> point.channels.keys }.distinct().size <= 1)
    }

    fun writeTo(outState: Bundle) {
        outState.putInt(STATE_WEEKDAYS_MASK, weekdaysMask)
        outState.putLongArray(STATE_POINT_TIMES, points.map { point -> point.timeMs }.toLongArray())
        DeviceLightCustomChannelId.entries.forEach { channel ->
            outState.putIntArray(
                STATE_CHANNEL_PREFIX + channel.name,
                points.map { point -> point.channels[channel] ?: ABSENT_CHANNEL }.toIntArray()
            )
        }
    }

    companion object {
        fun restore(state: Bundle): DeviceLightCustomDraft? {
            val times = state.getLongArray(STATE_POINT_TIMES)
            val channelValues = DeviceLightCustomChannelId.entries.associateWith { channel ->
                state.getIntArray(STATE_CHANNEL_PREFIX + channel.name)
            }
            val channelsInvalid = times == null || channelValues.values.any { values ->
                values == null || values.size != times.size
            }
            return if (times == null || channelsInvalid) {
                null
            } else {
                runCatching {
                    DeviceLightCustomDraft(
                        weekdaysMask = state.getInt(STATE_WEEKDAYS_MASK, EVERY_DAY_MASK),
                        points = times.mapIndexed { index, timeMs ->
                            DeviceLightCustomPointUiState(
                                timeMs = timeMs,
                                channels = channelValues.mapNotNull { (channel, values) ->
                                    requireNotNull(values)[index]
                                        .takeUnless { value -> value == ABSENT_CHANNEL }
                                        ?.let { value -> channel to value }
                                }.toMap()
                            )
                        }
                    )
                }.getOrNull()
            }
        }
    }
}

internal data class DeviceLightCustomCurveUiState(
    val deviceUid: String = "",
    val connectionVisualState: DeviceConnectionVisualState? = null,
    val channels: List<DeviceLightCustomChannelId> = emptyList(),
    val draft: DeviceLightCustomDraft = DeviceLightCustomDraft(),
    val selectedTimeMs: Long? = null,
    val previewTimeMs: Long = DEFAULT_PREVIEW_TIME_MS,
    val deviceTimeMs: Long? = null,
    val playheadMode: DeviceLightCustomPlayheadMode = DeviceLightCustomPlayheadMode.CLOCK,
    val maxPoints: Int = MAX_POINT_CAPACITY,
    val timeStepMs: Long = MILLIS_PER_MINUTE,
    val contentEnabled: Boolean = false,
    val firmwareWriteAuthoritative: Boolean = false,
    val deviceProgramInstalled: Boolean = false,
    override val initialLoading: Boolean = false,
    override val operationInProgress: Boolean = false,
    val blockingOperationInProgress: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val hasUnappliedChanges: Boolean = false,
    val readFailed: Boolean = false
) : DeviceLightOperationLoadingState {
    override val showGlobalLoading: Boolean
        get() = initialLoading || blockingOperationInProgress

    val selectedPoint: DeviceLightCustomPointUiState?
        get() = draft.points.singleOrNull { point -> point.timeMs == selectedTimeMs }

    val previewPlaybackActive: Boolean
        get() = playheadMode == DeviceLightCustomPlayheadMode.PREVIEW

    val valuesPoint: DeviceLightCustomValuesUiState?
        get() = if (previewPlaybackActive && draft.points.isNotEmpty()) {
            val timeMs = previewTimeMs.coerceIn(0L, MILLIS_PER_DAY - 1L)
            DeviceLightCustomValuesUiState(
                timeMs = timeMs,
                channels = draft.points.interpolatedChannelsAt(timeMs, channels)
            )
        } else {
            selectedPoint?.let { point ->
                DeviceLightCustomValuesUiState(
                    timeMs = point.timeMs,
                    channels = point.channels
                )
            }
        }

    val canSaveAs: Boolean
        get() = contentEnabled && !operationInProgress && draft.points.isNotEmpty()

    val canPreview: Boolean
        get() = contentEnabled && firmwareWriteAuthoritative &&
            !operationInProgress && draft.points.isNotEmpty()

    val canApplyToDevice: Boolean
        get() = contentEnabled && firmwareWriteAuthoritative &&
            !operationInProgress && draft.points.isNotEmpty() && hasUnappliedChanges

    val canClearDeviceProgram: Boolean
        get() = contentEnabled && firmwareWriteAuthoritative &&
            !operationInProgress && deviceProgramInstalled

    val canDeleteSelectedPoint: Boolean
        get() = contentEnabled && !operationInProgress &&
            selectedPoint != null && draft.points.size > MIN_CUSTOM_POINT_COUNT
}

internal fun DeviceLightCustomChannel.toUiChannel(): DeviceLightCustomChannelId = when (this) {
    DeviceLightCustomChannel.RED -> DeviceLightCustomChannelId.RED
    DeviceLightCustomChannel.GREEN -> DeviceLightCustomChannelId.GREEN
    DeviceLightCustomChannel.BLUE -> DeviceLightCustomChannelId.BLUE
    DeviceLightCustomChannel.WHITE -> DeviceLightCustomChannelId.WHITE
}

internal const val EVERY_DAY_MASK = 127
internal const val MILLIS_PER_MINUTE = 60_000L
internal const val MINUTES_PER_HOUR = 60
internal const val MILLIS_PER_HOUR = MINUTES_PER_HOUR * MILLIS_PER_MINUTE
internal const val MINUTES_PER_DAY = 1_440
internal const val MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE
internal const val CUSTOM_DAY_PREVIEW_DURATION_MS = 24_000L
internal const val MAX_POINT_CAPACITY = 96
internal const val MIN_LIGHT_CHANNEL_PERCENT = 0
internal const val MAX_LIGHT_CHANNEL_PERCENT = 100
private const val DEFAULT_PREVIEW_MINUTES = 15 * 60 + 30
private const val DEFAULT_PREVIEW_TIME_MS = DEFAULT_PREVIEW_MINUTES * MILLIS_PER_MINUTE
private const val ABSENT_CHANNEL = -1
private const val STATE_WEEKDAYS_MASK = "light_custom_weekdays_mask"
private const val STATE_POINT_TIMES = "light_custom_point_times"
private const val STATE_CHANNEL_PREFIX = "light_custom_channel_"
private const val MIN_CUSTOM_POINT_COUNT = 1

internal fun customWeekdayMask(dayIndex: Int): Int {
    require(dayIndex in FIRST_WEEKDAY_INDEX..LAST_WEEKDAY_INDEX)
    return 1 shl (LAST_WEEKDAY_INDEX - dayIndex)
}
