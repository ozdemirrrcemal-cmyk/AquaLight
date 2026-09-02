@file:Suppress("LongParameterList", "TooManyFunctions")

package com.aqua.aqualight.application.devices.dosing

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Firmware-independent application boundary for channel status and non-calibration mutations.
 *
 * The UI deliberately works with stable slot ids and exact microliter amounts. A future v1 data
 * adapter owns channel-key translation, optimistic revisions, wire enums, JSON and invalidation.
 */
interface DeviceDosingChannelOperations {
    /** Latest fully authoritative channel snapshot, if the current connection has one. */
    fun current(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? = null

    fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?>

    fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        flowOf(emptyList())

    suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult

    suspend fun refreshAll(deviceUid: String): Boolean = false

    suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult

    suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult

    suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult

    suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult

    suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult

    suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult

    suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult
}

data class DeviceDosingSchedulingPolicy(
    val amountResolutionMicroliters: Long = 1L,
    val maxEventsPerChannel: Int = 24,
    val maxCustomPeriodsPerChannel: Int = 24,
    val scheduledDispatchGraceMillis: Long = 0L,
    val minimumPumpRunDurationMillis: Long = 1L,
    val maximumPumpRunDurationMillis: Long = 60_000L,
    val maximumManualDoseMicroliters: Long = 1_000_000L,
    val supportsWeekdayRecurrence: Boolean = true,
    val supportsMissedDoseRecovery: Boolean = true,
    val supportsChannelReset: Boolean = true,
    val supportsDailyDeliveredUsage: Boolean = true,
    val supportedModes: Set<DeviceDosingProgramMode> = DeviceDosingProgramMode.entries.toSet(),
    val effectiveScheduledDoseMicroliters: LongRange? = null
) {
    init {
        require(amountResolutionMicroliters > 0L)
        require(maxEventsPerChannel > 0)
        require(maxCustomPeriodsPerChannel > 0)
        require(scheduledDispatchGraceMillis >= 0L)
        require(minimumPumpRunDurationMillis > 0L)
        require(maximumPumpRunDurationMillis >= minimumPumpRunDurationMillis)
        require(maximumManualDoseMicroliters > 0L)
        require(supportedModes.isNotEmpty())
        effectiveScheduledDoseMicroliters?.let { range ->
            require(range.first > 0L && range.last >= range.first)
        }
    }

    fun acceptsAmount(amountMicroliters: Long): Boolean =
        amountMicroliters > 0L && amountMicroliters % amountResolutionMicroliters == 0L

    fun acceptsManualDose(amountMicroliters: Long): Boolean =
        acceptsAmount(amountMicroliters) && amountMicroliters <= maximumManualDoseMicroliters

    fun acceptsScheduledDose(amountMicroliters: Long): Boolean =
        acceptsAmount(amountMicroliters) &&
            effectiveScheduledDoseMicroliters?.contains(amountMicroliters) != false
}

enum class DeviceDosingProgramMode {
    SINGLE,
    HOURLY_24,
    CUSTOM_PERIODS,
    TIMER
}

sealed interface DeviceDosingProgramSchedule {
    val mode: DeviceDosingProgramMode

    data class Single(
        val dailyDoseMicroliters: Long,
        val startTimeMillis: Long
    ) : DeviceDosingProgramSchedule {
        override val mode: DeviceDosingProgramMode = DeviceDosingProgramMode.SINGLE
    }

    data class Hourly24(
        val dailyDoseMicroliters: Long,
        val minuteOfHour: Int
    ) : DeviceDosingProgramSchedule {
        override val mode: DeviceDosingProgramMode = DeviceDosingProgramMode.HOURLY_24
    }

    data class CustomPeriods(
        val dailyDoseMicroliters: Long,
        val periods: List<DeviceDosingCustomPeriodDraft>
    ) : DeviceDosingProgramSchedule {
        override val mode: DeviceDosingProgramMode = DeviceDosingProgramMode.CUSTOM_PERIODS
    }

