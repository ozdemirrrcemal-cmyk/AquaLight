package com.aqua.aqualight.data.devices.runtime.modules.timer

import org.json.JSONArray
import org.json.JSONObject

enum class DeviceTimerRegime(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off")
}

enum class DeviceTimerOperatingState {
    ON,
    OFF
}

enum class DeviceTimerNextTransitionType(val wireValue: String) {
    NONE("NONE"),
    ON("ON"),
    OFF("OFF")
}

enum class DeviceTimerOutputHealth(val wireValue: String) {
    UNVERIFIED("UNVERIFIED"),
    HARDWARE_FAULT("HARDWARE_FAULT")
}

enum class DeviceTimerRuntimeReason(val wireValue: String) {
    NOT_EVALUATED("notEvaluated"),
    HARDWARE_FAULT("hardwareFault"),
    TEMPORARY_OVERRIDE_ON("temporaryOverrideOn"),
    TEMPORARY_OVERRIDE_OFF("temporaryOverrideOff"),
    MANUAL_ON("manualOn"),
    MANUAL_OFF("manualOff"),
    CLOCK_UNAVAILABLE("clockUnavailable"),
    NO_ENABLED_SCHEDULES("noEnabledSchedules"),
    SCHEDULE_ACTIVE("scheduleActive"),
    OUTSIDE_SCHEDULE("outsideSchedule")
}

data class DeviceTimerRuntimeCapabilities(
    val identity: DeviceTimerRuntimeIdentity,
    val readSupport: DeviceTimerRuntimeReadSupport,
    val mutationSupport: DeviceTimerRuntimeMutationSupport,
    val heap: DeviceTimerRuntimeHeap
) {
    val module: String get() = identity.module
    val configApplyScope: String get() = identity.configApplyScope
    val event: String get() = identity.event
    val readOnly: Boolean get() = readSupport.readOnly
    val supportsChannels: Boolean get() = readSupport.supportsChannels
    val supportsChannelScopedStatus: Boolean get() = readSupport.supportsChannelScopedStatus
    val supportsConfigApply: Boolean get() = mutationSupport.supportsConfigApply
    val supportsChannelSet: Boolean get() = mutationSupport.supportsChannelSet
    val supportsSchedules: Boolean get() = mutationSupport.supportsSchedules
    val supportsSpansMidnight: Boolean get() = mutationSupport.supportsSpansMidnight
    val supportsTemporaryOverride: Boolean get() = mutationSupport.supportsTemporaryOverride
    val supportsChannelScopedConfigApply: Boolean
        get() = mutationSupport.supportsChannelScopedConfigApply
    val internalHeapMinimumFreeBytes: Long get() = heap.minimumFreeBytes
    val internalHeapLargestFreeBlockBytes: Long get() = heap.largestFreeBlockBytes
}

data class DeviceTimerRuntimeIdentity(
    val module: String,
    val configApplyScope: String,
    val event: String
)

data class DeviceTimerRuntimeReadSupport(
    val readOnly: Boolean,
    val supportsChannels: Boolean,
    val supportsChannelScopedStatus: Boolean
)

data class DeviceTimerRuntimeMutationSupport(
    val supportsConfigApply: Boolean,
    val supportsChannelSet: Boolean,
    val supportsSchedules: Boolean,
    val supportsSpansMidnight: Boolean,
    val supportsTemporaryOverride: Boolean,
    val supportsChannelScopedConfigApply: Boolean
)

data class DeviceTimerRuntimeHeap(
    val minimumFreeBytes: Long,
    val largestFreeBlockBytes: Long
)

data class DeviceTimerChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val hardwareCalibration: Boolean
)

