@file:Suppress("LongMethod", "LongParameterList", "ReturnCount", "TooManyFunctions")

package com.aqua.aqualight.data.devices.dosing

import com.aqua.aqualight.application.devices.DeviceDosingChannelSlot
import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationOperations
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationResult
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSessionPhase
import com.aqua.aqualight.application.devices.dosing.DeviceDosingCalibrationSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.dosing.contract.DeviceDosingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationFinishPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationStartResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingCalibrationState
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowPayload
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingDoseNowResult
import com.aqua.aqualight.data.devices.runtime.modules.dosing.models.DeviceDosingStatus
import com.aqua.aqualight.data.devices.runtime.modules.dosing.repository.DeviceDosingRuntimeRepository
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Resolves catalog slot identity once, then delegates every command to the central runtime. */
internal class DefaultDeviceDosingCalibrationOperations(
    private val devicesRepository: DevicesRepository
) : DeviceDosingCalibrationOperations {

    override fun observe(
        deviceUid: String,
        slotId: String
    ): Flow<DeviceDosingCalibrationSnapshot?> {
        val context = resolveContext(deviceUid, slotId) ?: return flowOf(null)
        return context.runtime.states
            .map { states ->
                states[context.uid]?.status?.toSnapshot(context)
            }
            .distinctUntilChanged()
    }

    override suspend fun refresh(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult {
        val context = resolveContext(deviceUid, slotId)
            ?: return DeviceDosingCalibrationResult.Unavailable
        return context.runtime.requestStatus(context.uid).toResult(context)
    }

    override suspend fun saveDisplayName(
        deviceUid: String,
        slotId: String,
        displayName: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.setChannelDisplayName(
            deviceUid = context.uid,
            channelKey = context.slot.wireKey.value,
            displayName = displayName,
            save = true
        )
    }

    override suspend fun primeStart(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.primeStart(context.uid, context.slot.wireKey.value)
    }

    override suspend fun primeStop(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.primeStop(context.uid, context.slot.wireKey.value)
    }

    override suspend fun start(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(
        deviceUid = deviceUid,
        slotId = slotId,
        operationDuration = { outcome ->
            (outcome as? DeviceRuntimeCommandOutcome.Success<*>)
                ?.value
                ?.let { value -> (value as? DeviceDosingCalibrationStartResult)?.durationMs }
        }
    ) { context ->
        context.runtime.calibrationStart(
            context.uid,
            DeviceDosingCalibrationStartPayload(
                channelKey = context.slot.wireKey.value,
                durationMs = DeviceDosingRuntimeContract.Limit.DEFAULT_CALIBRATION_DURATION_MS
            )
        )
    }

    override suspend fun finish(
        deviceUid: String,
        slotId: String,
        measuredMl: Double
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.calibrationFinish(
            context.uid,
            DeviceDosingCalibrationFinishPayload(
                channelKey = context.slot.wireKey.value,
                measuredMl = measuredMl
            )
        )
    }

    override suspend fun startVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(
        deviceUid = deviceUid,
        slotId = slotId,
        operationDuration = { outcome ->
            (outcome as? DeviceRuntimeCommandOutcome.Success<*>)
                ?.value
                ?.let { value -> (value as? DeviceDosingDoseNowResult)?.durationMs }
        }
    ) { context ->
        context.runtime.doseNow(
            context.uid,
            DeviceDosingDoseNowPayload(
                channelKey = context.slot.wireKey.value,
                amountMl = DeviceDosingRuntimeContract.Limit.VERIFICATION_DOSE_ML,
                usePendingCalibration = true
            )
        )
    }

    override suspend fun stopVerificationDose(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.doseStop(context.uid, context.slot.wireKey.value)
    }

    override suspend fun confirm(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.calibrationConfirm(context.uid, context.slot.wireKey.value)
    }

    override suspend fun cancel(
        deviceUid: String,
        slotId: String
    ): DeviceDosingCalibrationResult = executeAndRefresh(deviceUid, slotId) { context ->
        context.runtime.calibrationCancel(context.uid, context.slot.wireKey.value)
    }

    private suspend fun executeAndRefresh(
        deviceUid: String,
        slotId: String,
        operationDuration: (DeviceRuntimeCommandOutcome<*>) -> Long? = { null },
        command: suspend (CalibrationContext) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceDosingCalibrationResult {
        val context = resolveContext(deviceUid, slotId)
            ?: return DeviceDosingCalibrationResult.Unavailable
        val outcome = runCatching { command(context) }
            .getOrElse { return DeviceDosingCalibrationResult.Failed }
        if (outcome !is DeviceRuntimeCommandOutcome.Success<*>) return outcome.toFailureResult()
        val durationMs = operationDuration(outcome)
        val refreshed = context.runtime.requestStatus(context.uid).toResult(context)
        return if (refreshed is DeviceDosingCalibrationResult.Success) {
            refreshed.copy(operationDurationMs = durationMs)
        } else {
            refreshed
        }
    }

    private fun resolveContext(deviceUid: String, slotId: String): CalibrationContext? {
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
        return CalibrationContext(uid, slot, root.channelSlots.dosingChannels.size, runtime)
    }

    private fun DeviceRuntimeCommandOutcome<DeviceDosingStatus>.toResult(
        context: CalibrationContext
    ): DeviceDosingCalibrationResult = when (this) {
        is DeviceRuntimeCommandOutcome.Success -> value.toSnapshot(context)
            ?.let { snapshot -> DeviceDosingCalibrationResult.Success(snapshot) }
            ?: DeviceDosingCalibrationResult.Unavailable
        else -> toFailureResult()
    }

    private fun DeviceRuntimeCommandOutcome<*>.toFailureResult(): DeviceDosingCalibrationResult =
        when (this) {
            is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                DeviceDosingCalibrationResult.Unavailable
            else -> DeviceDosingCalibrationResult.Failed
        }

    private fun DeviceDosingStatus.toSnapshot(
        context: CalibrationContext
    ): DeviceDosingCalibrationSnapshot? = channels.singleOrNull { channel ->
        channel.index == context.slot.index.zeroBased &&
            channel.key == context.slot.wireKey.value
    }?.let { channel ->
        val session = channel.dosing.calibration
        DeviceDosingCalibrationSnapshot(
            deviceUid = context.uid.value,
            slotId = context.slot.id.value,
            pumpCount = context.pumpCount,
            channelNumber = context.slot.index.position,
            channelTitle = channel.displayName.ifBlank { context.slot.defaultDisplayName },
            deviceUptimeMs = uptimeMs,
            calibrated = channel.dosing.calibrated,
            lastCalibratedAt = channel.dosing.lastCalibratedAt,
            sessionPhase = when (session.state) {
                DeviceDosingCalibrationState.IDLE -> DeviceDosingCalibrationSessionPhase.IDLE
                DeviceDosingCalibrationState.RUNNING -> DeviceDosingCalibrationSessionPhase.RUNNING
                DeviceDosingCalibrationState.PENDING_VERIFICATION ->
                    DeviceDosingCalibrationSessionPhase.PENDING_VERIFICATION
            },
            startedAtUptimeMs = session.startedAtUptimeMs,
            durationMs = session.durationMs,
            measuredMl = session.measuredMl,
            pendingDoseMsPerMl = session.pendingDoseMsPerMl,
            verificationDoseStarted = session.verificationDoseStarted,
            verificationDoseComplete = session.verificationDoseComplete,
            verificationDoseRemainingMs = session.verificationDoseRemainingMs,
            manualActive = channel.valueManual >= 0.0
        )
    }

    private data class CalibrationContext(
        val uid: DeviceUid,
        val slot: DeviceDosingChannelSlot,
        val pumpCount: Int,
        val runtime: DeviceDosingRuntimeRepository
    )
}
