package com.aqua.aqualight.data.devices.runtime.modules.dosing.models

import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.normalizeDosingChannelKey
import org.json.JSONArray
import org.json.JSONObject

enum class DeviceDosingCalibrationState(val wireValue: String) {
    IDLE("idle"),
    RUNNING("running"),
    PENDING_VERIFICATION("pendingVerification");

    companion object {
        fun fromWire(value: String): DeviceDosingCalibrationState = entries.single { it.wireValue == value }
    }
}

enum class DeviceDosingProgramMode(val wireValue: String) {
    SINGLE("single"),
    HOURLY_24("hourly24"),
    CUSTOM_PERIODS("customPeriods"),
    TIMER("timer");

    companion object {
        fun fromWire(value: String): DeviceDosingProgramMode = entries.single { it.wireValue == value }
    }
}

enum class DeviceDosingRuntimeReason(val wireValue: String) {
    NONE("none"),
    PROGRAM_DISABLED("programDisabled"),
    MISSING_CALIBRATION("missingCalibration"),
    INVALID_TIME("invalidTime"),
    RESERVOIR_UNAVAILABLE("reservoirUnavailable"),
    ACCOUNTING_UNCERTAIN("accountingUncertain"),
    UNSAFE_AFTER_CALIBRATION("unsafeAfterCalibration"),
    BUSY("busy"),
    INVALID_PROGRAM("invalidProgram");

    companion object {
        fun fromWire(value: String): DeviceDosingRuntimeReason = entries.single { it.wireValue == value }
    }
}

enum class DeviceDosingRunSource(val wireValue: String) {
    NONE("none"),
    SCHEDULED("scheduled"),
    MANUAL("manual"),
    CALIBRATION("calibration"),
    VERIFICATION("verification"),
    PRIME("prime");

    companion object {
        fun fromWire(value: String): DeviceDosingRunSource = entries.single { it.wireValue == value }
    }
}

data class DeviceDosingStatusEnvelope(
    val supported: Boolean,
    val schema: String,
    val schemaVersion: Int,
    val unit: String,
    val channelCount: Int,
    val uptimeMs: Long,
    val bootReady: Boolean,
    val storageHealthy: Boolean,
    val storageIssue: String
)

data class DeviceDosingEffectiveScheduledDose(
    val available: Boolean,
    val minDoseMl: Double?,
    val maxDoseMl: Double?
)

data class DeviceDosingSchedulingMetadata(
    val contract: String,
    val schemaVersion: Int,
    val amountResolutionMl: Double,
    val maxEventsPerChannel: Int,
    val maxCustomPeriodsPerChannel: Int,
    val scheduledDispatchGraceMs: Long,
    val missedDoseRecoveryWindowMs: Long,
    val minPumpRunDurationMs: Long,
    val maxPumpRunDurationMs: Long,
    val maxManualDoseMl: Double,
    val supportsWeekdayRecurrence: Boolean,
    val supportsMissedDoseRecovery: Boolean,
    val supportsChannelReset: Boolean,
    val supportsDailyDeliveredUsage: Boolean,
    val supportedModes: List<DeviceDosingProgramMode>,
    val weekdayOrder: List<String>,
    val effectiveScheduledDose: DeviceDosingEffectiveScheduledDose
)

data class DeviceDosingUsageToday(
    val dateValid: Boolean,
    val localDate: String?,
    val scheduledDeliveredMl: Double,
    val manualDeliveredMl: Double,
    val totalDeliveredMl: Double
)

data class DeviceDosingReservoirStatus(
    val trackingEnabled: Boolean,
    val capacityMl: Double,
    val remainingMl: Double,
    val accountingCertain: Boolean,
    val remainingPercent: Double
)

data class DeviceDosingReservoirSummary(
    val trackingEnabled: Boolean,
    val remainingMl: Double,
    val accountingCertain: Boolean
)

data class DeviceDosingCalibrationStatus(
    val confirmed: Boolean,
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val state: DeviceDosingCalibrationState,
    val durationMs: Long,
    val measuredMl: Double,
    val pendingDoseMsPerMl: Long,
    val verificationDoseStarted: Boolean,
    val verificationDoseComplete: Boolean
)

data class DeviceDosingActiveRunStatus(
    val active: Boolean,
    val source: DeviceDosingRunSource,
    val targetAmountMl: Double,
    val remainingMs: Long
)