    data class Timer(
        val doses: List<DeviceDosingTimerDoseDraft>
    ) : DeviceDosingProgramSchedule {
        override val mode: DeviceDosingProgramMode = DeviceDosingProgramMode.TIMER
    }
}

data class DeviceDosingProgram(
    val enabled: Boolean,
    val weekdays: List<Boolean>,
    val schedule: DeviceDosingProgramSchedule,
    val missedDoseRecoveryEnabled: Boolean
) {
    init {
        require(weekdays.size == WEEKDAY_COUNT) {
            "Dosing weekdays must contain Monday through Sunday."
        }
    }

    fun isValidFor(policy: DeviceDosingSchedulingPolicy): Boolean =
        isCompatibleWith(policy) && schedule.isValidFor(policy)

    private fun isCompatibleWith(policy: DeviceDosingSchedulingPolicy): Boolean = listOf(
        schedule.mode in policy.supportedModes,
        !enabled || !policy.supportsWeekdayRecurrence || weekdays.any { it },
        !missedDoseRecoveryEnabled || policy.supportsMissedDoseRecovery
    ).all { compatible -> compatible }
}

private fun DeviceDosingProgramSchedule.isValidFor(
    policy: DeviceDosingSchedulingPolicy
): Boolean = when (this) {
    is DeviceDosingProgramSchedule.Single ->
        policy.acceptsScheduledDose(dailyDoseMicroliters) && isDosingTime(startTimeMillis)
    is DeviceDosingProgramSchedule.Hourly24 ->
        validDistributedAmount(
            totalMicroliters = dailyDoseMicroliters,
            count = HOURLY_DOSE_COUNT,
            policy = policy
        ) && minuteOfHour in 0 until MINUTES_PER_HOUR &&
            policy.maxEventsPerChannel >= HOURLY_DOSE_COUNT
    is DeviceDosingProgramSchedule.CustomPeriods -> isValidFor(policy)
    is DeviceDosingProgramSchedule.Timer -> isValidFor(policy)
}

private fun DeviceDosingProgramSchedule.CustomPeriods.isValidFor(
    policy: DeviceDosingSchedulingPolicy
): Boolean {
    val eventCount = periods.sumOf(DeviceDosingCustomPeriodDraft::doseCount)
    return listOf(
        periods.isNotEmpty(),
        periods.size <= policy.maxCustomPeriodsPerChannel,
        eventCount <= policy.maxEventsPerChannel,
        validDistributedAmount(dailyDoseMicroliters, eventCount, policy),
        validCustomPeriods(periods)
    ).all { valid -> valid }
}

private fun DeviceDosingProgramSchedule.Timer.isValidFor(
    policy: DeviceDosingSchedulingPolicy
): Boolean = listOf(
    doses.isNotEmpty(),
    doses.size <= policy.maxEventsPerChannel,
    doses.all { dose ->
        isDosingTime(dose.startTimeMs) &&
            policy.acceptsScheduledDose(dose.amountMicroliters)
    },
    doses.map(DeviceDosingTimerDoseDraft::startTimeMs).distinct().size == doses.size
).all { valid -> valid }

enum class DeviceDosingOccurrenceState {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    UNCERTAIN
}

/** One compiled firmware occurrence, kept independent from its UI representation. */
data class DeviceDosingOccurrenceProgress(
    val index: Int,
    val eventId: Long,
    val programDayOffset: Int,
    val timeMillis: Long,
    val amountMicroliters: Long,
    val state: DeviceDosingOccurrenceState
) {
    init {
        require(index >= 0)
        require(eventId >= 0L)
        require(timeMillis in 0L until MILLIS_PER_DAY)
        require(amountMicroliters > 0L)
    }
}

enum class DeviceDosingScheduleState {
    ACTIVE,
    NO_SCHEDULE
}

/**
 * Firmware-owned compiled progress projection for the current program day.
 *
 * Wire summary values are retained explicitly so presentation never has to recreate scheduler
 * counts, amount totals, remaining amount or completion percentage. Defaults are only construction
 * conveniences for non-wire callers; the production v1 mapper supplies every authoritative field.
 */
data class DeviceDosingChannelProgress(
    val scheduledAmountMicroliters: Long = 0L,
    val completedAmountMicroliters: Long = 0L,
    val remainingAmountMicroliters: Long =
        (scheduledAmountMicroliters - completedAmountMicroliters).coerceAtLeast(0L),
    val occurrences: List<DeviceDosingOccurrenceProgress> = emptyList(),
    val scheduleState: DeviceDosingScheduleState = if (scheduledAmountMicroliters > 0L) {
        DeviceDosingScheduleState.ACTIVE
    } else {
        DeviceDosingScheduleState.NO_SCHEDULE
    },
    val totalOccurrences: Int = occurrences.size,
    val completedOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.COMPLETED
    },
    val resolvedOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.COMPLETED ||
            occurrence.state == DeviceDosingOccurrenceState.SKIPPED
    },
    val pendingOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.PENDING
    },
    val runningOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.RUNNING
    },
    val skippedOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.SKIPPED
    },
    val uncertainOccurrences: Int = occurrences.count { occurrence ->
        occurrence.state == DeviceDosingOccurrenceState.UNCERTAIN
    },
    val completionPercent: Double = if (scheduledAmountMicroliters > 0L) {
        completedAmountMicroliters.toDouble() / scheduledAmountMicroliters.toDouble() *
            DOSING_PROGRESS_PERCENT_SCALE
    } else {
        0.0
    },
    val executionCurrent: Boolean = false,
    val accountingCertain: Boolean = true,
    /** Firmware-owned active program day. Null means no authoritative projection anchor exists. */
    val programDayDate: LocalDate? = null
) {
    init {
        require(scheduledAmountMicroliters >= 0L)
        require(completedAmountMicroliters in 0L..scheduledAmountMicroliters)
        require(remainingAmountMicroliters in 0L..scheduledAmountMicroliters)
        require(completedAmountMicroliters + remainingAmountMicroliters == scheduledAmountMicroliters)
        require(totalOccurrences >= 0)
        require(completedOccurrences >= 0)
        require(resolvedOccurrences >= 0)
        require(pendingOccurrences >= 0)
        require(runningOccurrences >= 0)
        require(skippedOccurrences >= 0)
        require(uncertainOccurrences >= 0)
        require(totalOccurrences == occurrences.size)
        require(resolvedOccurrences == completedOccurrences + skippedOccurrences)
        require(
            pendingOccurrences + runningOccurrences + completedOccurrences +
                skippedOccurrences + uncertainOccurrences == totalOccurrences
        )
        require(completedOccurrences == occurrences.count { occurrence ->
            occurrence.state == DeviceDosingOccurrenceState.COMPLETED
        })
        require(pendingOccurrences == occurrences.count { occurrence ->
            occurrence.state == DeviceDosingOccurrenceState.PENDING
        })
        require(runningOccurrences == occurrences.count { occurrence ->
            occurrence.state == DeviceDosingOccurrenceState.RUNNING
        })
        require(skippedOccurrences == occurrences.count { occurrence ->
            occurrence.state == DeviceDosingOccurrenceState.SKIPPED
        })
        require(uncertainOccurrences == occurrences.count { occurrence ->
            occurrence.state == DeviceDosingOccurrenceState.UNCERTAIN
        })
        require(
            completionPercent.isFinite() &&
                completionPercent in 0.0..DOSING_PROGRESS_PERCENT_SCALE
        )
        require(occurrences.map(DeviceDosingOccurrenceProgress::index).distinct().size ==
            occurrences.size)
        require(runningOccurrences <= 1)
    }
}

