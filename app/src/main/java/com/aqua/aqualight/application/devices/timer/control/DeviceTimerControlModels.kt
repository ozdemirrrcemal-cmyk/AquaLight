package com.aqua.aqualight.application.devices.timer.control

data class DeviceTimerControlSnapshot(
    val deviceUid: String,
    val authority: DeviceTimerControlAuthority,
    val capabilities: DeviceTimerControlCapabilities,
    val channels: List<DeviceTimerChannelSnapshot>
) {
    val revision: Long get() = authority.revision
    val lockLoop: Boolean get() = authority.lockLoop
    val uptimeMillis: Long get() = authority.uptimeMillis
    val maxSchedulesPerChannel: Int get() = authority.maxSchedulesPerChannel
}

data class DeviceTimerControlAuthority(
    val revision: Long,
    val lockLoop: Boolean,
    val uptimeMillis: Long,
    val maxSchedulesPerChannel: Int
)

data class DeviceTimerControlCapabilities(
    val readOnly: Boolean,
    val mutations: DeviceTimerMutationCapabilities
) {
    val supportsConfigApply: Boolean get() = mutations.supportsConfigApply
    val supportsChannelState: Boolean get() = mutations.supportsChannelState
    val supportsSchedules: Boolean get() = mutations.supportsSchedules
    val supportsSpansMidnight: Boolean get() = mutations.supportsSpansMidnight
    val supportsTemporaryOverride: Boolean get() = mutations.supportsTemporaryOverride
    val supportsChannelDisplayName: Boolean get() = mutations.supportsChannelDisplayName
}

data class DeviceTimerMutationCapabilities(
    val supportsConfigApply: Boolean,
    val supportsChannelState: Boolean,
    val supportsSchedules: Boolean,
    val supportsSpansMidnight: Boolean,
    val supportsTemporaryOverride: Boolean,
    val supportsChannelDisplayName: Boolean
)

data class DeviceTimerChannelSnapshot(
    val identity: DeviceTimerChannelIdentity,
    val state: DeviceTimerChannelState,
    val transition: DeviceTimerChannelTransition,
    val runtime: DeviceTimerChannelRuntime,
    /** Null until an authoritative channel-scoped status has been read for this revision. */
    val schedules: List<DeviceTimerScheduleSnapshot>?
) {
    val slotId: String get() = identity.slotId
    val channelNumber: Int get() = identity.channelNumber
    val defaultName: String get() = identity.defaultName
    val displayName: String get() = identity.displayName
    val displayNameEditable: Boolean get() = identity.displayNameEditable
    val regime: DeviceTimerChannelRegime get() = state.regime
    val operatingState: DeviceTimerOperatingState get() = state.operatingState
    val scheduleCount: Int get() = state.scheduleCount
    val outputHealth: DeviceTimerOutputHealth get() = state.outputHealth
    val activeScheduleSlotId: Int? get() = transition.activeScheduleSlotId
    val activeScheduleName: String? get() = transition.activeScheduleName
    val nextTransitionType: DeviceTimerNextTransitionType get() = transition.nextTransitionType
    val nextTransitionAtEpochMillis: Long? get() = transition.nextTransitionAtEpochMillis
    val runtimeReason: DeviceTimerRuntimeReason get() = runtime.reason
    val clockReady: Boolean get() = runtime.clockReady
    val temporaryOverrideActive: Boolean get() = runtime.temporaryOverrideActive
    val temporaryOverrideRemainingMillis: Long get() = runtime.temporaryOverrideRemainingMillis
    val physicalFeedbackAvailable: Boolean get() = runtime.physicalFeedbackAvailable
}

data class DeviceTimerChannelIdentity(
    val slotId: String,
    val channelNumber: Int,
    val defaultName: String,
    val displayName: String,
    val displayNameEditable: Boolean
)

data class DeviceTimerChannelState(
    val regime: DeviceTimerChannelRegime,
    val operatingState: DeviceTimerOperatingState,
    val scheduleCount: Int,
    val outputHealth: DeviceTimerOutputHealth
)

data class DeviceTimerChannelTransition(
    val activeScheduleSlotId: Int?,
    val activeScheduleName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAtEpochMillis: Long?
)

data class DeviceTimerChannelRuntime(
    val reason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMillis: Long,
    val physicalFeedbackAvailable: Boolean
)

data class DeviceTimerScheduleSnapshot(
    val identity: DeviceTimerScheduleIdentity,
    val window: DeviceTimerScheduleWindow
) {
    val index: Int get() = identity.index
    val slotId: Int get() = identity.slotId
    val enabled: Boolean get() = identity.enabled
    val name: String get() = identity.name
    val weekdays: List<Boolean> get() = window.weekdays
    val startTimeMillis: Long get() = window.startTimeMillis
    val endTimeMillis: Long get() = window.endTimeMillis
    val spansMidnight: Boolean get() = window.spansMidnight
}

data class DeviceTimerScheduleIdentity(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String
)

data class DeviceTimerScheduleWindow(
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