data class DeviceDosingRuntimeEventStatus(
    val valid: Boolean,
    val sequence: Long,
    val occurredAtMs: Long,
    val kind: String,
    val reason: String,
    val source: DeviceDosingRunSource
)

data class DeviceDosingChannelHardware(
    val channelType: String,
    val gpio: Int,
    val ledcChannel: Int,
    val resolutionBits: Int,
    val frequencyHz: Int
)

data class DeviceDosingChannelEditable(
    val hardware: Boolean,
    val displayName: Boolean,
    val dosingCalibration: Boolean,
    val reservoir: Boolean
)

sealed interface DeviceDosingProgramConfig

data class DeviceDosingDistributedProgramConfig(
    val dailyDoseMl: Double,
    val startTimeMs: Long
) : DeviceDosingProgramConfig

data class DeviceDosingCustomPeriod(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val doseCount: Int
)

data class DeviceDosingCustomPeriodsProgramConfig(
    val dailyDoseMl: Double,
    val periods: List<DeviceDosingCustomPeriod>
) : DeviceDosingProgramConfig

data class DeviceDosingTimerEvent(
    val timeMs: Long,
    val amountMl: Double
)

data class DeviceDosingTimerProgramConfig(
    val events: List<DeviceDosingTimerEvent>
) : DeviceDosingProgramConfig

data class DeviceDosingProgram(
    val enabled: Boolean,
    val weekdays: List<Boolean>,
    val mode: DeviceDosingProgramMode,
    val missedDoseRecoveryEnabled: Boolean,
    val config: DeviceDosingProgramConfig
) {
    init {
        require(weekdays.size == DeviceDosingRuntimeContract.Limit.WEEKDAY_COUNT)
        if (enabled) require(weekdays.any { it })
        require(
            when (mode) {
                DeviceDosingProgramMode.SINGLE,
                DeviceDosingProgramMode.HOURLY_24 -> config is DeviceDosingDistributedProgramConfig
                DeviceDosingProgramMode.CUSTOM_PERIODS -> config is DeviceDosingCustomPeriodsProgramConfig
                DeviceDosingProgramMode.TIMER -> config is DeviceDosingTimerProgramConfig
            }
        ) { "Dosing program mode/config mismatch." }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.ENABLED, enabled)
        .put(DeviceDosingRuntimeContract.Field.WEEKDAYS, JSONArray(weekdays))
        .put(DeviceDosingRuntimeContract.Field.MODE, mode.wireValue)
        .put(
            DeviceDosingRuntimeContract.Field.MISSED_DOSE_RECOVERY_ENABLED,
            missedDoseRecoveryEnabled
        )
        .put(DeviceDosingRuntimeContract.Field.CONFIG, config.toJson())
}

private fun DeviceDosingProgramConfig.toJson(): JSONObject = when (this) {
    is DeviceDosingDistributedProgramConfig -> JSONObject()
        .put(DeviceDosingRuntimeContract.Field.DAILY_DOSE_ML, dailyDoseMl)
        .put(DeviceDosingRuntimeContract.Field.START_TIME_MS, startTimeMs)
    is DeviceDosingCustomPeriodsProgramConfig -> JSONObject()
        .put(DeviceDosingRuntimeContract.Field.DAILY_DOSE_ML, dailyDoseMl)
        .put(
            DeviceDosingRuntimeContract.Field.PERIODS,
            JSONArray().also { array ->
                periods.forEach { period ->
                    array.put(
                        JSONObject()
                            .put(DeviceDosingRuntimeContract.Field.START_TIME_MS, period.startTimeMs)
                            .put(DeviceDosingRuntimeContract.Field.END_TIME_MS, period.endTimeMs)
                            .put(DeviceDosingRuntimeContract.Field.DOSE_COUNT, period.doseCount)
                    )
                }
            }
        )
    is DeviceDosingTimerProgramConfig -> JSONObject().put(
        DeviceDosingRuntimeContract.Field.EVENTS,
        JSONArray().also { array ->
            events.forEach { event ->
                array.put(
                    JSONObject()
                        .put(DeviceDosingRuntimeContract.Field.TIME_MS, event.timeMs)
                        .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, event.amountMl)
                )
            }
        }
    )
}

data class DeviceDosingGlobalChannelSummary(
    val channelKey: String,
    val effectiveName: String,
    val revision: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: DeviceDosingRuntimeReason,
    val programEnabled: Boolean,
    val programMode: DeviceDosingProgramMode?,
    val deliveryAccountingCertain: Boolean,
    val usageToday: DeviceDosingUsageToday,
    val reservoir: DeviceDosingReservoirSummary,
    val active: Boolean
)

