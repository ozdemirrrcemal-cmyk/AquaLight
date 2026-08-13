package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelProgramSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingChannelSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramCustomPeriodDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftConfig
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramDraftMode
import com.aqua.aqualight.application.devices.dosing.DeviceDosingProgramTimerEventDraft
import com.aqua.aqualight.application.devices.dosing.DeviceDosingReservoirSnapshot
import com.aqua.aqualight.application.devices.dosing.DeviceDosingSchedulingConstraints
import com.aqua.aqualight.application.devices.dosing.DeviceDosingUsageSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingChannelStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriod
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCustomPeriodsProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDistributedProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgram
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingProgramMode
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingReservoirConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerEvent
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingTimerProgramConfig
import com.aqua.aqualight.data.devices.runtime.modules.dosing.repository.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Product-slot adapter over the single central Dosing runtime source-of-truth. */
internal class DefaultDeviceDosingChannelOperations(
    private val devicesRepository: DevicesRepository
) : DeviceDosingChannelOperations {

    override fun observe(deviceUid: String, slotId: String): Flow<DeviceDosingChannelSnapshot?> {
        val context = resolveContext(deviceUid, slotId) ?: return flowOf(null)
        return context.runtime.states.map { states ->
            states[context.uid]
                ?.channel(context.slot.wireKey.value)
                ?.toApplicationSnapshot(context)
        }.distinctUntilChanged()
    }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult {
        val context = resolveContext(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        return context.runtime.requestChannelStatus(
            context.uid,
            context.slot.wireKey.value
        ).toApplicationResult(context)
    }

    override suspend fun saveProgram(
        deviceUid: String,
        slotId: String,
        program: DeviceDosingProgramDraft
    ): DeviceDosingChannelOperationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.saveProgram(
            context.uid,
            context.slot.wireKey.value,
            program.toRuntimeProgram()
        )
    }

    override suspend fun setMissedDoseRecoveryEnabled(
        deviceUid: String,
        slotId: String,
        enabled: Boolean
    ): DeviceDosingChannelOperationResult {
        val context = resolveContext(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        val current = currentChannel(context)
            ?: when (val refreshed = context.runtime.requestChannelStatus(
                context.uid,
                context.slot.wireKey.value
            )) {
                is DeviceRuntimeCommandOutcome.Success -> refreshed.value
                else -> return refreshed.toApplicationFailure()
            }
        val program = current.channel.program ?: return DeviceDosingChannelOperationResult.Unavailable
        if (!current.scheduling.supportsMissedDoseRecovery && enabled) {
            return DeviceDosingChannelOperationResult.Unavailable
        }
        return executeAndRefresh(context) {
            context.runtime.saveProgram(
                context.uid,
                context.slot.wireKey.value,
                program.copy(missedDoseRecoveryEnabled = enabled)
            )
        }
    }

    override suspend fun dispenseManualDose(
        deviceUid: String,
        slotId: String,
        amountMl: Double
    ): DeviceDosingChannelOperationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.doseNow(
            context.uid,
            DeviceDosingDoseNowPayload(
                channelKey = context.slot.wireKey.value,
                amountMl = amountMl
            )
        )
    }

    override suspend fun resetChannel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.resetChannel(context.uid, context.slot.wireKey.value)
    }

    override suspend fun saveReservoir(
        deviceUid: String,
        slotId: String,
        trackingEnabled: Boolean,
        capacityMl: Double?
    ): DeviceDosingChannelOperationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.configureReservoir(
            context.uid,
            context.slot.wireKey.value,
            DeviceDosingReservoirConfig(
                trackingEnabled = trackingEnabled,
                capacityMl = capacityMl
            )
        )
    }

    override suspend fun refillReservoir(
        deviceUid: String,
        slotId: String
    ): DeviceDosingChannelOperationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.reservoirRefill(context.uid, context.slot.wireKey.value)
    }

    private suspend fun executeAndRefresh(
        deviceUid: String,
        slotId: String,
        command: suspend (ChannelContext) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceDosingChannelOperationResult {
        val context = resolveContext(deviceUid, slotId)
            ?: return DeviceDosingChannelOperationResult.Unavailable
        return executeAndRefresh(context, command)
    }

    private suspend fun executeAndRefresh(
        context: ChannelContext,
        command: suspend (ChannelContext) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceDosingChannelOperationResult {
        val outcome = runCatching { command(context) }
            .getOrElse { return DeviceDosingChannelOperationResult.Failed }
        if (outcome !is DeviceRuntimeCommandOutcome.Success<*>) return outcome.toApplicationFailure()
        return context.runtime.requestChannelStatus(
            context.uid,
            context.slot.wireKey.value
        ).toApplicationResult(context)
    }

    private fun currentChannel(context: ChannelContext): DeviceDosingChannelStatus? =
        context.runtime.states.value[context.uid]?.channel(context.slot.wireKey.value)

    private fun resolveContext(deviceUid: String, slotId: String): ChannelContext? {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid) ?: return null
        val normalizedSlotId = slotId.trim().takeIf(String::isNotBlank) ?: return null
        val root = devicesRepository.currentDevice(uid)?.toDeviceRootSnapshot()
            ?.takeIf { snapshot ->
                snapshot.catalogState == DeviceRootCatalogState.VALID &&
                    snapshot.family == OwnerDeviceFamily.DOSING
            }
            ?: return null
        val slot = root.channelSlots.dosingChannels.singleOrNull { candidate ->
            candidate.id.value == normalizedSlotId
        } ?: return null
        val runtime = devicesRepository.runtimeModules()?.dosing ?: return null
        return ChannelContext(uid, slot, runtime)
    }

    private fun DeviceRuntimeCommandOutcome<DeviceDosingChannelStatus>.toApplicationResult(
        context: ChannelContext
    ): DeviceDosingChannelOperationResult = when (this) {
        is DeviceRuntimeCommandOutcome.Success -> value.toApplicationSnapshot(context)
            ?.let(DeviceDosingChannelOperationResult::Success)
            ?: DeviceDosingChannelOperationResult.Unavailable
        else -> toApplicationFailure()
    }

    private fun DeviceRuntimeCommandOutcome<*>.toApplicationFailure(): DeviceDosingChannelOperationResult =
        when (this) {
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceDosingChannelOperationResult.Unavailable
            else -> DeviceDosingChannelOperationResult.Failed
        }

    private fun DeviceDosingChannelStatus.toApplicationSnapshot(
        context: ChannelContext
    ): DeviceDosingChannelSnapshot? {
        val detail = channel.takeIf { item ->
            item.index == context.slot.index.zeroBased && item.channelKey == context.slot.wireKey.value
        } ?: return null
        val effectiveDose = scheduling.effectiveScheduledDose
        return DeviceDosingChannelSnapshot(
            deviceUid = context.uid.value,
            slotId = context.slot.id.value,
            channelKey = detail.channelKey,
            revision = detail.revision,
            channelTitle = detail.effectiveName.ifBlank { context.slot.defaultDisplayName },
            calibrated = detail.calibration.confirmed,
            lastCalibratedAt = detail.calibration.lastCalibratedAt,
            runtimeEnabled = detail.runtimeEnabled,
            runtimeReason = detail.runtimeReason.wireValue,
            program = detail.program?.toApplicationProgram(detail.revision),
            scheduling = DeviceDosingSchedulingConstraints(
                amountResolutionMl = scheduling.amountResolutionMl,
                maxEventsPerChannel = scheduling.maxEventsPerChannel,
                maxCustomPeriodsPerChannel = scheduling.maxCustomPeriodsPerChannel,
                missedDoseRecoveryWindowMs = scheduling.missedDoseRecoveryWindowMs,
                minPumpRunDurationMs = scheduling.minPumpRunDurationMs,
                maxPumpRunDurationMs = scheduling.maxPumpRunDurationMs,
                maxManualDoseMl = scheduling.maxManualDoseMl,
                supportsMissedDoseRecovery = scheduling.supportsMissedDoseRecovery,
                supportsChannelReset = scheduling.supportsChannelReset,
                effectiveScheduledDoseMinMl = effectiveDose.minDoseMl,
                effectiveScheduledDoseMaxMl = effectiveDose.maxDoseMl
            ),
            usageToday = DeviceDosingUsageSnapshot(
                localDate = detail.usageToday.localDate,
                scheduledDeliveredMl = detail.usageToday.scheduledDeliveredMl,
                manualDeliveredMl = detail.usageToday.manualDeliveredMl,
                totalDeliveredMl = detail.usageToday.totalDeliveredMl
            ),
            reservoir = DeviceDosingReservoirSnapshot(
                trackingEnabled = detail.reservoir.trackingEnabled,
                capacityMl = detail.reservoir.capacityMl.takeIf { it >= 0.0 },
                remainingMl = detail.reservoir.remainingMl.takeIf { it >= 0.0 },
                accountingCertain = detail.reservoir.accountingCertain,
                remainingPercent = detail.reservoir.remainingPercent.takeIf { it >= 0.0 }
            ),
            active = detail.activeRun.active
        )
    }

    private fun DeviceDosingProgram.toApplicationProgram(revision: Long) =
        DeviceDosingChannelProgramSnapshot(
            revision = revision,
            enabled = enabled,
            weekdays = weekdays,
            mode = mode.toApplicationMode(),
            missedDoseRecoveryEnabled = missedDoseRecoveryEnabled,
            config = config.toApplicationConfig()
        )

    private fun DeviceDosingProgramConfig.toApplicationConfig(): DeviceDosingProgramDraftConfig = when (this) {
        is DeviceDosingDistributedProgramConfig -> DeviceDosingProgramDraftConfig.Distributed(
            dailyDoseMl = dailyDoseMl,
            startTimeMs = startTimeMs
        )
        is DeviceDosingCustomPeriodsProgramConfig -> DeviceDosingProgramDraftConfig.CustomPeriods(
            dailyDoseMl = dailyDoseMl,
            periods = periods.map { period ->
                DeviceDosingProgramCustomPeriodDraft(
                    startTimeMs = period.startTimeMs,
                    endTimeMs = period.endTimeMs,
                    doseCount = period.doseCount
                )
            }
        )
        is DeviceDosingTimerProgramConfig -> DeviceDosingProgramDraftConfig.Timer(
            events = events.map { event ->
                DeviceDosingProgramTimerEventDraft(event.timeMs, event.amountMl)
            }
        )
    }

    private fun DeviceDosingProgramDraft.toRuntimeProgram() = DeviceDosingProgram(
        enabled = enabled,
        weekdays = weekdays,
        mode = mode.toRuntimeMode(),
        missedDoseRecoveryEnabled = missedDoseRecoveryEnabled,
        config = config.toRuntimeConfig()
    )

    private fun DeviceDosingProgramDraftConfig.toRuntimeConfig(): DeviceDosingProgramConfig = when (this) {
        is DeviceDosingProgramDraftConfig.Distributed -> DeviceDosingDistributedProgramConfig(
            dailyDoseMl = dailyDoseMl,
            startTimeMs = startTimeMs
        )
        is DeviceDosingProgramDraftConfig.CustomPeriods -> DeviceDosingCustomPeriodsProgramConfig(
            dailyDoseMl = dailyDoseMl,
            periods = periods.map { period ->
                DeviceDosingCustomPeriod(period.startTimeMs, period.endTimeMs, period.doseCount)
            }
        )
        is DeviceDosingProgramDraftConfig.Timer -> DeviceDosingTimerProgramConfig(
            events = events.map { event -> DeviceDosingTimerEvent(event.timeMs, event.amountMl) }
        )
    }

    private fun DeviceDosingProgramDraftMode.toRuntimeMode() = when (this) {
        DeviceDosingProgramDraftMode.SINGLE -> DeviceDosingProgramMode.SINGLE
        DeviceDosingProgramDraftMode.HOURLY_24 -> DeviceDosingProgramMode.HOURLY_24
        DeviceDosingProgramDraftMode.CUSTOM_PERIODS -> DeviceDosingProgramMode.CUSTOM_PERIODS
        DeviceDosingProgramDraftMode.TIMER -> DeviceDosingProgramMode.TIMER
    }

    private fun DeviceDosingProgramMode.toApplicationMode() = when (this) {
        DeviceDosingProgramMode.SINGLE -> DeviceDosingProgramDraftMode.SINGLE
        DeviceDosingProgramMode.HOURLY_24 -> DeviceDosingProgramDraftMode.HOURLY_24
        DeviceDosingProgramMode.CUSTOM_PERIODS -> DeviceDosingProgramDraftMode.CUSTOM_PERIODS
        DeviceDosingProgramMode.TIMER -> DeviceDosingProgramDraftMode.TIMER
    }

    private data class ChannelContext(
        val uid: DeviceUid,
        val slot: DeviceDosingChannelSlot,
        val runtime: DeviceDosingRuntimeRepository
    )
}
