package com.aqua.aqualight.data.devices.cooling.program

import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanOnTemperaturePolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramFanPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramPolicy
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramReadResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSaveResult
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSlot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramSnapshot
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidation
import com.aqua.aqualight.application.devices.cooling.program.CoolingProgramValidationResult
import com.aqua.aqualight.application.devices.cooling.program.DeviceCoolingProgramOperations
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentAuthoritativeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramPolicy
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSlot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSlotPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cool Pro 1F program adapter over the single central Cooling runtime repository.
 *
 * Program reads and writes use the same generation authority as live Cooling state. A save first
 * reads the authoritative device program revision, performs the revision-aware mutation, then
 * requires the central status readback performed by [DeviceCoolingRuntimeRepository.applyProgram]
 * to prove the committed revision before exposing a Saved result.
 */
internal class DefaultDeviceCoolingProgramOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingProgramOperations {

    override suspend fun readProgram(deviceUid: String): CoolingProgramReadResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            ProgramRuntimeResolution.Unsupported -> CoolingProgramReadResult.Unsupported
            ProgramRuntimeResolution.Unavailable -> CoolingProgramReadResult.Unavailable
            is ProgramRuntimeResolution.Ready -> resolved.readProgram()
        }

    override suspend fun saveProgram(
        deviceUid: String,
        slots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult = when (val resolved = resolveRuntime(deviceUid)) {
        ProgramRuntimeResolution.Unsupported -> CoolingProgramSaveResult.Unsupported
        ProgramRuntimeResolution.Unavailable -> CoolingProgramSaveResult.Unavailable
        is ProgramRuntimeResolution.Ready -> resolved.saveProgram(slots)
    }

    private fun resolveRuntime(deviceUid: String): ProgramRuntimeResolution {
        val uid = deviceUid.toCoolingUidOrNull()
        val device = uid?.let(devicesRepository::currentDevice)
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return when {
            uid == null || device == null -> ProgramRuntimeResolution.Unavailable
            !device.isSupportedCoolingV1() -> ProgramRuntimeResolution.Unsupported
            runtime == null -> ProgramRuntimeResolution.Unavailable
            else -> ProgramRuntimeResolution.Ready(uid, runtime)
        }
    }
}

private sealed interface ProgramRuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceCoolingRuntimeRepository
    ) : ProgramRuntimeResolution

    data object Unsupported : ProgramRuntimeResolution
    data object Unavailable : ProgramRuntimeResolution
}

