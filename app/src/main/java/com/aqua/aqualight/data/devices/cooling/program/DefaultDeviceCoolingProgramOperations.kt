package com.aqua.aqualight.data.devices.cooling.program

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
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
import com.aqua.aqualight.data.devices.cooling.v1.DeviceCoolingV1FailureMapper
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
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ProgramApplyResult
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
    ) : ProgramRuntimeResolution {

        suspend fun readProgram(): CoolingProgramReadResult =
            when (val outcome = runtime.requestProgram(deviceUid)) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    if (!runtime.isAuthoritative(deviceUid, outcome.generation)) {
                        CoolingProgramReadResult.NotConnected
                    } else {
                        runCatching(outcome.value::toApplicationSnapshot).fold(
                            onSuccess = CoolingProgramReadResult::Loaded,
                            onFailure = {
                                CoolingProgramReadResult.Rejected(
                                    DeviceCoolingCommandFailure.PROTOCOL_ERROR
                                )
                            }
                        )
                    }
                }
                is DeviceRuntimeCommandOutcome.NotConnected,
                is DeviceRuntimeCommandOutcome.NotAuthenticated ->
                    CoolingProgramReadResult.NotConnected
                is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                    CoolingProgramReadResult.Unsupported
                is DeviceRuntimeCommandOutcome.FirmwareError ->
                    CoolingProgramReadResult.Rejected(DeviceCoolingV1FailureMapper.map(outcome))
                is DeviceRuntimeCommandOutcome.ProtocolError ->
                    CoolingProgramReadResult.Rejected(DeviceCoolingCommandFailure.PROTOCOL_ERROR)
                is DeviceRuntimeCommandOutcome.SendFailed,
                is DeviceRuntimeCommandOutcome.Timeout,
                is DeviceRuntimeCommandOutcome.Cancelled -> CoolingProgramReadResult.Unavailable
            }

        suspend fun saveProgram(slots: List<CoolingProgramSlot>): CoolingProgramSaveResult =
            when (val preflight = requestSavePreflight()) {
                is ProgramSavePreflight.Failed -> preflight.result
                is ProgramSavePreflight.Ready -> applyProgram(preflight.program, slots)
            }

        private suspend fun requestSavePreflight(): ProgramSavePreflight =
            when (val outcome = runtime.requestProgram(deviceUid)) {
                is DeviceRuntimeCommandOutcome.Success -> if (
                    runtime.isAuthoritative(deviceUid, outcome.generation)
                ) {
                    ProgramSavePreflight.Ready(outcome.value)
                } else {
                    ProgramSavePreflight.Failed(CoolingProgramSaveResult.NotConnected)
                }
                is DeviceRuntimeCommandOutcome.NotConnected,
                is DeviceRuntimeCommandOutcome.NotAuthenticated ->
                    ProgramSavePreflight.Failed(CoolingProgramSaveResult.NotConnected)
                is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                    ProgramSavePreflight.Failed(CoolingProgramSaveResult.Unsupported)
                is DeviceRuntimeCommandOutcome.FirmwareError -> ProgramSavePreflight.Failed(
                    CoolingProgramSaveResult.Rejected(DeviceCoolingV1FailureMapper.map(outcome))
                )
                is DeviceRuntimeCommandOutcome.ProtocolError -> ProgramSavePreflight.Failed(
                    CoolingProgramSaveResult.Rejected(DeviceCoolingCommandFailure.PROTOCOL_ERROR)
                )
                is DeviceRuntimeCommandOutcome.SendFailed,
                is DeviceRuntimeCommandOutcome.Timeout,
                is DeviceRuntimeCommandOutcome.Cancelled ->
                    ProgramSavePreflight.Failed(CoolingProgramSaveResult.Unavailable)
            }

        private suspend fun applyProgram(
            currentProgram: DeviceCoolingV1ProgramSnapshot,
            slots: List<CoolingProgramSlot>
        ): CoolingProgramSaveResult {
            val payload = currentProgram.toApplyPayloadOrNull(slots)
            return payload?.let { validPayload ->
                ProgramSaveResultProjector.project(
                    outcome = runtime.applyProgram(deviceUid, validPayload),
                    runtime = runtime,
                    deviceUid = deviceUid,
                    requestedSlots = slots
                )
            } ?: CoolingProgramSaveResult.InvalidConfiguration
        }
    }

    data object Unsupported : ProgramRuntimeResolution
    data object Unavailable : ProgramRuntimeResolution
}

private sealed interface ProgramSavePreflight {
    data class Ready(val program: DeviceCoolingV1ProgramSnapshot) : ProgramSavePreflight
    data class Failed(val result: CoolingProgramSaveResult) : ProgramSavePreflight
}