data class DeviceDosingGlobalRuntimeCapabilities(
    val module: String,
    val supportsProgramApply: Boolean,
    val supportsChannelConfig: Boolean,
    val supportsChannelReset: Boolean,
    val supportsPrime: Boolean,
    val supportsManualDose: Boolean,
    val supportsCalibrationWorkflow: Boolean,
    val supportsReservoirRefill: Boolean,
    val supportsChannelScopedStatus: Boolean,
    val displayNameEditable: Boolean
)

data class DeviceDosingResourceMetrics(
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

data class DeviceDosingGlobalStatus(
    val envelope: DeviceDosingStatusEnvelope,
    val scheduling: DeviceDosingSchedulingMetadata,
    val channels: List<DeviceDosingGlobalChannelSummary>,
    val runtime: DeviceDosingGlobalRuntimeCapabilities,
    val resources: DeviceDosingResourceMetrics
)

data class DeviceDosingChannelDetail(
    val channelKey: String,
    val revision: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: DeviceDosingRuntimeReason,
    val program: DeviceDosingProgram?,
    val usageToday: DeviceDosingUsageToday,
    val index: Int,
    val defaultName: String,
    val displayName: String,
    val effectiveName: String,
    val profileManaged: Boolean,
    val deliveryAccountingCertain: Boolean,
    val hardware: DeviceDosingChannelHardware,
    val calibration: DeviceDosingCalibrationStatus,
    val reservoir: DeviceDosingReservoirStatus,
    val activeRun: DeviceDosingActiveRunStatus,
    val lastRuntimeEvent: DeviceDosingRuntimeEventStatus,
    val editable: DeviceDosingChannelEditable
)

data class DeviceDosingChannelStatus(
    val envelope: DeviceDosingStatusEnvelope,
    val scheduling: DeviceDosingSchedulingMetadata,
    val channel: DeviceDosingChannelDetail
)

data class DeviceDosingStatusChange(
    val schema: String,
    val schemaVersion: Int,
    val channelKey: String,
    val revision: Long,
    val storageHealthy: Boolean,
    val change: DeviceDosingRuntimeEventStatus
)

sealed interface DeviceDosingDisplayNameMutation {
    data class Set(val value: String) : DeviceDosingDisplayNameMutation
    data object Clear : DeviceDosingDisplayNameMutation
}

data class DeviceDosingReservoirConfig(
    val trackingEnabled: Boolean,
    val capacityMl: Double? = null
) {
    init {
        if (trackingEnabled) {
            require(capacityMl != null && capacityMl.isFinite() && capacityMl > 0.0)
        } else {
            require(capacityMl == null)
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.TRACKING_ENABLED, trackingEnabled)
        .also { json ->
            if (trackingEnabled) {
                json.put(DeviceDosingRuntimeContract.Field.CAPACITY_ML, requireNotNull(capacityMl))
            }
        }
}

data class DeviceDosingChannelConfigPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val displayName: DeviceDosingDisplayNameMutation? = null,
    val reservoir: DeviceDosingReservoirConfig? = null
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(expectedRevision in 0L..DeviceDosingRuntimeContract.Limit.MAX_UINT32)
        require(displayName != null || reservoir != null) { "Dosing channel config is empty." }
        (displayName as? DeviceDosingDisplayNameMutation.Set)?.let { mutation ->
            val value = mutation.value.trim()
            require(value.isNotEmpty())
            require(value.none(Char::isISOControl))
            require(
                value.toByteArray(Charsets.UTF_8).size <=
                    DeviceDosingRuntimeContract.Limit.MAX_CHANNEL_DISPLAY_NAME_BYTES
            )
        }
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .also { json ->
            when (val mutation = displayName) {
                null -> Unit
                DeviceDosingDisplayNameMutation.Clear ->
                    json.put(DeviceDosingRuntimeContract.Field.DISPLAY_NAME, JSONObject.NULL)
                is DeviceDosingDisplayNameMutation.Set ->
                    json.put(DeviceDosingRuntimeContract.Field.DISPLAY_NAME, mutation.value.trim())
            }
            reservoir?.let { value ->
                json.put(DeviceDosingRuntimeContract.Field.RESERVOIR, value.toJson())
            }
        }
}

data class DeviceDosingProgramApplyPayload(
    val channelKey: String,
    val expectedRevision: Long,
    val program: DeviceDosingProgram
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(expectedRevision in 0L..DeviceDosingRuntimeContract.Limit.MAX_UINT32)
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
        .put(DeviceDosingRuntimeContract.Field.PROGRAM, program.toJson())
}

data class DeviceDosingChannelResetPayload(
    val channelKey: String,
    val expectedRevision: Long
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(expectedRevision in 0L..DeviceDosingRuntimeContract.Limit.MAX_UINT32)
    }

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
        .put(DeviceDosingRuntimeContract.Field.EXPECTED_REVISION, expectedRevision)
}