data class DeviceTimerChannelStatus(
    val identity: DeviceTimerChannelIdentity,
    val hardware: DeviceTimerChannelHardware,
    val values: DeviceTimerChannelValues,
    val state: DeviceTimerChannelState,
    val transition: DeviceTimerChannelTransition,
    val runtime: DeviceTimerChannelRuntime
) {
    val index: Int get() = identity.index
    val listIndex: Int get() = identity.listIndex
    val key: String get() = identity.key
    val name: String get() = identity.name
    val displayName: String get() = identity.displayName
    val profileManaged: Boolean get() = identity.profileManaged
    val editable: DeviceTimerChannelEditable get() = identity.editable
    val channelKind: String get() = hardware.channelKind
    val gpio: Int get() = hardware.gpio
    val ledcChannel: Int get() = hardware.ledcChannel
    val group: Int get() = hardware.group
    val invert: Boolean get() = hardware.invert
    val pwmResolutionBits: Int get() = hardware.pwmResolutionBits
    val pwmFrequencyHz: Int get() = hardware.pwmFrequencyHz
    val valueNow: Double get() = values.now
    val valueAuto: Double get() = values.automatic
    val valueManual: Double get() = values.manual
    val manualTimeoutMs: Long get() = values.manualTimeoutMs
    val regime: DeviceTimerRegime get() = state.regime
    val outputHealth: DeviceTimerOutputHealth get() = state.outputHealth
    val physicalFeedbackAvailable: Boolean get() = state.physicalFeedbackAvailable
    val scheduleCount: Int get() = state.scheduleCount
    val operatingState: DeviceTimerOperatingState get() = state.operatingState
    val activeSlotId: Int? get() = transition.activeSlotId
    val activeSlotName: String? get() = transition.activeSlotName
    val nextTransitionType: DeviceTimerNextTransitionType get() = transition.nextTransitionType
    val nextTransitionAt: Long? get() = transition.nextTransitionAt
    val runtimeReason: DeviceTimerRuntimeReason get() = runtime.reason
    val clockReady: Boolean get() = runtime.clockReady
    val temporaryOverrideActive: Boolean get() = runtime.temporaryOverrideActive
    val temporaryOverrideRemainingMs: Long get() = runtime.temporaryOverrideRemainingMs
}

data class DeviceTimerChannelIdentity(
    val index: Int,
    val listIndex: Int,
    val key: String,
    val name: String,
    val displayName: String,
    val profileManaged: Boolean,
    val editable: DeviceTimerChannelEditable
)

data class DeviceTimerChannelHardware(
    val channelKind: String,
    val gpio: Int,
    val ledcChannel: Int,
    val group: Int,
    val invert: Boolean,
    val pwmResolutionBits: Int,
    val pwmFrequencyHz: Int
)

data class DeviceTimerChannelValues(
    val now: Double,
    val automatic: Double,
    val manual: Double,
    val manualTimeoutMs: Long
)

data class DeviceTimerChannelState(
    val regime: DeviceTimerRegime,
    val outputHealth: DeviceTimerOutputHealth,
    val physicalFeedbackAvailable: Boolean,
    val scheduleCount: Int,
    val operatingState: DeviceTimerOperatingState
)

data class DeviceTimerChannelTransition(
    val activeSlotId: Int?,
    val activeSlotName: String?,
    val nextTransitionType: DeviceTimerNextTransitionType,
    val nextTransitionAt: Long?
)

data class DeviceTimerChannelRuntime(
    val reason: DeviceTimerRuntimeReason,
    val clockReady: Boolean,
    val temporaryOverrideActive: Boolean,
    val temporaryOverrideRemainingMs: Long
)

data class DeviceTimerScheduleStatus(
    val identity: DeviceTimerScheduleIdentity,
    val window: DeviceTimerScheduleWindow
) {
    val index: Int get() = identity.index
    val slotId: Int get() = identity.slotId
    val enabled: Boolean get() = identity.enabled
    val name: String get() = identity.name
    val channelKey: String get() = identity.channelKey
    val bound: Boolean get() = identity.bound
    val weekdays: List<Boolean> get() = window.weekdays
    val startTimeMs: Long get() = window.startTimeMs
    val startTime: String get() = window.startTime
    val endTimeMs: Long get() = window.endTimeMs
    val endTime: String get() = window.endTime
    val spansMidnight: Boolean get() = window.spansMidnight
}

data class DeviceTimerScheduleIdentity(
    val index: Int,
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val channelKey: String,
    val bound: Boolean
)

