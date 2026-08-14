@file:Suppress(
    "CyclomaticComplexMethod",
    "LargeClass",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions"
)

package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingDailyUsageSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import com.aqua.aqualight.application.devices.dosing.DeviceDosingTimerDoseDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Debug-only mutable Dosing state used by installable test devices.
 *
 * It never talks to a physical runtime and never changes the production Dosing boundary. Dose Pro 4
 * showcases all four commercial program modes. Dose Pro 2 retains an uncalibrated channel so the
 * calibration destination remains directly testable.
 */
internal class DebugFixtureDosingStateStore(
    fixtures: DebugDeviceFixtureCatalog
) {
    private val states = linkedMapOf<String, MutableStateFlow<DeviceDosingCalibrationSnapshot>>()
    private val channelStates = linkedMapOf<String, MutableStateFlow<DeviceDosingChannelSnapshot>>()
    private val channelKeysByDevice = linkedMapOf<String, MutableList<String>>()
    private val defaultTitles = linkedMapOf<String, String>()

    init {
        fixtures.snapshots.forEach { snapshot ->
            val root = fixtures.rootSnapshot(snapshot.deviceUid.value) ?: return@forEach
            root.channelSlots.dosingChannels.forEach { slot ->
                val pumpCount = root.channelSlots.dosingChannels.size
                val calibrated = pumpCount == DOSING_PRO_4_CHANNEL_COUNT ||
                    slot.index.position % 2 == 0
                val calibrationState = DeviceDosingCalibrationSnapshot(
                    deviceUid = root.deviceUid,
                    slotId = slot.id.value,
                    pumpCount = root.channelSlots.dosingChannels.size,
                    channelNumber = slot.index.position,
                    channelTitle = slot.defaultDisplayName,
                    deviceUptimeMs = FIXTURE_UPTIME_MS,
                    calibrated = calibrated,
                    lastCalibratedAt = if (calibrated) currentEpochSeconds() else 0L,
                    sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                    startedAtUptimeMs = 0L,
                    durationMs = 0L,
                    measuredMl = 0.0,
                    pendingDoseMsPerMl = 0L,
                    verificationDoseStarted = false,
                    verificationDoseComplete = false,
                    verificationDoseRemainingMs = 0L,
                    manualActive = false
                )
                val stateKey = key(root.deviceUid, slot.id.value)
                val program = initialProgram(slot.index.position, pumpCount, calibrated)
                val activeRun = initialActiveRun(slot.index.position, pumpCount, calibrated)
                val progress = initialProgress(
                    program = program,
                    channelNumber = slot.index.position,
                    pumpCount = pumpCount,
                    active = activeRun.active
                )
                val usageToday = initialUsage(
                    progress = progress,
                    channelNumber = slot.index.position,
                    pumpCount = pumpCount
                )
                val channelState = DeviceDosingChannelSnapshot(
                    deviceUid = root.deviceUid,
                    slotId = slot.id.value,
                    pumpCount = root.channelSlots.dosingChannels.size,
                    channelNumber = slot.index.position,
                    channelTitle = slot.defaultDisplayName,
                    revision = slot.index.position.toLong(),
                    runtimeEnabled = false,
                    runtimeReason = DeviceDosingRuntimeReason.PROGRAM_DISABLED,
                    deliveryAccountingCertain = true,
                    calibrated = calibrated,
                    lastCalibratedAtEpochSeconds = calibrationState.lastCalibratedAt,
                    scheduling = FIXTURE_SCHEDULING_POLICY,
                    program = program,
                    progress = progress,
                    reservoir = initialReservoir(
                        channelNumber = slot.index.position,
                        pumpCount = pumpCount,
                        calibrated = calibrated
                    ),
                    activeRun = activeRun,
                    controls = DeviceDosingChannelControls(
                        programEditable = true,
                        reservoirEditable = true,
                        displayNameEditable = slot.displayNameEditable,
                        calibrationEditable = true,
                        manualDoseSupported = true,
                        stopDoseSupported = true,
                        resetSupported = true,
                        refillSupported = true
                    ),
                    usageToday = usageToday
                ).withDerivedRuntime()
                states[stateKey] = MutableStateFlow(calibrationState)
                channelStates[stateKey] = MutableStateFlow(channelState)
                defaultTitles[stateKey] = slot.defaultDisplayName
                channelKeysByDevice.getOrPut(root.deviceUid) { mutableListOf() }.add(stateKey)
            }
        }
    }

    fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingCalibrationSnapshot?> =
        states[key(deviceUid, slotId)]?.asStateFlow() ?: flowOf(null)

    fun current(deviceUid: String, slotId: String): DeviceDosingCalibrationSnapshot? =
        states[key(deviceUid, slotId)]?.value

    fun observeChannel(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = channelStates[key(deviceUid, slotId)]
        ?.asStateFlow()
        ?.map<DeviceDosingChannelSnapshot, DeviceDosingChannelSnapshot?> { snapshot -> snapshot }
        ?: flowOf(null)

    fun observeChannels(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> {
        val flows = channelKeysByDevice[deviceUid.trim()]
            .orEmpty()
            .mapNotNull(channelStates::get)
        return if (flows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(flows) { snapshots -> snapshots.toList() }
        }
    }

    fun currentChannel(deviceUid: String, slotId: String): DeviceDosingChannelSnapshot? =
        channelStates[key(deviceUid, slotId)]?.value

    fun isCalibrated(deviceUid: String, slotId: String): Boolean =
        current(deviceUid, slotId)?.calibrated == true

    fun refreshChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = currentChannel(deviceUid, slotId).toChannelResult()

    fun refreshChannels(deviceUid: String): Boolean =
        channelKeysByDevice[deviceUid.trim()].orEmpty().isNotEmpty()

    fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.controls.programEditable) return rejectedNotEditable()
        if (!current.calibrated) return rejectedNotCalibrated()
        if (!program.isValidFor(current.scheduling)) return rejectedInvalidDraft()

        return updateChannel(deviceUid, slotId) { state ->
            state.copy(
                revision = state.revision + 1L,
                program = program,
                progress = progressFor(program),
                usageToday = state.usageToday.copy(
                    scheduledDeliveredMicroliters = 0L,
                    totalDeliveredMicroliters = state.usageToday.manualDeliveredMicroliters
                )
            )
        }.toChannelResult()
    }

    fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        val program = current.program ?: return rejectedInvalidDraft()
        if (!current.controls.programEditable) return rejectedNotEditable()
        val updatedProgram = program.copy(missedDoseRecoveryEnabled = enabled)
        if (!updatedProgram.isValidFor(current.scheduling)) return rejectedInvalidDraft()
        return updateChannel(deviceUid, slotId) { state ->
            state.copy(
                revision = state.revision + 1L,
                program = updatedProgram
            )
        }.toChannelResult()
    }

    fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.controls.reservoirEditable) return rejectedNotEditable()
        val capacity = settings.capacityMicroliters
        if (capacity != null && !current.scheduling.acceptsAmount(capacity)) {
            return rejectedInvalidDraft()
        }

        return updateChannel(deviceUid, slotId) { state ->
            val oldReservoir = state.reservoir
            val newCapacity = capacity ?: 0L
            val remaining = when {
                !settings.trackingEnabled -> 0L
                !oldReservoir.trackingEnabled -> newCapacity
                else -> oldReservoir.remainingMicroliters.coerceAtMost(newCapacity)
            }
            state.copy(
                revision = state.revision + 1L,
                reservoir = DeviceDosingReservoirSnapshot(
                    trackingEnabled = settings.trackingEnabled,
                    capacityMicroliters = newCapacity,
                    remainingMicroliters = remaining,
                    accountingCertain = true,
                    lowLevelActive = settings.trackingEnabled &&
                        remaining * LOW_LEVEL_DIVISOR <= newCapacity,
                    lowLevelAlertEnabled = settings.lowLevelAlertEnabled
                )
            )
        }.toChannelResult()
    }

    fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.controls.refillSupported || !current.reservoir.trackingEnabled) {
            return rejectedNotEditable()
        }
        return updateChannel(deviceUid, slotId) { state ->
            state.copy(
                revision = state.revision + 1L,
                reservoir = state.reservoir.copy(
                    remainingMicroliters = state.reservoir.capacityMicroliters,
                    accountingCertain = true,
                    lowLevelActive = false
                )
            )
        }.toChannelResult()
    }

    fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.controls.manualDoseSupported) return rejectedNotEditable()
        if (!current.calibrated) return rejectedNotCalibrated()
        if (current.activeRun.active) return rejectedBusy()
        if (!current.scheduling.acceptsManualDose(amountMicroliters)) {
            return rejectedInvalidDraft()
        }
        val durationMillis = (
            amountMicroliters * FIXTURE_DOSE_MILLIS_PER_MILLILITER /
                MICROLITERS_PER_MILLILITER
            ).coerceAtLeast(1L)
        return updateChannel(deviceUid, slotId) { state ->
            state.copy(
                activeRun = DeviceDosingActiveRun(
                    active = true,
                    source = DeviceDosingRunSource.MANUAL,
                    targetAmountMicroliters = amountMicroliters,
                    remainingMillis = durationMillis.coerceAtMost(
                        state.scheduling.maximumPumpRunDurationMillis
                    )
                )
            )
        }.toChannelResult()
    }

    fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult {
        val current = currentChannel(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.controls.stopDoseSupported) return rejectedNotEditable()
        if (!current.activeRun.active) return rejectedInvalidDraft()
        return updateChannel(deviceUid, slotId) { state ->
            state.copy(activeRun = DeviceDosingActiveRun())
        }.toChannelResult()
    }

    fun resetChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult {
        val stateKey = key(deviceUid, slotId)
        val channelFlow = channelStates[stateKey]
            ?: return DeviceDosingChannelOperationResult.Unavailable
        val calibrationFlow = states[stateKey]
            ?: return DeviceDosingChannelOperationResult.Unavailable
        val channel = channelFlow.value
        if (!channel.controls.resetSupported) return rejectedNotEditable()

        val resetCalibration = calibrationFlow.value.copy(
            channelTitle = defaultTitles.getValue(stateKey),
            calibrated = false,
            lastCalibratedAt = 0L,
            sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
            startedAtUptimeMs = 0L,
            durationMs = 0L,
            measuredMl = 0.0,
            pendingDoseMsPerMl = 0L,
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
        calibrationFlow.value = resetCalibration
        channelFlow.value = channel.copy(
            channelTitle = resetCalibration.channelTitle,
            revision = channel.revision + 1L,
            calibrated = false,
            lastCalibratedAtEpochSeconds = 0L,
            program = null,
            progress = DeviceDosingChannelProgress(),
            reservoir = DeviceDosingReservoirSnapshot(),
            activeRun = DeviceDosingActiveRun(),
            usageToday = DeviceDosingDailyUsageSnapshot()
        ).withDerivedRuntime()
        return channelFlow.value.toChannelResult()
    }

    fun refresh(deviceUid: String, slotId: String): DeviceDosingCalibrationResult {
        val state = current(deviceUid, slotId) ?: return DeviceDosingCalibrationResult.Unavailable
        val refreshed = if (
            state.sessionPhase == DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION &&
            state.verificationDoseStarted &&
            !state.verificationDoseComplete
        ) {
            update(deviceUid, slotId) { current ->
                current.copy(
                    verificationDoseComplete = true,
                    verificationDoseRemainingMs = 0L,
                    manualActive = false
                )
            }
        } else {
            state
        }
        return refreshed.toResult()
    }

    fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(channelTitle = displayName.trim())
    }.toResult()

    fun primeStart(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state -> state.copy(manualActive = true) }.toResult()

    fun primeStop(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state -> state.copy(manualActive = false) }.toResult()

    fun start(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                deviceUptimeMs = FIXTURE_UPTIME_MS,
                sessionPhase = DeviceDosingCalibrationSessionPhase.RUNNING,
                startedAtUptimeMs = FIXTURE_UPTIME_MS,
                durationMs = CALIBRATION_DURATION_MS,
                measuredMl = 0.0,
                pendingDoseMsPerMl = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = true
            )
        }.toResult(operationDurationMs = CALIBRATION_DURATION_MS)

    fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
            durationMs = CALIBRATION_DURATION_MS,
            measuredMl = measuredMl,
            pendingDoseMsPerMl = (CALIBRATION_DURATION_MS / measuredMl)
                .toLong()
                .coerceAtLeast(1L),
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }.toResult()

    fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            sessionPhase = DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION,
            verificationDoseStarted = true,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = VERIFICATION_DURATION_MS,
            manualActive = true
        )
    }.toResult(operationDurationMs = VERIFICATION_DURATION_MS)

    fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = update(deviceUid, slotId) { state ->
        state.copy(
            verificationDoseStarted = false,
            verificationDoseComplete = false,
            verificationDoseRemainingMs = 0L,
            manualActive = false
        )
    }.toResult()

    fun confirm(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                calibrated = true,
                lastCalibratedAt = currentEpochSeconds(),
                sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                startedAtUptimeMs = 0L,
                durationMs = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = false
            )
        }.toResult()

    fun cancel(deviceUid: String, slotId: String): DeviceDosingCalibrationResult =
        update(deviceUid, slotId) { state ->
            state.copy(
                sessionPhase = DeviceDosingCalibrationSessionPhase.IDLE,
                startedAtUptimeMs = 0L,
                durationMs = 0L,
                measuredMl = 0.0,
                pendingDoseMsPerMl = 0L,
                verificationDoseStarted = false,
                verificationDoseComplete = false,
                verificationDoseRemainingMs = 0L,
                manualActive = false
            )
        }.toResult()

    private fun update(
        deviceUid: String,
        slotId: String,
        transform: (DeviceDosingCalibrationSnapshot) -> DeviceDosingCalibrationSnapshot
    ): DeviceDosingCalibrationSnapshot? {
        val stateKey = key(deviceUid, slotId)
        val flow = states[stateKey] ?: return null
        val previous = flow.value
        val updated = transform(previous)
        flow.value = updated
        channelStates[stateKey]?.let { channelFlow ->
            channelFlow.value = channelFlow.value.withCalibration(previous, updated)
        }
        return updated
    }

    private fun updateChannel(
        deviceUid: String,
        slotId: String,
        transform: (DeviceDosingChannelSnapshot) -> DeviceDosingChannelSnapshot
    ): DeviceDosingChannelSnapshot? {
        val flow = channelStates[key(deviceUid, slotId)] ?: return null
        return transform(flow.value)
            .withDerivedRuntime()
            .also { updated -> flow.value = updated }
    }

    private fun DeviceDosingChannelSnapshot.withCalibration(
        previous: DeviceDosingCalibrationSnapshot,
        updated: DeviceDosingCalibrationSnapshot
    ): DeviceDosingChannelSnapshot {
        val persistedChange = previous.channelTitle != updated.channelTitle ||
            previous.calibrated != updated.calibrated ||
            previous.lastCalibratedAt != updated.lastCalibratedAt
        val calibrationRun = when {
            updated.manualActive && updated.verificationDoseStarted -> DeviceDosingActiveRun(
                active = true,
                source = DeviceDosingRunSource.VERIFICATION,
                remainingMillis = updated.verificationDoseRemainingMs
            )
            updated.manualActive &&
                updated.sessionPhase == DeviceDosingCalibrationSessionPhase.RUNNING ->
                DeviceDosingActiveRun(
                    active = true,
                    source = DeviceDosingRunSource.CALIBRATION,
                    remainingMillis = updated.durationMs
                )
            updated.manualActive -> DeviceDosingActiveRun(
                active = true,
                source = DeviceDosingRunSource.PRIME,
                remainingMillis = FIXTURE_PRIME_REMAINING_MS
            )
            activeRun.source in CALIBRATION_RUN_SOURCES -> DeviceDosingActiveRun()
            else -> activeRun
        }
        return copy(
            channelTitle = updated.channelTitle,
            revision = revision + if (persistedChange) 1L else 0L,
            calibrated = updated.calibrated,
            lastCalibratedAtEpochSeconds = updated.lastCalibratedAt,
            activeRun = calibrationRun
        ).withDerivedRuntime()
    }

    private fun DeviceDosingChannelSnapshot.withDerivedRuntime(): DeviceDosingChannelSnapshot {
        val reservoirReady = !reservoir.trackingEnabled ||
            reservoir.accountingCertain && reservoir.remainingMicroliters > 0L
        val enabled = calibrated &&
            program?.enabled == true &&
            deliveryAccountingCertain &&
            reservoirReady
        val reason = when {
            !calibrated -> DeviceDosingRuntimeReason.MISSING_CALIBRATION
            !deliveryAccountingCertain ||
                reservoir.trackingEnabled && !reservoir.accountingCertain ->
                DeviceDosingRuntimeReason.ACCOUNTING_UNCERTAIN
            reservoir.trackingEnabled && reservoir.remainingMicroliters <= 0L ->
                DeviceDosingRuntimeReason.RESERVOIR_UNAVAILABLE
            activeRun.active -> DeviceDosingRuntimeReason.BUSY
            program?.enabled != true -> DeviceDosingRuntimeReason.PROGRAM_DISABLED
            else -> DeviceDosingRuntimeReason.NONE
        }
        return copy(runtimeEnabled = enabled, runtimeReason = reason)
    }

    private fun initialProgram(
        channelNumber: Int,
        pumpCount: Int,
        calibrated: Boolean
    ): DeviceDosingProgram? {
        if (!calibrated) return null
        if (pumpCount != DOSING_PRO_4_CHANNEL_COUNT) return compactFixtureProgram()
        val schedule = when (channelNumber) {
            SINGLE_FIXTURE_CHANNEL -> DeviceDosingProgramSchedule.Single(
                dailyDoseMicroliters = 3_000L,
                startTimeMillis = 9 * MILLIS_PER_HOUR
            )
            HOURLY_FIXTURE_CHANNEL -> DeviceDosingProgramSchedule.Hourly24(
                dailyDoseMicroliters = 24_000L,
                startTimeMillis = 15 * MILLIS_PER_MINUTE
            )
            CUSTOM_FIXTURE_CHANNEL -> DeviceDosingProgramSchedule.CustomPeriods(
                dailyDoseMicroliters = 12_000L,
                periods = listOf(
                    DeviceDosingCustomPeriodDraft(
                        startTimeMs = 8 * MILLIS_PER_HOUR,
                        endTimeMs = 10 * MILLIS_PER_HOUR,
                        doseCount = 3
                    ),
                    DeviceDosingCustomPeriodDraft(
                        startTimeMs = 14 * MILLIS_PER_HOUR,
                        endTimeMs = 16 * MILLIS_PER_HOUR,
                        doseCount = 3
                    ),
                    DeviceDosingCustomPeriodDraft(
                        startTimeMs = 20 * MILLIS_PER_HOUR,
                        endTimeMs = 22 * MILLIS_PER_HOUR,
                        doseCount = 2
                    )
                )
            )
            else -> DeviceDosingProgramSchedule.Timer(
                doses = listOf(
                    DeviceDosingTimerDoseDraft(7 * MILLIS_PER_HOUR, 1_500L),
                    DeviceDosingTimerDoseDraft(11 * MILLIS_PER_HOUR + 30 * MILLIS_PER_MINUTE, 2_000L),
                    DeviceDosingTimerDoseDraft(16 * MILLIS_PER_HOUR, 1_250L),
                    DeviceDosingTimerDoseDraft(21 * MILLIS_PER_HOUR, 2_750L)
                )
            )
        }
        return DeviceDosingProgram(
            enabled = channelNumber != CUSTOM_FIXTURE_CHANNEL,
            weekdays = fixtureWeekdays(channelNumber),
            schedule = schedule,
            missedDoseRecoveryEnabled = true
        )
    }

    private fun compactFixtureProgram() = DeviceDosingProgram(
        enabled = true,
        weekdays = List(WEEKDAY_COUNT) { true },
        schedule = DeviceDosingProgramSchedule.Timer(
            doses = listOf(
                DeviceDosingTimerDoseDraft(8 * MILLIS_PER_HOUR, 2_500L),
                DeviceDosingTimerDoseDraft(14 * MILLIS_PER_HOUR, 2_000L),
                DeviceDosingTimerDoseDraft(20 * MILLIS_PER_HOUR, 1_500L)
            )
        ),
        missedDoseRecoveryEnabled = true
    )

    private fun fixtureWeekdays(channelNumber: Int): List<Boolean> {
        val currentDayIndex = LocalDate.now().dayOfWeek.value - 1
        val configuredDays = when (channelNumber) {
            SINGLE_FIXTURE_CHANNEL -> listOf(false, true, false, true, false, true, false)
            TIMER_FIXTURE_CHANNEL -> listOf(true, true, true, true, true, false, false)
            else -> List(WEEKDAY_COUNT) { true }
        }
        return configuredDays.mapIndexed { index, selected ->
            selected || index == currentDayIndex
        }
    }

    private fun initialProgress(
        program: DeviceDosingProgram?,
        channelNumber: Int,
        pumpCount: Int,
        active: Boolean
    ): DeviceDosingChannelProgress {
        if (program == null || !program.enabled) return DeviceDosingChannelProgress()
        val completed = when {
            pumpCount != DOSING_PRO_4_CHANNEL_COUNT -> 1
            channelNumber == SINGLE_FIXTURE_CHANNEL -> 1
            channelNumber == HOURLY_FIXTURE_CHANNEL -> 18
            channelNumber == TIMER_FIXTURE_CHANNEL -> 2
            else -> 0
        }
        return compiledProgress(program, completed, active)
    }

    private fun initialUsage(
        progress: DeviceDosingChannelProgress,
        channelNumber: Int,
        pumpCount: Int
    ): DeviceDosingDailyUsageSnapshot {
        val manualAmount = if (
            pumpCount == DOSING_PRO_4_CHANNEL_COUNT &&
            channelNumber == SINGLE_FIXTURE_CHANNEL
        ) {
            10_000L
        } else {
            0L
        }
        return DeviceDosingDailyUsageSnapshot(
            valid = true,
            scheduledDeliveredMicroliters = progress.completedAmountMicroliters,
            manualDeliveredMicroliters = manualAmount,
            totalDeliveredMicroliters = progress.completedAmountMicroliters + manualAmount
        )
    }

    private fun progressFor(program: DeviceDosingProgram): DeviceDosingChannelProgress =
        if (program.enabled) compiledProgress(program, completedCount = 0, active = false)
        else DeviceDosingChannelProgress()

    private fun compiledProgress(
        program: DeviceDosingProgram,
        completedCount: Int,
        active: Boolean
    ): DeviceDosingChannelProgress {
        val occurrences = compiledOccurrences(program)
        val completed = completedCount.coerceIn(0, occurrences.size)
        val activeIndex = completed.takeIf { active && it < occurrences.size }
        val resolvedOccurrences = occurrences.mapIndexed { index, occurrence ->
            occurrence.copy(
                state = when {
                    index < completed -> DeviceDosingOccurrenceState.COMPLETED
                    index == activeIndex -> DeviceDosingOccurrenceState.RUNNING
                    else -> DeviceDosingOccurrenceState.PENDING
                }
            )
        }
        return DeviceDosingChannelProgress(
            scheduledAmountMicroliters = resolvedOccurrences.sumOf { it.amountMicroliters },
            completedAmountMicroliters = resolvedOccurrences
                .filter { it.state == DeviceDosingOccurrenceState.COMPLETED }
                .sumOf { it.amountMicroliters },
            occurrences = resolvedOccurrences,
            executionCurrent = true,
            accountingCertain = true
        )
    }

    private fun compiledOccurrences(program: DeviceDosingProgram):
        List<DeviceDosingOccurrenceProgress> {
        val planned = when (val schedule = program.schedule) {
            is DeviceDosingProgramSchedule.Single -> listOf(
                FixtureOccurrence(schedule.startTimeMillis, schedule.dailyDoseMicroliters)
            )
            is DeviceDosingProgramSchedule.Hourly24 -> {
                val amounts = splitAmount(schedule.dailyDoseMicroliters, HOURLY_DOSE_COUNT)
                List(HOURLY_DOSE_COUNT) { index ->
                    val absoluteTime = schedule.startTimeMillis + index * MILLIS_PER_HOUR
                    FixtureOccurrence(
                        timeMillis = absoluteTime % MILLIS_PER_DAY,
                        amountMicroliters = amounts[index],
                        programDayOffset = (absoluteTime / MILLIS_PER_DAY).toInt()
                    )
                }
            }
            is DeviceDosingProgramSchedule.CustomPeriods -> customFixtureOccurrences(schedule)
            is DeviceDosingProgramSchedule.Timer -> schedule.doses.map { dose ->
                FixtureOccurrence(dose.startTimeMs, dose.amountMicroliters)
            }
        }
        return planned.mapIndexed { index, occurrence ->
            DeviceDosingOccurrenceProgress(
                index = index,
                eventId = index + 1L,
                programDayOffset = occurrence.programDayOffset,
                timeMillis = occurrence.timeMillis,
                amountMicroliters = occurrence.amountMicroliters,
                state = DeviceDosingOccurrenceState.PENDING
            )
        }
    }

    private fun customFixtureOccurrences(
        schedule: DeviceDosingProgramSchedule.CustomPeriods
    ): List<FixtureOccurrence> {
        val amounts = splitAmount(
            schedule.dailyDoseMicroliters,
            schedule.periods.sumOf(DeviceDosingCustomPeriodDraft::doseCount)
        )
        var amountIndex = 0
        return schedule.periods.flatMap { period ->
            List(period.doseCount) { index ->
                val interval = if (period.doseCount <= 1) {
                    0L
                } else {
                    (period.endTimeMs - period.startTimeMs) / (period.doseCount - 1)
                }
                FixtureOccurrence(
                    timeMillis = period.startTimeMs + interval * index,
                    amountMicroliters = amounts[amountIndex++]
                )
            }
        }
    }

    private fun splitAmount(totalMicroliters: Long, count: Int): List<Long> {
        val base = totalMicroliters / count
        val remainder = (totalMicroliters % count).toInt()
        return List(count) { index -> base + if (index < remainder) 1L else 0L }
    }

    private fun initialActiveRun(
        channelNumber: Int,
        pumpCount: Int,
        calibrated: Boolean
    ): DeviceDosingActiveRun = if (
        calibrated &&
        pumpCount == DOSING_PRO_4_CHANNEL_COUNT &&
        channelNumber == TIMER_FIXTURE_CHANNEL
    ) {
        DeviceDosingActiveRun(
            active = true,
            source = DeviceDosingRunSource.SCHEDULED,
            targetAmountMicroliters = 1_250L,
            remainingMillis = 1_200L
        )
    } else {
        DeviceDosingActiveRun()
    }

    private fun initialReservoir(
        channelNumber: Int,
        pumpCount: Int,
        calibrated: Boolean
    ): DeviceDosingReservoirSnapshot {
        if (!calibrated) return DeviceDosingReservoirSnapshot()
        if (
            pumpCount == DOSING_PRO_4_CHANNEL_COUNT &&
            channelNumber == CUSTOM_FIXTURE_CHANNEL
        ) {
            return DeviceDosingReservoirSnapshot()
        }
        val remaining = when (channelNumber) {
            SINGLE_FIXTURE_CHANNEL -> 350_000L
            HOURLY_FIXTURE_CHANNEL -> 188_500L
            TIMER_FIXTURE_CHANNEL -> 47_000L
            else -> 120_000L
        }
        return DeviceDosingReservoirSnapshot(
            trackingEnabled = true,
            capacityMicroliters = 450_000L,
            remainingMicroliters = remaining,
            accountingCertain = true,
            lowLevelActive = remaining <= 45_000L,
            lowLevelAlertEnabled = true
        )
    }

    private data class FixtureOccurrence(
        val timeMillis: Long,
        val amountMicroliters: Long,
        val programDayOffset: Int = 0
    )

    private fun DeviceDosingCalibrationSnapshot?.toResult(
        operationDurationMs: Long? = null
    ): DeviceDosingCalibrationResult = this?.let { snapshot ->
        DeviceDosingCalibrationResult.Success(snapshot, operationDurationMs)
    } ?: DeviceDosingCalibrationResult.Unavailable

    private fun DeviceDosingChannelSnapshot?.toChannelResult():
        DeviceDosingChannelOperationResult = this?.let { snapshot ->
        DeviceDosingChannelOperationResult.Success(snapshot)
    } ?: DeviceDosingChannelOperationResult.Unavailable

    private fun rejectedInvalidDraft() = DeviceDosingChannelOperationResult.Rejected(
        DeviceDosingChannelRejection.INVALID_DRAFT
    )

    private fun rejectedNotEditable() = DeviceDosingChannelOperationResult.Rejected(
        DeviceDosingChannelRejection.NOT_EDITABLE
    )

    private fun rejectedNotCalibrated() = DeviceDosingChannelOperationResult.Rejected(
        DeviceDosingChannelRejection.NOT_CALIBRATED
    )

    private fun rejectedBusy() = DeviceDosingChannelOperationResult.Rejected(
        DeviceDosingChannelRejection.BUSY
    )

    private fun key(deviceUid: String, slotId: String): String =
        "${deviceUid.trim()}|${slotId.trim()}"

    private fun currentEpochSeconds(): Long =
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    private companion object {
        const val FIXTURE_UPTIME_MS = 60_000L
        const val CALIBRATION_DURATION_MS = 5_000L
        const val VERIFICATION_DURATION_MS = 1_000L
        const val FIXTURE_PRIME_REMAINING_MS = 30_000L
        const val FIXTURE_DOSE_MILLIS_PER_MILLILITER = 1_000L
        const val MICROLITERS_PER_MILLILITER = 1_000L
        const val LOW_LEVEL_DIVISOR = 10L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 60L * MILLIS_PER_MINUTE
        const val MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR
        const val WEEKDAY_COUNT = 7
        const val HOURLY_DOSE_COUNT = 24
        const val DOSING_PRO_4_CHANNEL_COUNT = 4
        const val SINGLE_FIXTURE_CHANNEL = 1
        const val HOURLY_FIXTURE_CHANNEL = 2
        const val CUSTOM_FIXTURE_CHANNEL = 3
        const val TIMER_FIXTURE_CHANNEL = 4

        val CALIBRATION_RUN_SOURCES = setOf(
            DeviceDosingRunSource.CALIBRATION,
            DeviceDosingRunSource.VERIFICATION,
            DeviceDosingRunSource.PRIME
        )

        val FIXTURE_SCHEDULING_POLICY = DeviceDosingSchedulingPolicy(
            amountResolutionMicroliters = 1L,
            maxEventsPerChannel = 24,
            maxCustomPeriodsPerChannel = 24,
            scheduledDispatchGraceMillis = 120_000L,
            minimumPumpRunDurationMillis = 25L,
            maximumPumpRunDurationMillis = 60_000L,
            maximumManualDoseMicroliters = 1_000_000L,
            supportsWeekdayRecurrence = true,
            supportsMissedDoseRecovery = true,
            supportsChannelReset = true,
            supportsDailyDeliveredUsage = true
        )
    }
}