private object ProgramSaveResultProjector {
    fun project(
        outcome: DeviceRuntimeCommandOutcome<DeviceCoolingV1ProgramApplyResult>,
        runtime: DeviceCoolingRuntimeRepository,
        deviceUid: DeviceUid,
        requestedSlots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult = when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> committedResult(
            outcome = outcome,
            runtime = runtime,
            deviceUid = deviceUid,
            requestedSlots = requestedSlots
        )
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> CoolingProgramSaveResult.NotConnected
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> CoolingProgramSaveResult.Unsupported
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            CoolingProgramSaveResult.Rejected(DeviceCoolingV1FailureMapper.map(outcome))
        is DeviceRuntimeCommandOutcome.ProtocolError ->
            CoolingProgramSaveResult.Rejected(DeviceCoolingCommandFailure.PROTOCOL_ERROR)
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> CoolingProgramSaveResult.Unavailable
    }

    private fun committedResult(
        outcome: DeviceRuntimeCommandOutcome.Success<DeviceCoolingV1ProgramApplyResult>,
        runtime: DeviceCoolingRuntimeRepository,
        deviceUid: DeviceUid,
        requestedSlots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult {
        val authoritativeState = runtime.currentAuthoritativeState(deviceUid)
        val committedRevision = outcome.value.program.programRevision
        val committedByAuthority = runtime.isAuthoritative(deviceUid, outcome.generation) &&
            authoritativeState?.connectionGeneration == outcome.generation &&
            authoritativeState.status?.programRevision == committedRevision
        return if (committedByAuthority) {
            committedSnapshotResult(outcome.value.program, requestedSlots)
        } else {
            CoolingProgramSaveResult.Unavailable
        }
    }

    private fun committedSnapshotResult(
        program: DeviceCoolingV1ProgramSnapshot,
        requestedSlots: List<CoolingProgramSlot>
    ): CoolingProgramSaveResult = runCatching(program::toApplicationSnapshot).fold(
        onSuccess = { snapshot ->
            val requested = requestedSlots.sortedBy(CoolingProgramSlot::startMinutes)
            val committed = snapshot.slots.sortedBy(CoolingProgramSlot::startMinutes)
            if (committed == requested) {
                CoolingProgramSaveResult.Saved(snapshot.copy(slots = committed))
            } else {
                CoolingProgramSaveResult.Rejected(DeviceCoolingCommandFailure.PROTOCOL_ERROR)
            }
        },
        onFailure = {
            CoolingProgramSaveResult.Rejected(DeviceCoolingCommandFailure.PROTOCOL_ERROR)
        }
    )
}

private fun DeviceCoolingV1ProgramSnapshot.toApplyPayloadOrNull(
    slots: List<CoolingProgramSlot>
): DeviceCoolingV1ProgramApplyPayload? = runCatching {
    val applicationPolicy = toApplicationSnapshot().policy
    require(
        CoolingProgramValidation.validate(slots, applicationPolicy) ==
            CoolingProgramValidationResult.Valid
    ) { "Cooling program draft violates the authoritative device policy." }
    DeviceCoolingV1ProgramApplyPayload(
        expectedProgramRevision = programRevision,
        slots = slots.map(CoolingProgramSlot::toV1Payload)
    )
}.getOrNull()

private fun DeviceCoolingV1ProgramSnapshot.toApplicationSnapshot(): CoolingProgramSnapshot {
    val applicationPolicy = policy.toApplicationPolicy()
    val applicationSlots = slots.map(DeviceCoolingV1ProgramSlot::toApplicationSlot)
    require(
        CoolingProgramValidation.validate(applicationSlots, applicationPolicy) ==
            CoolingProgramValidationResult.Valid
    ) { "Firmware returned a Cooling program outside the reported policy." }
    return CoolingProgramSnapshot(
        slots = applicationSlots,
        policy = applicationPolicy,
        clockReady = clockReady,
        currentMinuteOfDay = currentMinuteOfDay,
        activeSlotIndex = activeSlotIndex
    )
}

private fun DeviceCoolingV1ProgramPolicy.toApplicationPolicy(): CoolingProgramPolicy {
    val minimumFanPercent = fan.minimumPercent.toExactPercentInt()
    val maximumFanPercent = fan.maximumPercent.toExactPercentInt()
    val fanPercentStep = fan.stepPercent.toExactPercentInt()
    return CoolingProgramPolicy(
        maximumSlotCount = maximumSlotCount,
        timeStepMinutes = timeStepMinutes,
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
    val minimum = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM.toInt()
    val maximum = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM.toInt()
    require(rounded in minimum..maximum)
    require(abs(this - rounded.toDouble()) <= DeviceCoolingV1Contract.Limit.ALIGNMENT_EPSILON)
    return rounded
}

private fun DeviceSnapshot.isSupportedCoolingV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun String.toCoolingUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)