data class DeviceTimerScheduleWindow(
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val startTime: String,
    val endTimeMs: Long,
    val endTime: String,
    val spansMidnight: Boolean
)

data class DeviceTimerStatus(
    val scope: DeviceTimerStatusScope,
    val limits: DeviceTimerStatusLimits,
    val authority: DeviceTimerStatusAuthority,
    val channels: List<DeviceTimerChannelStatus>,
    val schedules: List<DeviceTimerScheduleStatus>,
    val runtime: DeviceTimerRuntimeCapabilities
) {
    val supported: Boolean get() = scope.supported
    val channelScoped: Boolean get() = scope.channelScoped
    val schedulesIncluded: Boolean get() = scope.schedulesIncluded
    val selectedChannelKey: String? get() = scope.selectedChannelKey
    val returnedScheduleCount: Int get() = scope.returnedScheduleCount
    val channelCount: Int get() = limits.channelCount
    val scheduleCount: Int get() = limits.scheduleCount
    val maxSchedulesPerChannel: Int get() = limits.maxSchedulesPerChannel
    val maxScheduleCount: Int get() = limits.maxScheduleCount
    val revision: Long get() = authority.revision
    val lockLoop: Boolean get() = authority.lockLoop
    val schema: String get() = authority.schema
    val schemaVersion: Int get() = authority.schemaVersion
    val rootName: String get() = authority.rootName
    val uptimeMs: Long get() = authority.uptimeMs
}

data class DeviceTimerStatusScope(
    val supported: Boolean,
    val channelScoped: Boolean,
    val schedulesIncluded: Boolean,
    val selectedChannelKey: String?,
    val returnedScheduleCount: Int
)

data class DeviceTimerStatusLimits(
    val channelCount: Int,
    val scheduleCount: Int,
    val maxSchedulesPerChannel: Int,
    val maxScheduleCount: Int
)

data class DeviceTimerStatusAuthority(
    val revision: Long,
    val lockLoop: Boolean,
    val schema: String,
    val schemaVersion: Int,
    val rootName: String,
    val uptimeMs: Long
)

data class DeviceTimerStatusGetPayload(
    val channelKey: String? = null
) {
    val normalizedChannelKey: String? = channelKey?.let(::normalizeTimerChannelKey)

    internal fun toJson(): JSONObject = JSONObject().also { data ->
        normalizedChannelKey?.let { data.put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, it) }
    }
}

sealed interface DeviceTimerDisplayNameUpdate {
    data object Omitted : DeviceTimerDisplayNameUpdate
    data object Clear : DeviceTimerDisplayNameUpdate

    data class Value(val displayName: String) : DeviceTimerDisplayNameUpdate {
        val normalizedDisplayName: String = displayName.trimTimerAsciiWhitespace()

        init {
            require(normalizedDisplayName.isNotEmpty()) {
                "Use DeviceTimerDisplayNameUpdate.Clear for an empty display name."
            }
            require(!normalizedDisplayName.hasTimerForbiddenControlBytes()) {
                "displayName must not contain control characters."
            }
            require(
                normalizedDisplayName.toByteArray(Charsets.UTF_8).size <=
                    DeviceTimerRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES
            ) { "displayName exceeds the firmware byte limit." }
        }
    }
}

