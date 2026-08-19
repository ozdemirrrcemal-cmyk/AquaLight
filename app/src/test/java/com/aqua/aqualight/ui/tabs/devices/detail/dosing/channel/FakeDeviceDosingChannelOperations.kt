package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel

import com.aqua.aqualight.application.devices.dosing.DeviceDosingActiveRun
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelControls
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelRejection
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceProgress
import com.aqua.aqualight.application.devices.dosing.DeviceDosingOccurrenceState
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgram
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramRevisionOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramSchedule
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirRevisionOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSettings
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRunSource
import com.aqua.aqualight.application.devices.dosing.DeviceDosingRuntimeReason
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeDeviceDosingChannelOperations(
    initialSnapshot: DeviceDosingChannelSnapshot? = sampleDosingChannelSnapshot()
) : DeviceDosingChannelOperations,
    DeviceDosingProgramRevisionOperations,
    DeviceDosingReservoirRevisionOperations {
    val snapshot = MutableStateFlow(initialSnapshot)
    var failMutations: Boolean = false
    var lastProgram: DeviceDosingProgram? = null
    var lastMissedDoseRecoveryEnabled: Boolean? = null
    var lastReservoirSettings: DeviceDosingReservoirSettings? = null
    var lastReservoirExpectedRevision: Long? = null
    var lastManualDoseMicroliters: Long? = null
    var reservoirConfigMutationCount: Int = 0
    var lowLevelAlertMutationCount: Int = 0
    var refillMutationCount: Int = 0

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingChannelSnapshot?> = snapshot

    override fun observeAll(deviceUid: String): Flow<List<DeviceDosingChannelSnapshot>> =
        snapshot.map { value -> listOfNotNull(value) }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = snapshot.value.toResult()

    override suspend fun refreshAll(deviceUid: String): Boolean = snapshot.value != null

    override suspend fun applyProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram
    ): DeviceDosingChannelOperationResult {
        lastProgram = program
        return mutate { state -> state.copy(revision = state.revision + 1L, program = program) }
    }

    override suspend fun applyProgramAtRevision(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgram,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult {
        val current = snapshot.value
        return when {
            current == null -> DeviceDosingChannelOperationResult.Unavailable
            current.revision != expectedRevision -> DeviceDosingChannelOperationResult.Rejected(
                DeviceDosingChannelRejection.CONFLICT
            )
            else -> applyProgram(deviceUid, slotId, program)
        }
    }

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        lastMissedDoseRecoveryEnabled = enabled
        return mutate { state ->
            state.copy(
                revision = state.revision + 1L,
                program = state.program?.copy(missedDoseRecoveryEnabled = enabled)
            )
        }
    }

    override suspend fun applyReservoirSettings(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings
    ): DeviceDosingChannelOperationResult {
        val revision = snapshot.value?.revision ?: return DeviceDosingChannelOperationResult.Unavailable
        return applyReservoirSettingsAtRevision(deviceUid, slotId, settings, revision)
    }

    override suspend fun applyReservoirSettingsAtRevision(
        deviceUid: String,
        slotId: String,
        settings: DeviceDosingReservoirSettings,
        expectedRevision: Long
    ): DeviceDosingChannelOperationResult {
        val current = snapshot.value
        return when {
            current == null -> DeviceDosingChannelOperationResult.Unavailable
            current.revision != expectedRevision -> DeviceDosingChannelOperationResult.Rejected(
                DeviceDosingChannelRejection.CONFLICT
            )
            else -> {
                lastReservoirSettings = settings
                lastReservoirExpectedRevision = expectedRevision
                reservoirConfigMutationCount += 1
                mutate { state ->
                    val capacity = settings.capacityMicroliters ?: 0L
                    state.copy(
                        revision = state.revision + 1L,
                        reservoir = DeviceDosingReservoirSnapshot(
                            trackingEnabled = settings.trackingEnabled,
                            capacityMicroliters = capacity,
                            remainingMicroliters = capacity,
                            lowLevelAlertEnabled = settings.lowLevelAlertEnabled
                        )
                    )
                }
            }
        }
    }

    override suspend fun setReservoirLowLevelAlertEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        lowLevelAlertMutationCount += 1
        return mutate { state ->
            state.copy(
                reservoir = state.reservoir.copy(lowLevelAlertEnabled = enabled)
            )
        }
    }

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult {
        refillMutationCount += 1
        return mutate { state ->
            state.copy(
                reservoir = state.reservoir.copy(
                    remainingMicroliters = state.reservoir.capacityMicroliters,
                    accountingCertain = true,
                    lowLevelActive = false
                )
            )
        }
    }

    override suspend fun doseNow(
        deviceUid: String,
        slotId: String,
        amountMicroliters: Long
    ): DeviceDosingChannelOperationResult {
        lastManualDoseMicroliters = amountMicroliters
        return mutate { state ->
            state.copy(
                activeRun = DeviceDosingActiveRun(
                    active = true,
                    source = DeviceDosingRunSource.MANUAL,
                    targetAmountMicroliters = amountMicroliters,
                    remainingMillis = 1_000L
                )
            )
        }
    }

    override suspend fun doseStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = mutate { state ->
        state.copy(activeRun = DeviceDosingActiveRun())
    }

    override suspend fun reset(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = mutate { state ->
        state.copy(
            revision = state.revision + 1L,
            calibrated = false,
            lastCalibratedAtEpochSeconds = 0L,
            program = null,
            progress = DeviceDosingChannelProgress(),
            reservoir = DeviceDosingReservoirSnapshot(),
            activeRun = DeviceDosingActiveRun()
        )
    }

    private fun mutate(
        transform: (DeviceDosingChannelSnapshot) -> DeviceDosingChannelSnapshot
    ): DeviceDosingChannelOperationResult = when {
        failMutations -> DeviceDosingChannelOperationResult.Failed
        snapshot.value == null -> DeviceDosingChannelOperationResult.Unavailable
        else -> transform(requireNotNull(snapshot.value))
            .also { updated -> snapshot.value = updated }
            .toResult()
    }

    private fun DeviceDosingChannelSnapshot?.toResult(): DeviceDosingChannelOperationResult =
        this?.let { snapshot -> DeviceDosingChannelOperationResult.Success(snapshot) }
            ?: DeviceDosingChannelOperationResult.Unavailable
}