private suspend fun ProgramRuntimeResolution.Ready.readProgram(): CoolingProgramReadResult =
    when (val outcome = runtime.requestProgram(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> {
            if (!runtime.isAuthoritative(deviceUid, outcome.generation)) {
                CoolingProgramReadResult.NotConnected
            } else {
                runCatching(outcome.value::toApplicationSnapshot)
                    .fold(
                        onSuccess = CoolingProgramReadResult::Loaded,
                        onFailure = { CoolingProgramReadResult.Unavailable }
                    )
            }
        }
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> CoolingProgramReadResult.NotConnected
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> CoolingProgramReadResult.Unsupported
        is DeviceRuntimeCommandOutcome.FirmwareError,
        is DeviceRuntimeCommandOutcome.ProtocolError,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> CoolingProgramReadResult.Unavailable
    }

private suspend fun ProgramRuntimeResolution.Ready.saveProgram(
    slots: List<CoolingProgramSlot>
): CoolingProgramSaveResult {
    val currentProgram = when (val outcome = runtime.requestProgram(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> {
            if (!runtime.isAuthoritative(deviceUid, outcome.generation)) {
                return CoolingProgramSaveResult.NotConnected
            }
            outcome.value
        }
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            return CoolingProgramSaveResult.NotConnected
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
            return CoolingProgramSaveResult.Unsupported
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            return CoolingProgramSaveResult.Rejected
        is DeviceRuntimeCommandOutcome.ProtocolError,
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled ->
            return CoolingProgramSaveResult.Unavailable
    }

    val payload = runCatching {
        val policy = currentProgram.toApplicationSnapshot().policy
        require(
            CoolingProgramValidation.validate(slots, policy) == CoolingProgramValidationResult.Valid
        ) { "Cooling program draft violates the authoritative device policy." }
        DeviceCoolingV1ProgramApplyPayload(
            expectedProgramRevision = currentProgram.programRevision,
            slots = slots.map(CoolingProgramSlot::toV1Payload)
        )
    }.getOrElse {
        return CoolingProgramSaveResult.InvalidConfiguration
    }

    return when (val outcome = runtime.applyProgram(deviceUid, payload)) {
        is DeviceRuntimeCommandOutcome.Success -> {
            val authoritativeState = runtime.currentAuthoritativeState(deviceUid)
            val committedRevision = outcome.value.program.programRevision
            if (
                !runtime.isAuthoritative(deviceUid, outcome.generation) ||
                authoritativeState?.connectionGeneration != outcome.generation ||
                authoritativeState.status?.programRevision != committedRevision
            ) {
                CoolingProgramSaveResult.Unavailable
            } else {
                runCatching(outcome.value.program::toApplicationSnapshot).fold(
                    onSuccess = { snapshot ->
                        val requested = slots.sortedBy(CoolingProgramSlot::startMinutes)
                        val committed = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
                        if (committed == requested) {
                            CoolingProgramSaveResult.Saved(snapshot.copy(slots = committed))
                        } else {
                            CoolingProgramSaveResult.Unavailable
                        }
                    },
                    onFailure = { CoolingProgramSaveResult.InvalidConfiguration }
                )
            }
        }
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> CoolingProgramSaveResult.NotConnected
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> CoolingProgramSaveResult.Unsupported
        is DeviceRuntimeCommandOutcome.FirmwareError -> CoolingProgramSaveResult.Rejected
        is DeviceRuntimeCommandOutcome.ProtocolError -> CoolingProgramSaveResult.InvalidConfiguration
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> CoolingProgramSaveResult.Unavailable
    }
}

private fun DeviceCoolingV1ProgramSnapshot.toApplicationSnapshot(): CoolingProgramSnapshot {
    val applicationPolicy = policy.toApplicationPolicy()
    val applicationSlots = slots.map(DeviceCoolingV1ProgramSlot::toApplicationSlot)
    require(
        CoolingProgramValidation.validate(applicationSlots, applicationPolicy) ==
            CoolingProgramValidationResult.Valid
    ) { "Firmware returned a Cooling program outside the reported policy." }
    return CoolingProgramSnapshot(
        slots = applicationSlots,
        policy = applicationPolicy
    )
}

private fun DeviceCoolingV1ProgramPolicy.toApplicationPolicy(): CoolingProgramPolicy {
    val minimumFanPercent = fan.minimumPercent.toExactPercentInt()
    val maximumFanPercent = fan.maximumPercent.toExactPercentInt()
    val fanPercentStep = fan.stepPercent.toExactPercentInt()
    return CoolingProgramPolicy(
        maximumSlotCount = maximumSlotCount,
        minimumSlotDurationMinutes = minimumDurationMinutes,
        fan = CoolingProgramFanPolicy(
            minimumPercent = minimumFanPercent,
            maximumPercent = maximumFanPercent,
            stepPercent = fanPercentStep
        ),
        fanOnTemperature = CoolingProgramFanOnTemperaturePolicy(
            minimumC = fanOnTemperature.minimumC,
            maximumC = fanOnTemperature.maximumC,
            stepC = fanOnTemperature.stepC,
            defaultC = fanOnTemperature.defaultC
        )
    )
}

private fun DeviceCoolingV1ProgramSlot.toApplicationSlot(): CoolingProgramSlot =
    CoolingProgramSlot(
        startMinutes = startMinute,
        endMinutes = endMinute,
        fanOnTemperatureC = fanOnTemperatureC,
        targetFanPercent = fanPercent.toExactPercentInt()
    )

private fun CoolingProgramSlot.toV1Payload(): DeviceCoolingV1ProgramSlotPayload =
    DeviceCoolingV1ProgramSlotPayload(
        startMinute = startMinutes,
        endMinute = endMinutes,
        fanOnTemperatureC = fanOnTemperatureC,
        fanPercent = targetFanPercent.toDouble()
    )

private fun Double.toExactPercentInt(): Int {
    require(isFinite())
    val rounded = roundToInt()
    require(rounded in 0..100)
    require(abs(this - rounded.toDouble()) <= DeviceCoolingV1Contract.Limit.ALIGNMENT_EPSILON)
    return rounded
}

private fun DeviceSnapshot.isSupportedCoolingV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun String.toCoolingUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)