data class DeviceDosingChannelKeyPayload(val channelKey: String) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    internal fun toJson(): JSONObject = JSONObject()
        .put(DeviceDosingRuntimeContract.Field.CHANNEL_KEY, normalizedChannelKey)
}

data class DeviceDosingCalibrationStartPayload(
    val channelKey: String,
    val durationMs: Long = DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(
            durationMs in DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_DURATION_MS..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_DURATION_MS
        )
    }

    internal fun toJson(): JSONObject = DeviceDosingChannelKeyPayload(normalizedChannelKey).toJson()
        .put(DeviceDosingRuntimeContract.Field.DURATION_MS, durationMs)
}

data class DeviceDosingCalibrationFinishPayload(
    val channelKey: String,
    val measuredMl: Double
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(
            measuredMl.isFinite() && measuredMl in
                DeviceDosingRuntimeContract.Limit.MIN_CALIBRATION_MEASURED_ML..
                DeviceDosingRuntimeContract.Limit.MAX_CALIBRATION_MEASURED_ML
        )
    }

    internal fun toJson(): JSONObject = DeviceDosingChannelKeyPayload(normalizedChannelKey).toJson()
        .put(DeviceDosingRuntimeContract.Field.MEASURED_ML, measuredMl)
}

data class DeviceDosingDoseNowPayload(
    val channelKey: String,
    val amountMl: Double,
    val usePendingCalibration: Boolean = false
) {
    val normalizedChannelKey = normalizeDosingChannelKey(channelKey)

    init {
        require(amountMl.isFinite() && amountMl > 0.0)
    }

    internal fun toJson(): JSONObject = DeviceDosingChannelKeyPayload(normalizedChannelKey).toJson()
        .put(DeviceDosingRuntimeContract.Field.AMOUNT_ML, amountMl)
        .put(DeviceDosingRuntimeContract.Field.USE_PENDING_CALIBRATION, usePendingCalibration)
}

sealed interface DeviceDosingMutationResult {
    val operation: String
    val channelKey: String
    val event: String
    val channel: DeviceDosingChannelDetail
}

data class DeviceDosingChannelConfigApplyResult(
    override val operation: String,
    override val channelKey: String,
    val saved: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingProgramApplyResult(
    override val operation: String,
    override val channelKey: String,
    val saved: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingChannelResetResult(
    override val operation: String,
    override val channelKey: String,
    val saved: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingPumpCommandResult(
    override val operation: String,
    override val channelKey: String,
    val durationMs: Long?,
    val doseMsPerMl: Long?,
    val manualActive: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingDoseNowResult(
    override val operation: String,
    override val channelKey: String,
    val amountMl: Double,
    val durationMs: Long,
    val doseMsPerMl: Long,
    val usePendingCalibration: Boolean,
    val manualActive: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationStartResult(
    override val operation: String,
    override val channelKey: String,
    val durationMs: Long,
    val calibrationState: DeviceDosingCalibrationState,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationFinishResult(
    override val operation: String,
    override val channelKey: String,
    val measuredMl: Double,
    val durationMs: Long,
    val pendingDoseMsPerMl: Long,
    val calibrationState: DeviceDosingCalibrationState,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationConfirmResult(
    override val operation: String,
    override val channelKey: String,
    val revision: Long,
    val doseMsPerMl: Long,
    val lastCalibratedAt: Long,
    val calibrationState: DeviceDosingCalibrationState,
    val saved: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingCalibrationCancelResult(
    override val operation: String,
    override val channelKey: String,
    val discardedPendingCalibration: Boolean,
    val calibrationState: DeviceDosingCalibrationState,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult

data class DeviceDosingReservoirRefillResult(
    override val operation: String,
    override val channelKey: String,
    val reservoirRemainingMlBefore: Double,
    val reservoirRemainingMl: Double,
    val persisted: Boolean,
    override val event: String,
    override val channel: DeviceDosingChannelDetail
) : DeviceDosingMutationResult