/** Routes fixture devices to the debug state store and real devices to the production boundary. */
internal class DebugFixtureDosingCalibrationOperations(
    private val delegate: DeviceDosingCalibrationOperations,
    private val fixtures: DebugDeviceFixtureCatalog,
    private val stateStore: DebugFixtureDosingStateStore
) : DeviceDosingCalibrationOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> = if (fixtures.contains(deviceUid)) {
        stateStore.observe(deviceUid, slotId)
    } else {
        delegate.observe(deviceUid, slotId)
    }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.refresh(deviceUid, slotId)
    } else {
        delegate.refresh(deviceUid, slotId)
    }

    override suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.saveDisplayName(deviceUid, slotId, displayName)
    } else {
        delegate.saveDisplayName(deviceUid, slotId, displayName)
    }

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.primeStart(deviceUid, slotId)
    } else {
        delegate.primeStart(deviceUid, slotId)
    }

    override suspend fun primeStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.primeStop(deviceUid, slotId)
    } else {
        delegate.primeStop(deviceUid, slotId)
    }

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.start(deviceUid, slotId)
    } else {
        delegate.start(deviceUid, slotId)
    }

    override suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.finish(deviceUid, slotId, measuredMl)
    } else {
        delegate.finish(deviceUid, slotId, measuredMl)
    }

    override suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.startVerificationDose(deviceUid, slotId)
    } else {
        delegate.startVerificationDose(deviceUid, slotId)
    }

    override suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.stopVerificationDose(deviceUid, slotId)
    } else {
        delegate.stopVerificationDose(deviceUid, slotId)
    }

    override suspend fun confirm(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.confirm(deviceUid, slotId)
    } else {
        delegate.confirm(deviceUid, slotId)
    }

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = if (fixtures.contains(deviceUid)) {
        stateStore.cancel(deviceUid, slotId)
    } else {
        delegate.cancel(deviceUid, slotId)
    }
}