/** Daily usage is distinct from scheduled progress so manual dosing never changes plan progress. */
data class DeviceDosingDailyUsageSnapshot(
    val valid: Boolean = false,
    val scheduledDeliveredMicroliters: Long = 0L,
    val manualDeliveredMicroliters: Long = 0L,
    val totalDeliveredMicroliters: Long = 0L
) {
    init {
        require(scheduledDeliveredMicroliters >= 0L)
        require(manualDeliveredMicroliters >= 0L)
        require(totalDeliveredMicroliters >= 0L)
        require(totalDeliveredMicroliters ==
            scheduledDeliveredMicroliters + manualDeliveredMicroliters)
    }
}

data class DeviceDosingReservoirSnapshot(
    val trackingEnabled: Boolean = false,
    val capacityMicroliters: Long = 0L,
    val remainingMicroliters: Long = 0L,
    val accountingCertain: Boolean = true,
    val lowLevelActive: Boolean = false,
    val lowLevelAlertEnabled: Boolean = false
) {
    init {
        require(capacityMicroliters >= 0L)
        require(remainingMicroliters >= 0L)
        if (trackingEnabled) require(capacityMicroliters > 0L)
    }
}

data class DeviceDosingReservoirSettings(
    val trackingEnabled: Boolean,
    val capacityMicroliters: Long?,
    val lowLevelAlertEnabled: Boolean
) {
    init {
        require(trackingEnabled == (capacityMicroliters != null)) {
            "Reservoir capacity is required only while tracking is enabled."
        }
        capacityMicroliters?.let { capacity ->
            require(DeviceDosingReservoirCapacityPolicy.isSupportedMicroliters(capacity))
        }
    }
}

enum class DeviceDosingRunSource {
    NONE,
    SCHEDULED,
    MANUAL,
    CALIBRATION,
    VERIFICATION,
    PRIME,
    UNKNOWN
}

