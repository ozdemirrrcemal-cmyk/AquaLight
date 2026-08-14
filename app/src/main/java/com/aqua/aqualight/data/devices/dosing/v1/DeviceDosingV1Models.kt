package com.aqua.aqualight.data.devices.dosing.v1

/**
 * Raw wire enum value. Known values can be recognized by callers, while a newer firmware value
 * remains available for diagnostics and safe application-layer fallback.
 */
@JvmInline
value class DeviceDosingV1WireValue(val raw: String) {
    init {
        require(raw.isNotEmpty()) { "Wire enum value must not be empty." }
        require(raw.none(Char::isISOControl)) { "Wire enum value contains a control character." }
    }

    fun isKnown(knownValues: Set<String>): Boolean = raw in knownValues
}

object DeviceDosingV1WireValues {
    val PROGRAM_MODES = setOf("none", "single", "hourly24", "customPeriods", "timer")
    val RUNTIME_REASONS = setOf(
        "none",
        "programDisabled",
        "missingCalibration",
        "invalidTime",
        "reservoirUnavailable",
        "accountingUncertain",
        "unsafeAfterCalibration",
        "busy",
        "invalidProgram"
    )
    val CALIBRATION_STATES = setOf("idle", "running", "pendingVerification")
    val RUN_SOURCES = setOf("none", "scheduled", "manual", "calibration", "verification", "prime")
    val EVENT_KINDS = setOf(
        "none",
        "stateChanged",
        "runStarted",
        "runCompleted",
        "scheduledSkipped",
        "fault"
    )
    val EVENT_REASONS = setOf(
        "none",
        "programChanged",
        "channelConfigChanged",
        "channelReset",
        "calibrationChanged",
        "reservoirRefilled",
        "scheduledDispatch",
        "manualCommand",
        "naturalDeadline",
        "missedDoseSkipped",
        "recoveryWindowExpired",
        "reservoirUnavailable",
        "missingCalibration",
        "invalidTime",
        "clockMovedBackward",
        "checkpointWriteFailed",
        "canonicalStorageFailed",
        "rollbackFailed",
        "corruptStorage",
        "storageIoFailure",
        "stateEpochMismatch",
        "hardwareStartFailed",
        "accountingFailed"
    )
    val SCHEDULE_STATES = setOf("active", "noSchedule")
    val OCCURRENCE_STATES = setOf("pending", "running", "completed", "skipped", "uncertain")
}

data class DeviceDosingV1Envelope(
    val supported: Boolean,
    val schema: String,
    val schemaVersion: Long,
    val unit: String,
    val channelCount: Int,
    val uptimeMillis: Long,
    val bootReady: Boolean,
    val storageHealthy: Boolean,
    val storageIssue: String
)

data class DeviceDosingV1EffectiveScheduledDose(
    val available: Boolean,
    val minimumMilliliters: Double?,
    val maximumMilliliters: Double?
)

data class DeviceDosingV1SchedulingMetadata(
    val contract: String,
    val schemaVersion: Long,
    val amountResolutionMilliliters: Double,
    val maxEventsPerChannel: Int,
    val maxCustomPeriodsPerChannel: Int,
    val scheduledDispatchGraceMillis: Long,
    val missedDoseRecoveryProgramDay: Boolean,
    val minimumPumpRunDurationMillis: Long,
    val maximumPumpRunDurationMillis: Long,
    val maximumManualDoseMilliliters: Double,
    val supportsWeekdayRecurrence: Boolean,
    val supportsMissedDoseRecovery: Boolean,
    val supportsChannelReset: Boolean,
    val supportsDailyDeliveredUsage: Boolean,
    val supportedModes: List<DeviceDosingV1WireValue>,
    val weekdayOrder: List<String>,
    val effectiveScheduledDose: DeviceDosingV1EffectiveScheduledDose
)

data class DeviceDosingV1DailyUsage(
    val dateValid: Boolean,
    val localDate: String?,
    val scheduledDeliveredMilliliters: Double,
    val manualDeliveredMilliliters: Double,
    val totalDeliveredMilliliters: Double
)

sealed interface DeviceDosingV1ProgramSnapshotConfig {
    data class Single(
        val dailyDoseMilliliters: Double,
        val startTimeMillis: Long
    ) : DeviceDosingV1ProgramSnapshotConfig

