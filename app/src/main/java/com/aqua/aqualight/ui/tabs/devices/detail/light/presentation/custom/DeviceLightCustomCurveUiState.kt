package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import android.os.Bundle
import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.ui.common.devicepresence.DeviceConnectionVisualState

internal enum class DeviceLightCustomChannelId(@StringRes val labelRes: Int) {
    RED(R.string.device_light_live_output_red),
    GREEN(R.string.device_light_live_output_green),
    BLUE(R.string.device_light_live_output_blue),
    WHITE(R.string.device_light_live_output_white)
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
        @Suppress("ReturnCount")
        fun restore(state: Bundle): DeviceLightCustomDraft? {
            val times = state.getLongArray(STATE_POINT_TIMES) ?: return null
            val channelValues = DeviceLightCustomChannelId.entries.associateWith { channel ->
                state.getIntArray(STATE_CHANNEL_PREFIX + channel.name) ?: return null
            }
            if (channelValues.values.any { values -> values.size != times.size }) return null
            return runCatching {
                DeviceLightCustomDraft(
                    weekdaysMask = state.getInt(STATE_WEEKDAYS_MASK, EVERY_DAY_MASK),
                    points = times.mapIndexed { index, timeMs ->
                        DeviceLightCustomPointUiState(
                            timeMs = timeMs,
                            channels = channelValues.mapNotNull { (channel, values) ->
                                values[index].takeUnless { value -> value == ABSENT_CHANNEL }
                                    ?.let { value -> channel to value }
                            }.toMap()
                        )
                    }
                )
            }.getOrNull()
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
    val maxPoints: Int = MAX_POINT_CAPACITY,
    val timeStepMs: Long = MILLIS_PER_MINUTE,
    val contentEnabled: Boolean = false,
    val initialLoading: Boolean = false,
    val operationInProgress: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val readFailed: Boolean = false
) {
    val selectedPoint: DeviceLightCustomPointUiState?
        get() = draft.points.singleOrNull { point -> point.timeMs == selectedTimeMs }

    val canSaveAs: Boolean
        get() = contentEnabled && !operationInProgress && draft.points.isNotEmpty()
}

internal fun DeviceLightCustomChannel.toUiChannel(): DeviceLightCustomChannelId = when (this) {
    DeviceLightCustomChannel.RED -> DeviceLightCustomChannelId.RED
    DeviceLightCustomChannel.GREEN -> DeviceLightCustomChannelId.GREEN
    DeviceLightCustomChannel.BLUE -> DeviceLightCustomChannelId.BLUE
    DeviceLightCustomChannel.WHITE -> DeviceLightCustomChannelId.WHITE
}

internal const val EVERY_DAY_MASK = 127
internal const val MILLIS_PER_MINUTE = 60_000L
internal const val MINUTES_PER_DAY = 1_440
internal const val MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE
internal const val MAX_POINT_CAPACITY = 96
internal const val MIN_LIGHT_CHANNEL_PERCENT = 0
internal const val MAX_LIGHT_CHANNEL_PERCENT = 100
private const val DEFAULT_PREVIEW_MINUTES = 15 * 60 + 30
private const val DEFAULT_PREVIEW_TIME_MS = DEFAULT_PREVIEW_MINUTES * MILLIS_PER_MINUTE
private const val ABSENT_CHANNEL = -1
private const val STATE_WEEKDAYS_MASK = "light_custom_weekdays_mask"
private const val STATE_POINT_TIMES = "light_custom_point_times"
private const val STATE_CHANNEL_PREFIX = "light_custom_channel_"