data class DeviceDosingActiveRun(
    val active: Boolean = false,
    val source: DeviceDosingRunSource = DeviceDosingRunSource.NONE,
    val targetAmountMicroliters: Long = 0L,
    val remainingMillis: Long = 0L
) {
    init {
        require(targetAmountMicroliters >= 0L)
        require(remainingMillis >= 0L)
    }
}

enum class DeviceDosingRuntimeReason {
    NONE,
    PROGRAM_DISABLED,
    MISSING_CALIBRATION,
    INVALID_TIME,
    RESERVOIR_UNAVAILABLE,
    ACCOUNTING_UNCERTAIN,
    UNSAFE_AFTER_CALIBRATION,
    BUSY,
    INVALID_PROGRAM,
    UNKNOWN
}

data class DeviceDosingChannelControls(
    val programEditable: Boolean = false,
    val reservoirEditable: Boolean = false,
    val displayNameEditable: Boolean = false,
    val calibrationEditable: Boolean = false,
    val manualDoseSupported: Boolean = false,
    val stopDoseSupported: Boolean = false,
    val resetSupported: Boolean = false,
    val refillSupported: Boolean = false
)

data class DeviceDosingChannelSnapshot(
    val deviceUid: String,
    val slotId: String,
    val pumpCount: Int,
    val channelNumber: Int,
    /** Firmware-authoritative effective name: default name or the persisted user name. */
    val channelTitle: String,
    val revision: Long,
    val runtimeEnabled: Boolean,
    val runtimeReason: DeviceDosingRuntimeReason,
    val deliveryAccountingCertain: Boolean,
    val calibrated: Boolean,
    val lastCalibratedAtEpochSeconds: Long,
    val scheduling: DeviceDosingSchedulingPolicy,
    val program: DeviceDosingProgram?,
    val progress: DeviceDosingChannelProgress,
    val reservoir: DeviceDosingReservoirSnapshot,
    val activeRun: DeviceDosingActiveRun,
    val controls: DeviceDosingChannelControls,
    val usageToday: DeviceDosingDailyUsageSnapshot = DeviceDosingDailyUsageSnapshot()
) {
    init {
        require(deviceUid.isNotBlank())
        require(slotId.isNotBlank())
        require(pumpCount > 0)
        require(channelNumber in 1..pumpCount)
        require(channelTitle.isNotBlank())
        require(revision >= 0L)
        require(lastCalibratedAtEpochSeconds >= 0L)
        require(program == null || program.isValidFor(scheduling))
    }
}

sealed interface DeviceDosingChannelOperationResult {
    data class Success(
        val snapshot: DeviceDosingChannelSnapshot
    ) : DeviceDosingChannelOperationResult

    data class Rejected(
        val reason: DeviceDosingChannelRejection
    ) : DeviceDosingChannelOperationResult

    data object Unavailable : DeviceDosingChannelOperationResult
    data object Failed : DeviceDosingChannelOperationResult
}

enum class DeviceDosingChannelRejection {
    INVALID_DRAFT,
    NOT_EDITABLE,
    NOT_CALIBRATED,
    BUSY,
    CONFLICT,
    OUTPUT_STOP_UNCONFIRMED,
    UNSAFE,
    UNKNOWN
}

private fun validCustomPeriods(periods: List<DeviceDosingCustomPeriodDraft>): Boolean =
    periods.all { period ->
        isDosingTime(period.startTimeMs) &&
            isDosingTime(period.endTimeMs) &&
            period.endTimeMs > period.startTimeMs &&
            period.doseCount > 0
    } && periods
        .zipWithNext()
        .all { (left, right) -> left.endTimeMs < right.startTimeMs }

private fun isDosingTime(value: Long): Boolean = value in 0L until MILLIS_PER_DAY

private fun validDistributedAmount(
    totalMicroliters: Long,
    count: Int,
    policy: DeviceDosingSchedulingPolicy
): Boolean {
    if (!policy.acceptsAmount(totalMicroliters) || count <= 0) return false
    val totalQuanta = totalMicroliters / policy.amountResolutionMicroliters
    val baseQuanta = totalQuanta / count
    val remainderQuanta = (totalQuanta % count).toInt()
    return (0 until count).all { index ->
        val quanta = baseQuanta + if (index < remainderQuanta) 1L else 0L
        policy.acceptsScheduledDose(quanta * policy.amountResolutionMicroliters)
    }
}

private const val DOSING_PROGRESS_PERCENT_SCALE = 100.0
private const val WEEKDAY_COUNT = 7
private const val HOURLY_DOSE_COUNT = 24
private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_DAY = 86_400_000L