    data class Hourly24(
        val dailyDoseMilliliters: Double,
        val startTimeMillis: Long
    ) : DeviceDosingV1ProgramSnapshotConfig

    data class CustomPeriod(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val doseCount: Int
    )

    data class CustomPeriods(
        val dailyDoseMilliliters: Double,
        val periods: List<CustomPeriod>
    ) : DeviceDosingV1ProgramSnapshotConfig

    data class TimerEvent(
        val timeMillis: Long,
        val amountMilliliters: Double
    )

    data class Timer(
        val events: List<TimerEvent>
    ) : DeviceDosingV1ProgramSnapshotConfig

    data class Unknown(
        val mode: DeviceDosingV1WireValue,
        val rawJson: String
    ) : DeviceDosingV1ProgramSnapshotConfig
}

data class DeviceDosingV1ProgramSnapshot(
    val enabled: Boolean,
    val weekdays: List<Boolean>,
    val mode: DeviceDosingV1WireValue,
    val missedDoseRecoveryEnabled: Boolean,
    val config: DeviceDosingV1ProgramSnapshotConfig
)

data class DeviceDosingV1GlobalReservoir(
    val trackingEnabled: Boolean,
    val remainingMilliliters: Double,
    val accountingCertain: Boolean,
    val lowLevelActive: Boolean
)

data class DeviceDosingV1GlobalChannel(
    val channelKey: DeviceDosingV1ChannelKey,
    val effectiveName: String,
    val revision: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: DeviceDosingV1WireValue,
    val programEnabled: Boolean,
    val programMode: DeviceDosingV1WireValue,
    val deliveryAccountingCertain: Boolean,
    val usageToday: DeviceDosingV1DailyUsage,
    val reservoir: DeviceDosingV1GlobalReservoir,
    val active: Boolean
)

data class DeviceDosingV1RuntimeCapabilities(
    val module: String,
    val supportsProgramApply: Boolean,
    val supportsChannelConfig: Boolean,
    val supportsChannelReset: Boolean,
    val supportsPrime: Boolean,
    val supportsManualDose: Boolean,
    val supportsCalibrationWorkflow: Boolean,
    val supportsReservoirRefill: Boolean,
    val supportsChannelScopedStatus: Boolean
)

data class DeviceDosingV1Resources(
    val freeHeapBytes: Long,
    val minimumFreeHeapBytes: Long,
    val largestFreeBlockBytes: Long,
    val taskStackHighWaterBytes: Long,
    val checkpointWritesThisBoot: Long,
    val canonicalConfigBytes: Long,
    val programServiceBytes: Long,
    val runtimeSnapshotBytes: Long,
    val statusSnapshotBytes: Long
)

data class DeviceDosingV1GlobalStatus(
    val envelope: DeviceDosingV1Envelope,
    val scheduling: DeviceDosingV1SchedulingMetadata,
    val channels: List<DeviceDosingV1GlobalChannel>,
    val runtime: DeviceDosingV1RuntimeCapabilities,
    val resources: DeviceDosingV1Resources
)

data class DeviceDosingV1Hardware(
    val channelType: String,
    val gpio: Int,
    val ledcChannel: Int,
    val resolutionBits: Int,
    val frequencyHertz: Int
)

data class DeviceDosingV1Calibration(
    val confirmed: Boolean,
    val doseMillisPerMilliliter: Double,
    val lastCalibratedAt: Long,
    val state: DeviceDosingV1WireValue,
    val durationMillis: Long,
    val measuredMilliliters: Double,
    val pendingDoseMillisPerMilliliter: Double,
    val verificationDoseStarted: Boolean,
    val verificationDoseComplete: Boolean
)

data class DeviceDosingV1Reservoir(
    val trackingEnabled: Boolean,
    val capacityMilliliters: Double,
    val remainingMilliliters: Double,
    val accountingCertain: Boolean,
    val lowLevelActive: Boolean,
    val remainingPercent: Double
)

data class DeviceDosingV1ActiveRun(
    val active: Boolean,
    val source: DeviceDosingV1WireValue,
    val targetAmountMilliliters: Double,
    val remainingMillis: Long
)

data class DeviceDosingV1RuntimeEventSnapshot(
    val valid: Boolean,
    val sequence: Long,
    val occurredAtMillis: Long,
    val kind: DeviceDosingV1WireValue,
    val reason: DeviceDosingV1WireValue,
    val source: DeviceDosingV1WireValue
)

