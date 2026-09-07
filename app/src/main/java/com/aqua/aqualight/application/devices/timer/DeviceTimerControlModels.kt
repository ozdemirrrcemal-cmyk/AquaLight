@file:Suppress("LongParameterList")

package com.aqua.aqualight.application.devices.timer

data class DeviceTimerControlSnapshot(
    val deviceUid: String,
    val revision: Long,
    val lockLoop: Boolean,
    val uptimeMillis: Long,
    val maxSchedulesPerChannel: Int,
    val capabilities: DeviceTimerControlCapabilities,
    val channels: List<DeviceTimerChannelSnapshot>
)

data class DeviceTimerControlCapabilities(
    val readOnly: Boolean,
    val supportsConfigApply: Boolean,
    val supportsChannelState: Boolean,
    val supportsSchedules: Boolean,
    val supportsSpansMidnight: Boolean,
    val supportsTemporaryOverride: Boolean,
    val supportsChannelDisplayName: Boolean
)

data class DeviceTimerChannelSnapshot(
    val slotId: String,
    val channelNumber: Int,
    val defaultName: String,
    val displayName: String,
    val regime: DeviceTimerChannelRegime,
    val operatingState: DeviceTimerOperatingState,
    val scheduleCount: Int,
    val activeScheduleSlotId: Int?,
    val activeScheduleName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAtEpochMillis: Long?,
    val runtimeReason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMillis: Long,
    val outputHealth: DeviceTimerOutputHealth,
    val physicalFeedbackAvailable: Boolean,
    val displayNameEditable: Boolean,
    /** Null until an authoritative channel-scoped status has been read for this revision. */
    val schedules: List<DeviceTimerScheduleSnapshot>?
)

data class DeviceTimerScheduleSnapshot(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val spansMidnight: Boolean
)

data class DeviceTimerScheduleDraft(
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startTimeMillis: Long,
    val endTimeMillis: Long
) {
    init {
        require(name.isNotBlank()) { "Timer schedule name must not be blank." }
        require(weekdays.size == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain Monday through Sunday."
        }
        require(!enabled || weekdays.any { selected -> selected }) {
            "An enabled Timer schedule requires at least one weekday."
        }
        require(startTimeMillis.isWholeMinuteOfDay())
        require(endTimeMillis.isWholeMinuteOfDay())
        require(startTimeMillis != endTimeMillis)
    }

    val spansMidnight: Boolean
        get() = endTimeMillis < startTimeMillis
}

sealed interface DeviceTimerDisplayNameUpdate {
    data object ResetToDefault : DeviceTimerDisplayNameUpdate

    data class Value(val displayName: String) : DeviceTimerDisplayNameUpdate {
        init {
            require(displayName.isNotBlank()) {
                "Use ResetToDefault for an empty Timer display name."
            }
        }
    }
}

enum class DeviceTimerChannelRegime {
    AUTO,
    ON,
    OFF
}

enum class DeviceTimerOperatingState {
    ON,
    OFF
}

enum class DeviceTimerNextTransitionType {
    NONE,
    ON,
    OFF
}

enum class DeviceTimerOutputHealth {
    UNVERIFIED,
    HARDWARE_FAULT
}

enum class DeviceTimerRuntimeReason {
    NOT_EVALUATED,
    HARDWARE_FAULT,
    TEMPORARY_OVERRIDE_ON,
    TEMPORARY_OVERRIDE_OFF,
    MANUAL_ON,
    MANUAL_OFF,
    CLOCK_UNAVAILABLE,
    NO_ENABLED_SCHEDULES,
    SCHEDULE_ACTIVE,
    OUTSIDE_SCHEDULE
}

private fun Long.isWholeMinuteOfDay(): Boolean =
    this in 0L until MILLIS_PER_DAY && this % MILLIS_PER_MINUTE == 0L

private const val TIMER_WEEKDAY_COUNT = 7
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_DAY = 86_400_000L