internal fun sampleDosingChannelSnapshot(): DeviceDosingChannelSnapshot {
    val program = DeviceDosingProgram(
        enabled = true,
        weekdays = List(7) { true },
        schedule = DeviceDosingProgramSchedule.Single(
            dailyDoseMicroliters = 3_000L,
            startTimeMillis = 28_800_000L
        ),
        missedDoseRecoveryEnabled = false
    )
    return DeviceDosingChannelSnapshot(
        deviceUid = "device-1",
        slotId = "dosing:channel2",
        pumpCount = 2,
        channelNumber = 2,
        channelTitle = "Channel 2",
        revision = 1L,
        runtimeEnabled = true,
        runtimeReason = DeviceDosingRuntimeReason.NONE,
        deliveryAccountingCertain = true,
        calibrated = true,
        lastCalibratedAtEpochSeconds = 100L,
        scheduling = DeviceDosingSchedulingPolicy(),
        program = program,
        progress = DeviceDosingChannelProgress(
            scheduledAmountMicroliters = 3_000L,
            occurrences = listOf(
                DeviceDosingOccurrenceProgress(
                    index = 0,
                    eventId = 1L,
                    programDayOffset = 0,
                    timeMillis = 28_800_000L,
                    amountMicroliters = 3_000L,
                    state = DeviceDosingOccurrenceState.PENDING
                )
            ),
            executionCurrent = true
        ),
        reservoir = DeviceDosingReservoirSnapshot(
            trackingEnabled = true,
            capacityMicroliters = 450_000L,
            remainingMicroliters = 250_000L
        ),
        activeRun = DeviceDosingActiveRun(),
        controls = DeviceDosingChannelControls(
            programEditable = true,
            reservoirEditable = true,
            displayNameEditable = true,
            calibrationEditable = true,
            manualDoseSupported = true,
            stopDoseSupported = true,
            resetSupported = true,
            refillSupported = true
        )
    )
}