data class DeviceDosingV1Editable(
    val hardware: Boolean,
    val displayName: Boolean,
    val dosingCalibration: Boolean,
    val reservoir: Boolean
)

data class DeviceDosingV1ChannelDetail(
    val channelKey: DeviceDosingV1ChannelKey,
    val revision: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: DeviceDosingV1WireValue,
    val program: DeviceDosingV1ProgramSnapshot?,
    val usageToday: DeviceDosingV1DailyUsage,
    val index: Int,
    val defaultName: String,
    val displayName: String?,
    val effectiveName: String,
    val profileManaged: Boolean,
    val deliveryAccountingCertain: Boolean,
    val hardware: DeviceDosingV1Hardware,
    val calibration: DeviceDosingV1Calibration,
    val reservoir: DeviceDosingV1Reservoir,
    val activeRun: DeviceDosingV1ActiveRun,
    val lastRuntimeEvent: DeviceDosingV1RuntimeEventSnapshot,
    val editable: DeviceDosingV1Editable
)

data class DeviceDosingV1ChannelStatus(
    val envelope: DeviceDosingV1Envelope,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1ProgressSummary(
    val scheduleState: DeviceDosingV1WireValue,
    val total: Int,
    val completed: Int,
    val resolved: Int,
    val pending: Int,
    val running: Int,
    val skipped: Int,
    val uncertain: Int,
    val totalAmountMilliliters: Double,
    val completedAmountMilliliters: Double,
    val remainingAmountMilliliters: Double,
    val completionPercent: Double,
    val executionCurrent: Boolean,
    val programDayDate: String?
)

data class DeviceDosingV1Occurrence(
    val index: Int,
    val eventId: Long,
    val programDayOffset: Int,
    val timeMillis: Long,
    val amountMilliliters: Double,
    val status: DeviceDosingV1WireValue
)

data class DeviceDosingV1ProgressStatus(
    val envelope: DeviceDosingV1Envelope,
    val channelKey: DeviceDosingV1ChannelKey,
    val revision: Long,
    val programEnabled: Boolean,
    val programMode: DeviceDosingV1WireValue,
    val progress: DeviceDosingV1ProgressSummary,
    val occurrences: List<DeviceDosingV1Occurrence>
)

data class DeviceDosingV1SavedMutationResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val saved: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1PrimeStartResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val durationMillis: Long,
    val doseMillisPerMilliliter: Double,
    val manualActive: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1SimpleStopResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val manualActive: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1DoseNowResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val amountMilliliters: Double,
    val durationMillis: Long,
    val doseMillisPerMilliliter: Double,
    val usePendingCalibration: Boolean,
    val manualActive: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1CalibrationStartResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val durationMillis: Long,
    val calibrationState: DeviceDosingV1WireValue,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1CalibrationFinishResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val measuredMilliliters: Double,
    val durationMillis: Long,
    val pendingDoseMillisPerMilliliter: Double,
    val calibrationState: DeviceDosingV1WireValue,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1CalibrationConfirmResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val revision: Long,
    val doseMillisPerMilliliter: Double,
    val lastCalibratedAt: Long,
    val calibrationState: DeviceDosingV1WireValue,
    val saved: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1CalibrationCancelResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val discardedPendingCalibration: Boolean,
    val calibrationState: DeviceDosingV1WireValue,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1ReservoirRefillResult(
    val operation: String,
    val channelKey: DeviceDosingV1ChannelKey,
    val reservoirRemainingMillilitersBefore: Double,
    val reservoirRemainingMilliliters: Double,
    val persisted: Boolean,
    val event: String,
    val channel: DeviceDosingV1ChannelDetail
)

data class DeviceDosingV1DirectEvent(
    val schema: String,
    val schemaVersion: Long,
    val channelKey: DeviceDosingV1ChannelKey,
    val revision: Long,
    val storageHealthy: Boolean,
    val change: DeviceDosingV1RuntimeEventSnapshot
)

/**
 * An event is never a complete status. It only tells the future binding layer what must be read.
 */
data class DeviceDosingV1Invalidation(
    val channelKey: DeviceDosingV1ChannelKey,
    val revisionHint: Long?,
    val refreshGlobal: Boolean = true,
    val refreshChannel: Boolean = true,
    val refreshProgress: Boolean = true
)
