package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftConfig
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramTimerEventDraft
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.DosingWeekday
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom.DeviceDosingCustomPeriod
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer.DeviceDosingTimerDose
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation-state owner backed by the firmware-authoritative per-channel Dosing program. */
internal class DeviceDosingPlanViewModel(
    private val operations: DeviceDosingChannelOperations
) : ViewModel() {
    private val mutableDraft = MutableStateFlow(DosingPlanDraft())
    val draft: StateFlow<DosingPlanDraft> = mutableDraft.asStateFlow()

    private val mutableSaveEnabled = MutableStateFlow(false)
    val saveEnabled: StateFlow<Boolean> = mutableSaveEnabled.asStateFlow()

    private val mutableSaving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = mutableSaving.asStateFlow()

    private var bound: BoundChannel? = null
    private var snapshot: DeviceDosingChannelSnapshot? = null
    private var dirty = false
    private var observeJob: Job? = null

    fun bind(deviceUid: String, slotId: String, restoredDraft: DosingPlanDraft?) {
        val next = BoundChannel(deviceUid.trim(), slotId.trim())
        require(next.deviceUid.isNotEmpty() && next.slotId.isNotEmpty())
        if (bound == next) return
        bound = next
        dirty = restoredDraft != null
        restoredDraft?.let { mutableDraft.value = it }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            operations.observe(next.deviceUid, next.slotId).collect { latest ->
                if (latest == null) return@collect
                snapshot = latest
                if (!dirty) {
                    latest.program?.toPlanDraft()?.let { mutableDraft.value = it }
                }
                updateSaveEnabled()
            }
        }
        viewModelScope.launch {
            when (val result = operations.refresh(next.deviceUid, next.slotId)) {
                is DeviceDosingChannelOperationResult.Success -> {
                    snapshot = result.snapshot
                    if (!dirty) {
                        result.snapshot.program?.toPlanDraft()?.let { mutableDraft.value = it }
                    }
                    updateSaveEnabled()
                }
                DeviceDosingChannelOperationResult.Unavailable,
                DeviceDosingChannelOperationResult.Failed -> mutableSaveEnabled.value = false
            }
        }
    }

    fun currentDraft(): DosingPlanDraft = mutableDraft.value

    fun currentMaxEventsPerChannel(): Int = snapshot?.scheduling?.maxEventsPerChannel ?: 0

    fun currentMaxCustomPeriodsPerChannel(): Int =
        snapshot?.scheduling?.maxCustomPeriodsPerChannel ?: 0

    suspend fun save(): Boolean {
        val target = bound ?: return false
        val currentSnapshot = snapshot ?: return false
        val program = currentDraft().toProgramDraft(currentSnapshot) ?: return false
        if (mutableSaving.value) return false
        mutableSaving.value = true
        mutableSaveEnabled.value = false
        return try {
            when (val result = operations.saveProgram(target.deviceUid, target.slotId, program)) {
                is DeviceDosingChannelOperationResult.Success -> {
                    snapshot = result.snapshot
                    dirty = false
                    result.snapshot.program?.toPlanDraft()?.let { mutableDraft.value = it }
                    true
                }
                DeviceDosingChannelOperationResult.Unavailable,
                DeviceDosingChannelOperationResult.Failed -> false
            }
        } finally {
            mutableSaving.value = false
            updateSaveEnabled()
        }
    }

    fun setDailyDoseMicroliters(microliters: Long) {
        if (microliters <= 0L) return
        update { state -> state.copy(distributedDailyDoseMicroliters = microliters) }
    }

    fun applyScheduleUpdate(scheduleUpdate: DosingPlanScheduleUpdate) = update { state ->
        when (scheduleUpdate) {
            is DosingPlanScheduleUpdate.Single -> state.copy(
                singleDoseStartTimeMs = scheduleUpdate.startTimeMs,
                selectedScheduleMode = DosingPlanScheduleMode.SINGLE
            )
            is DosingPlanScheduleUpdate.Hourly -> state.copy(
                hourlyStartTimeMs = scheduleUpdate.startTimeMs,
                selectedScheduleMode = DosingPlanScheduleMode.HOURLY
            )
            is DosingPlanScheduleUpdate.Custom -> state.copy(
                customPeriods = scheduleUpdate.periods,
                selectedScheduleMode = DosingPlanScheduleMode.CUSTOM
            )
            is DosingPlanScheduleUpdate.Timer -> state.copy(
                timerDoses = scheduleUpdate.doses,
                selectedScheduleMode = DosingPlanScheduleMode.TIMER
            )
        }
    }

    fun setScheduleEnabled(enabled: Boolean) = update { state ->
        state.copy(scheduleEnabled = enabled)
    }

    fun selectEveryDay() = update { state ->
        state.copy(recurrenceState = state.recurrenceState.selectEveryDay())
    }

    fun setWeekdaySelected(weekday: DosingWeekday, selected: Boolean) = update { state ->
        state.copy(recurrenceState = state.recurrenceState.withDaySelection(weekday, selected))
    }

    private inline fun update(transform: (DosingPlanDraft) -> DosingPlanDraft) {
        dirty = true
        mutableDraft.value = transform(mutableDraft.value)
        updateSaveEnabled()
    }

    private fun updateSaveEnabled() {
        mutableSaveEnabled.value = !mutableSaving.value &&
            snapshot?.let { currentDraft().toProgramDraft(it) != null } == true
    }

    private fun DosingPlanDraft.toProgramDraft(
        current: DeviceDosingChannelSnapshot
    ): DeviceDosingProgramDraft? {
        if (scheduleEnabled && !current.calibrated) return null
        val weekdays = recurrenceState.toWeekdayFlags().toList()
        if (scheduleEnabled && weekdays.none { it }) return null
        val config = when (selectedScheduleMode) {
            DosingPlanScheduleMode.SINGLE -> distributedConfig(
                distributedDailyDoseMicroliters,
                singleDoseStartTimeMs,
                current
            )
            DosingPlanScheduleMode.HOURLY -> distributedConfig(
                distributedDailyDoseMicroliters,
                hourlyStartTimeMs,
                current
            )
            DosingPlanScheduleMode.CUSTOM -> customConfig(current)
            DosingPlanScheduleMode.TIMER -> timerConfig(current)
        } ?: return null
        return DeviceDosingProgramDraft(
            enabled = scheduleEnabled,
            weekdays = weekdays,
            mode = selectedScheduleMode.toApplicationMode(),
            missedDoseRecoveryEnabled = current.program?.missedDoseRecoveryEnabled ?: false,
            config = config
        )
    }

    private fun DosingPlanDraft.distributedConfig(
        microliters: Long,
        startTimeMs: Long,
        current: DeviceDosingChannelSnapshot
    ): DeviceDosingProgramDraftConfig.Distributed? {
        val amount = microliters.toMl()
        if (!amountAllowed(amount, current)) return null
        if (startTimeMs !in 0L until MILLIS_PER_DAY) return null
        return DeviceDosingProgramDraftConfig.Distributed(amount, startTimeMs)
    }

    private fun DosingPlanDraft.customConfig(
        current: DeviceDosingChannelSnapshot
    ): DeviceDosingProgramDraftConfig.CustomPeriods? {
        val amount = distributedDailyDoseMicroliters.toMl()
        val limits = current.scheduling
        if (!amountAllowed(amount, current)) return null
        if (customPeriods.isEmpty() || customPeriods.size > limits.maxCustomPeriodsPerChannel) return null
        if (customPeriods.sumOf { it.doseCount } > limits.maxEventsPerChannel) return null
        val normalized = customPeriods.sortedBy { it.startTimeMs }
        normalized.forEach { period ->
            if (
                period.startTimeMs !in 0L until MILLIS_PER_DAY ||
                period.endTimeMs !in 0L until MILLIS_PER_DAY ||
                period.startTimeMs >= period.endTimeMs ||
                period.doseCount <= 0
            ) return null
        }
        normalized.zipWithNext().forEach { (first, second) ->
            if (first.endTimeMs >= second.startTimeMs) return null
        }
        return DeviceDosingProgramDraftConfig.CustomPeriods(
            dailyDoseMl = amount,
            periods = normalized.map { period ->
                DeviceDosingProgramCustomPeriodDraft(
                    period.startTimeMs,
                    period.endTimeMs,
                    period.doseCount
                )
            }
        )
    }

    private fun DosingPlanDraft.timerConfig(
        current: DeviceDosingChannelSnapshot
    ): DeviceDosingProgramDraftConfig.Timer? {
        val limits = current.scheduling
        if (timerDoses.isEmpty() || timerDoses.size > limits.maxEventsPerChannel) return null
        val normalized = timerDoses.sortedBy { it.startTimeMs }
        if (normalized.map { it.startTimeMs }.distinct().size != normalized.size) return null
        val events = normalized.map { dose ->
            val amount = dose.amountMicroliters.toMl()
            if (dose.startTimeMs !in 0L until MILLIS_PER_DAY || !amountAllowed(amount, current)) {
                return null
            }
            DeviceDosingProgramTimerEventDraft(dose.startTimeMs, amount)
        }
        return DeviceDosingProgramDraftConfig.Timer(events)
    }

    private fun amountAllowed(amountMl: Double, current: DeviceDosingChannelSnapshot): Boolean {
        if (!amountMl.isFinite() || amountMl <= 0.0) return false
        val limits = current.scheduling
        val resolution = limits.amountResolutionMl
        if (!resolution.isFinite() || resolution <= 0.0) return false
        val normalized = BigDecimal.valueOf(amountMl)
            .divide(BigDecimal.valueOf(resolution), 9, RoundingMode.HALF_UP)
        if (normalized.stripTrailingZeros().scale() > 0) return false
        limits.effectiveScheduledDoseMinMl?.let { if (amountMl + EPSILON < it) return false }
        limits.effectiveScheduledDoseMaxMl?.let { if (amountMl > it + EPSILON) return false }
        return true
    }

    private fun Long.toMl(): Double = toDouble() / MICROLITERS_PER_MILLILITER

    private fun com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgramSnapshot.toPlanDraft(): DosingPlanDraft {
        val mode = mode.toUiMode()
        val base = DosingPlanDraft(
            selectedScheduleMode = mode,
            scheduleEnabled = enabled,
            recurrenceState = DosingPlanRecurrenceState.fromWeekdayFlags(weekdays.toBooleanArray())
                ?: DosingPlanRecurrenceState()
        )
        return when (val value = config) {
            is DeviceDosingProgramDraftConfig.Distributed -> base.copy(
                distributedDailyDoseMicroliters = value.dailyDoseMl.toMicroliters(),
                singleDoseStartTimeMs = if (mode == DosingPlanScheduleMode.SINGLE) value.startTimeMs else 0L,
                hourlyStartTimeMs = if (mode == DosingPlanScheduleMode.HOURLY) value.startTimeMs else 0L
            )
            is DeviceDosingProgramDraftConfig.CustomPeriods -> base.copy(
                distributedDailyDoseMicroliters = value.dailyDoseMl.toMicroliters(),
                customPeriods = value.periods.map { period ->
                    DeviceDosingCustomPeriod(period.startTimeMs, period.endTimeMs, period.doseCount)
                }
            )
            is DeviceDosingProgramDraftConfig.Timer -> base.copy(
                timerDoses = value.events.map { event ->
                    DeviceDosingTimerDose(event.timeMs, event.amountMl.toMicroliters())
                }
            )
        }
    }

    private fun Double.toMicroliters(): Long = BigDecimal.valueOf(this)
        .multiply(BigDecimal.valueOf(MICROLITERS_PER_MILLILITER.toLong()))
        .setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact()

    private fun DosingPlanScheduleMode.toApplicationMode() = when (this) {
        DosingPlanScheduleMode.SINGLE -> DeviceDosingProgramDraftMode.SINGLE
        DosingPlanScheduleMode.HOURLY -> DeviceDosingProgramDraftMode.HOURLY_24
        DosingPlanScheduleMode.CUSTOM -> DeviceDosingProgramDraftMode.CUSTOM_PERIODS
        DosingPlanScheduleMode.TIMER -> DeviceDosingProgramDraftMode.TIMER
    }

    private fun DeviceDosingProgramDraftMode.toUiMode() = when (this) {
        DeviceDosingProgramDraftMode.SINGLE -> DosingPlanScheduleMode.SINGLE
        DeviceDosingProgramDraftMode.HOURLY_24 -> DosingPlanScheduleMode.HOURLY
        DeviceDosingProgramDraftMode.CUSTOM_PERIODS -> DosingPlanScheduleMode.CUSTOM
        DeviceDosingProgramDraftMode.TIMER -> DosingPlanScheduleMode.TIMER
    }

    private data class BoundChannel(val deviceUid: String, val slotId: String)

    private companion object {
        const val MICROLITERS_PER_MILLILITER = 1_000.0
        const val MILLIS_PER_DAY = 86_400_000L
        const val EPSILON = 0.000_001
    }
}