data class DeviceTimerScheduleConfig(
    val slotId: Int,
    val enabled: Boolean,
    val name: String,
    val weekdays: List<Boolean>,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val spansMidnight: Boolean = endTimeMs < startTimeMs
) {
    val normalizedName: String = name.trimTimerAsciiWhitespace()

    init {
        require(slotId in DeviceTimerRuntimeContract.Limit.SLOT_ID_MINIMUM..
            DeviceTimerRuntimeContract.Limit.SLOT_ID_MAXIMUM) {
            "slotId must be inside the firmware Timer slot range."
        }
        require(normalizedName.isNotEmpty()) { "Timer schedule name must not be blank." }
        require(!normalizedName.hasTimerForbiddenControlBytes()) {
            "Timer schedule name must not contain control characters."
        }
        require(
            normalizedName.toByteArray(Charsets.UTF_8).size <=
                DeviceTimerRuntimeContract.Limit.MAX_SCHEDULE_NAME_BYTES
        ) { "Timer schedule name exceeds the firmware byte limit." }
        require(weekdays.size == TIMER_WEEKDAY_COUNT) {
            "Timer weekdays must contain exactly $TIMER_WEEKDAY_COUNT values."
        }
        require(!enabled || weekdays.any { selected -> selected }) {
            "An enabled Timer schedule requires at least one weekday."
        }
        require(isTimerScheduleBoundary(startTimeMs)) {
            "startTimeMs must be milliseconds from local midnight on a whole-minute boundary."
        }
        require(isTimerScheduleBoundary(endTimeMs)) {
            "endTimeMs must be milliseconds from local midnight on a whole-minute boundary."
        }
        require(startTimeMs != endTimeMs) { "Timer schedule boundaries must differ." }
        require(spansMidnight == (endTimeMs < startTimeMs)) {
            "spansMidnight must equal endTimeMs < startTimeMs."
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.SLOT_ID, slotId)
        .put(DeviceTimerRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceTimerRuntimeContract.Field.NAME, normalizedName)
        .put(DeviceTimerRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
        .put(DeviceTimerRuntimeContract.Field.START_TIME_MS, startTimeMs)
        .put(DeviceTimerRuntimeContract.Field.END_TIME_MS, endTimeMs)
        .put(DeviceTimerRuntimeContract.Field.SPANS_MIDNIGHT, spansMidnight)
}

data class DeviceTimerConfigApplyPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val displayName: DeviceTimerDisplayNameUpdate = DeviceTimerDisplayNameUpdate.Omitted,
    val schedules: List<DeviceTimerScheduleConfig>? = null,
    val save: Boolean = true
) {
    val normalizedChannelKey: String = normalizeTimerChannelKey(channelKey)

    init {
        require(expectedRevision in TIMER_NON_NEGATIVE_LONG..
            DeviceTimerRuntimeContract.Limit.UINT32_MAX) {
            "expectedRevision is outside the firmware unsigned integer range."
        }
        require(displayName != DeviceTimerDisplayNameUpdate.Omitted || schedules != null) {
            "timer.config.apply requires displayName and/or schedules."
        }
        require((schedules?.size ?: 0) <=
            DeviceTimerRuntimeContract.Limit.MAX_SCHEDULES_PER_CHANNEL) {
            "Timer supports at most 8 schedules per channel."
        }
        schedules?.validateTimerScheduleReplacement()
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
        .also { data ->
            when (val update = displayName) {
                DeviceTimerDisplayNameUpdate.Omitted -> Unit
                DeviceTimerDisplayNameUpdate.Clear -> data.put(
                    DeviceTimerRuntimeContract.Field.DISPLAY_NAME,
                    JSONObject.NULL
                )
                is DeviceTimerDisplayNameUpdate.Value -> data.put(
                    DeviceTimerRuntimeContract.Field.DISPLAY_NAME,
                    update.normalizedDisplayName
                )
            }
            schedules?.let { items ->
                data.put(
                    DeviceTimerRuntimeContract.Field.SCHEDULES,
                    JSONArray().also { array -> items.forEach { array.put(it.toJson()) } }
                )
            }
        }
        .also(::requireTimerMutationBudget)
}

data class DeviceTimerChannelSetPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val regime: DeviceTimerRegime,
    val durationMs: Long? = null,
    val save: Boolean = true
) {
    val normalizedChannelKey: String = normalizeTimerChannelKey(channelKey)

    init {
        require(expectedRevision in TIMER_NON_NEGATIVE_LONG..
            DeviceTimerRuntimeContract.Limit.UINT32_MAX) {
            "expectedRevision is outside the firmware unsigned integer range."
        }
        durationMs?.let { duration ->
            require(duration in DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MINIMUM_MS..
                DeviceTimerRuntimeContract.Limit.TEMPORARY_DURATION_MAXIMUM_MS) {
                "Temporary Timer override duration is outside 1..86400000 ms."
            }
            require(regime != DeviceTimerRegime.AUTO) {
                "Temporary Timer overrides support only On or Off."
            }
            require(!save) { "Temporary Timer overrides require save=false." }
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceTimerRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceTimerRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceTimerRuntimeContract.Field.REGIME, regime.wireValue)
        .put(DeviceTimerRuntimeContract.Field.SAVE, save)
        .also { data ->
            durationMs?.let { data.put(DeviceTimerRuntimeContract.Field.DURATION_MS, it) }
        }
        .also(::requireTimerMutationBudget)
}

data class DeviceTimerConfigApplyResult(
    val mutation: DeviceTimerConfigMutationResult,
    val transport: DeviceTimerMutationTransport,
    val application: DeviceTimerConfigApplication,
    val channel: DeviceTimerChannelStatus
) {
    val operation: String get() = mutation.operation
    val changed: Boolean get() = mutation.changed
    val saved: Boolean get() = mutation.saved
    val saveRequested: Boolean get() = mutation.saveRequested
    val revision: Long get() = mutation.revision
    val channelKey: String get() = transport.channelKey
    val runtimeTransport: String get() = transport.runtimeTransport
    val command: String get() = transport.command
    val appliedDisplayName: Boolean get() = application.appliedDisplayName
    val replacedSchedules: Boolean get() = application.replacedSchedules
}

data class DeviceTimerConfigMutationResult(
    val operation: String,
    val changed: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean,
    val revision: Long
)

data class DeviceTimerMutationTransport(
    val channelKey: String,
    val runtimeTransport: String,
    val command: String
)

data class DeviceTimerConfigApplication(
    val appliedDisplayName: Boolean,
    val replacedSchedules: Boolean
)

data class DeviceTimerChannelSetResult(
    val mutation: DeviceTimerChannelMutationResult,
    val target: DeviceTimerChannelMutationTarget,
    val authority: DeviceTimerMutationAuthority,
    val transport: DeviceTimerMutationTransport,
    val channel: DeviceTimerChannelStatus
) {
    val operation: String get() = mutation.operation
    val changed: Boolean get() = mutation.changed
    val persistentChanged: Boolean get() = mutation.persistentChanged
    val temporaryOverrideCancelled: Boolean get() = mutation.temporaryOverrideCancelled
    val saved: Boolean get() = mutation.saved
    val saveRequested: Boolean get() = mutation.saveRequested
    val channelKey: String get() = target.channelKey
    val regime: DeviceTimerRegime get() = target.regime
    val durationMs: Long get() = target.durationMs
    val revision: Long get() = authority.revision
    val runtimeTransport: String get() = transport.runtimeTransport
    val command: String get() = transport.command
}

data class DeviceTimerChannelMutationResult(
    val operation: String,
    val changed: Boolean,
    val persistentChanged: Boolean,
    val temporaryOverrideCancelled: Boolean,
    val saved: Boolean,
    val saveRequested: Boolean
)

data class DeviceTimerChannelMutationTarget(
    val channelKey: String,
    val regime: DeviceTimerRegime,
    val durationMs: Long
)

data class DeviceTimerMutationAuthority(
    val revision: Long
)

data class DeviceTimerStatusChange(
    val event: DeviceTimerStatusChangeEvent,
    val output: DeviceTimerStatusChangeOutput,
    val transition: DeviceTimerChannelTransition,
    val override: DeviceTimerTemporaryOverride
) {
    val sequence: Long get() = event.sequence
    val occurredAtMs: Long get() = event.occurredAtMs
    val operatingState: DeviceTimerOperatingState get() = output.operatingState
    val runtimeReason: DeviceTimerRuntimeReason get() = output.runtimeReason
    val clockReady: Boolean get() = output.clockReady
    val activeSlotId: Int? get() = transition.activeSlotId
    val activeSlotName: String? get() = transition.activeSlotName
    val nextTransitionType: DeviceTimerNextTransitionType get() = transition.nextTransitionType
    val nextTransitionAt: Long? get() = transition.nextTransitionAt
    val temporaryOverrideActive: Boolean get() = override.active
    val temporaryOverrideRemainingMs: Long get() = override.remainingMs
}

data class DeviceTimerStatusChangeEvent(
    val sequence: Long,
    val occurredAtMs: Long
)

data class DeviceTimerStatusChangeOutput(
    val operatingState: DeviceTimerOperatingState,
    val runtimeReason: DeviceTimerRuntimeReason,
    val clockReady: Boolean
)

data class DeviceTimerTemporaryOverride(
    val active: Boolean,
    val remainingMs: Long
)

data class DeviceTimerStatusChangedEvent(
    val schema: String,
    val schemaVersion: Int,
    val channelKey: String,
    val revision: Long,
    val publishedAtMs: Long,
    val change: DeviceTimerStatusChange
)

private fun List<DeviceTimerScheduleConfig>.validateTimerScheduleReplacement() {
    require(map(DeviceTimerScheduleConfig::slotId).distinct().size == size) {
        "Timer slotId must be unique inside one channel replacement."
    }
    for (firstIndex in indices) {
        for (secondIndex in firstIndex + 1 until size) {
            require(!this[firstIndex].overlaps(this[secondIndex])) {
                "Timer schedules must not overlap across the local week."
            }
        }
    }
}

private fun DeviceTimerScheduleConfig.overlaps(other: DeviceTimerScheduleConfig): Boolean =
    weeklyIntervals().any { first ->
        other.weeklyIntervals().any { second -> first.overlaps(second) }
    }

private fun DeviceTimerScheduleConfig.weeklyIntervals(): List<TimerInterval> =
    weekdays.indices
        .filter { day -> weekdays[day] }
        .flatMap { day ->
            val start = day * DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS + startTimeMs
            val end = start + durationMillis()
            listOf(
                TimerInterval(start, end),
                TimerInterval(start - TIMER_WEEK_MILLISECONDS, end - TIMER_WEEK_MILLISECONDS),
                TimerInterval(start + TIMER_WEEK_MILLISECONDS, end + TIMER_WEEK_MILLISECONDS)
            )
        }

private fun DeviceTimerScheduleConfig.durationMillis(): Long =
    if (endTimeMs > startTimeMs) {
        endTimeMs - startTimeMs
    } else {
        DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS - startTimeMs + endTimeMs
    }

private data class TimerInterval(val start: Long, val end: Long) {
    fun overlaps(other: TimerInterval): Boolean = start < other.end && other.start < end
}

private fun requireTimerMutationBudget(data: JSONObject) {
    val decodedBytes = data.toString().toByteArray(Charsets.UTF_8).size
    require(decodedBytes <= DeviceTimerRuntimeContract.Limit.DECODED_DATA_MAXIMUM_BYTES) {
        "Timer mutation exceeds the decoded WebSocket data budget."
    }
    val encodedCharacters = BASE64_BLOCK_CHARACTERS *
        ((decodedBytes + BASE64_PADDING_BYTES) / BASE64_BLOCK_BYTES)
    require(encodedCharacters <=
        DeviceTimerRuntimeContract.Limit.ENCODED_DATA_MAXIMUM_CHARACTERS) {
        "Timer mutation exceeds the encoded WebSocket data budget."
    }
    require(timerJsonKeyCount(data) <= DeviceTimerRuntimeContract.Limit.JSON_KEY_MAXIMUM) {
        "Timer mutation exceeds the WebSocket JSON key budget."
    }
}

private fun timerJsonKeyCount(value: Any): Int = when (value) {
    is JSONObject -> value.keys().asSequence().sumOf { key ->
        1 + timerJsonKeyCount(value.get(key))
    }
    is JSONArray -> (0 until value.length()).sumOf { index -> timerJsonKeyCount(value.get(index)) }
    else -> 0
}

private const val TIMER_WEEK_MILLISECONDS = TIMER_WEEKDAY_COUNT *
    DeviceTimerRuntimeContract.Limit.DAY_MILLISECONDS
private const val BASE64_BLOCK_CHARACTERS = 4
private const val BASE64_PADDING_BYTES = 2
private const val BASE64_BLOCK_BYTES = 3
