package com.aqua.aqualight.data.devices.light.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationPolicy
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationSnapshot
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationState
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationPolicy
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStopPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data
import com.aqua.aqualight.data.devices.runtime.modules.light.requestAcclimationStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.startAcclimation
import com.aqua.aqualight.data.devices.runtime.modules.light.stopAcclimation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Owner-scoped adapter over the single authoritative Light runtime state owner. */
internal class DefaultDeviceLightAdaptationOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightAdaptationOperations {

    override fun observe(deviceUid: String): Flow<DeviceLightAdaptationReadResult> {
        val resolution = resolve(deviceUid)
        return when (resolution) {
            is AdaptationRuntimeResolution.Failed -> flowOf(readFailure(resolution.failure))
            is AdaptationRuntimeResolution.Ready -> resolution.runtime.states.map {
                project(
                    resolution.deviceUid,
                    resolution.runtime.currentStatus(resolution.deviceUid)
                )
            }.distinctUntilChanged()
        }
    }

    override fun current(deviceUid: String): DeviceLightAdaptationReadResult =
        when (val resolution = resolve(deviceUid)) {
            is AdaptationRuntimeResolution.Failed -> readFailure(resolution.failure)
            is AdaptationRuntimeResolution.Ready -> project(
                resolution.deviceUid,
                resolution.runtime.currentStatus(resolution.deviceUid)
            )
        }

    override suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult =
        when (val resolution = resolve(deviceUid)) {
            is AdaptationRuntimeResolution.Failed -> readFailure(resolution.failure)
            is AdaptationRuntimeResolution.Ready -> runCatching {
                resolution.runtime.requestAcclimationStatus(resolution.deviceUid)
            }.fold(
                onSuccess = { outcome -> outcome.toReadResult(resolution) },
                onFailure = { readFailure(DeviceLightAdaptationFailure.INVALID_DATA) }
            )
        }

    override suspend fun start(
        deviceUid: String,
        expectedRevision: Long,
        startPercent: Int,
        durationDays: Int
    ): DeviceLightAdaptationMutationResult = mutate(deviceUid) { resolution ->
        val policy = resolution.requirePolicy()
        if (!policy.accepts(startPercent, durationDays)) {
            return@mutate mutationFailure(DeviceLightAdaptationFailure.INVALID_REQUEST)
        }
        resolution.runtime.startAcclimation(
            resolution.deviceUid,
            DeviceLightAcclimationStartPayload(expectedRevision, startPercent, durationDays)
        ).toMutationResult(resolution)
    }

    override suspend fun stop(
        deviceUid: String,
        expectedRevision: Long
    ): DeviceLightAdaptationMutationResult = mutate(deviceUid) { resolution ->
        resolution.runtime.stopAcclimation(
            resolution.deviceUid,
            DeviceLightAcclimationStopPayload(expectedRevision)
        ).toMutationResult(resolution)
    }

    private suspend fun mutate(
        deviceUid: String,
        block: suspend (AdaptationRuntimeResolution.Ready) -> DeviceLightAdaptationMutationResult
    ): DeviceLightAdaptationMutationResult = when (val resolution = resolve(deviceUid)) {
        is AdaptationRuntimeResolution.Failed -> mutationFailure(resolution.failure)
        is AdaptationRuntimeResolution.Ready -> when (
            val current = project(
                resolution.deviceUid,
                resolution.runtime.currentStatus(resolution.deviceUid)
            )
        ) {
            is DeviceLightAdaptationReadResult.Failed -> mutationFailure(current.failure)
            is DeviceLightAdaptationReadResult.Available -> runCatching {
                block(resolution)
            }.getOrElse {
                mutationFailure(DeviceLightAdaptationFailure.INVALID_DATA)
            }
        }
    }

    private fun resolve(deviceUid: String): AdaptationRuntimeResolution {
        val uid = deviceUid.trim().takeIf(String::isNotBlank)?.let(::DeviceUid)
            ?: return AdaptationRuntimeResolution.Failed(DeviceLightAdaptationFailure.UNAVAILABLE)
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return AdaptationRuntimeResolution.Failed(DeviceLightAdaptationFailure.UNAVAILABLE)
        return AdaptationRuntimeResolution.Ready(uid, runtime)
    }
}

private sealed interface AdaptationRuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceLightRuntimeRepository
    ) : AdaptationRuntimeResolution {
        fun requirePolicy(): DeviceLightAcclimationPolicy =
            requireNotNull(runtime.currentStatus(deviceUid)).policy.acclimation
    }

    data class Failed(val failure: DeviceLightAdaptationFailure) : AdaptationRuntimeResolution
}

private fun project(
    deviceUid: DeviceUid,
    status: DeviceLightStatus?
): DeviceLightAdaptationReadResult = when {
    status == null -> readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    !status.supportsAdaptation() -> readFailure(DeviceLightAdaptationFailure.UNSUPPORTED)
    else -> status.acclimation.toSnapshot(deviceUid, status.policy.acclimation)
        ?.let(DeviceLightAdaptationReadResult::Available)
        ?: readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
}

private fun DeviceLightStatus.supportsAdaptation(): Boolean =
    product == DeviceLightProduct.WRGB_PRO_ELITE &&
        features.acclimation &&
        acclimation.supported &&
        policy.acclimation.supported

private fun DeviceLightAcclimationPolicy.accepts(startPercent: Int, durationDays: Int): Boolean {
    val startMin = startPercentMin ?: return false
    val startMax = startPercentMax ?: return false
    val startStep = startPercentStep ?: return false
    val durationMin = durationDaysMin ?: return false
    val durationMax = durationDaysMax ?: return false
    val durationStep = durationDaysStep ?: return false
    return startPercent in startMin..startMax &&
        (startPercent - startMin) % startStep == 0 &&
        durationDays in durationMin..durationMax &&
        (durationDays - durationMin) % durationStep == 0
}

private fun DeviceLightAcclimationStatus.toSnapshot(
    deviceUid: DeviceUid,
    policy: DeviceLightAcclimationPolicy
): DeviceLightAdaptationSnapshot? {
    val applicationPolicy = policy.toApplicationPolicy() ?: return null
    return DeviceLightAdaptationSnapshot(
        deviceUid = deviceUid.value,
        revision = revision ?: return null,
        state = state?.toApplicationState() ?: return null,
        clockReady = clockReady ?: return null,
        startPercent = startPercent ?: applicationPolicy.defaultStartPercent,
        currentPermille = currentPermille,
        targetPercent = targetPercent ?: applicationPolicy.targetPercent,
        durationDays = durationDays ?: applicationPolicy.defaultDurationDays,
        startedAtEpochSeconds = startedAtEpochSeconds,
        endsAtEpochSeconds = endsAtEpochSeconds,
        remainingSeconds = remainingSeconds,
        policy = applicationPolicy
    )
}

private fun DeviceLightAcclimationPolicy.toApplicationPolicy(): DeviceLightAdaptationPolicy? {
    return DeviceLightAdaptationPolicy(
        startPercentMin = startPercentMin ?: return null,
        startPercentMax = startPercentMax ?: return null,
        startPercentStep = startPercentStep ?: return null,
        defaultStartPercent = defaultStartPercent ?: return null,
        durationDaysMin = durationDaysMin ?: return null,
        durationDaysMax = durationDaysMax ?: return null,
        durationDaysStep = durationDaysStep ?: return null,
        defaultDurationDays = defaultDurationDays ?: return null,
        targetPercent = targetPercent ?: return null
    )
}

private fun DeviceLightAcclimationState.toApplicationState(): DeviceLightAdaptationState = when (this) {
    DeviceLightAcclimationState.DISABLED -> DeviceLightAdaptationState.DISABLED
    DeviceLightAcclimationState.ACTIVE -> DeviceLightAdaptationState.ACTIVE
    DeviceLightAcclimationState.COMPLETED -> DeviceLightAdaptationState.COMPLETED
}

private fun DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus>.toReadResult(
    resolution: AdaptationRuntimeResolution.Ready
): DeviceLightAdaptationReadResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        resolution.runtime.isAuthoritative(resolution.deviceUid, generation)
    ) {
        value.toSnapshot(resolution.deviceUid, resolution.requirePolicy())
            ?.let(DeviceLightAdaptationReadResult::Available)
            ?: readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
    } else {
        readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    }
    else -> readFailure(toAdaptationFailure())
}

private fun DeviceRuntimeCommandOutcome<DeviceLightAcclimationStatus>.toMutationResult(
    resolution: AdaptationRuntimeResolution.Ready
): DeviceLightAdaptationMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> if (
        resolution.runtime.isAuthoritative(resolution.deviceUid, generation)
    ) {
        value.toSnapshot(resolution.deviceUid, resolution.requirePolicy())
            ?.let(DeviceLightAdaptationMutationResult::Success)
            ?: mutationFailure(DeviceLightAdaptationFailure.INVALID_DATA)
    } else {
        mutationFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
    }
    else -> mutationFailure(toAdaptationFailure())
}

private fun DeviceRuntimeCommandOutcome<*>.toAdaptationFailure(): DeviceLightAdaptationFailure =
    when (this) {
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            DeviceLightAdaptationFailure.NOT_CONNECTED
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
            DeviceLightAdaptationFailure.UNSUPPORTED
        is DeviceRuntimeCommandOutcome.FirmwareError -> toFirmwareFailure()
        is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightAdaptationFailure.INVALID_DATA
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightAdaptationFailure.UNAVAILABLE
        is DeviceRuntimeCommandOutcome.Success<*> -> error("Success has no failure mapping.")
    }

private fun DeviceRuntimeCommandOutcome.FirmwareError.toFirmwareFailure(): DeviceLightAdaptationFailure =
    runCatching { lightV1Data().reason }.getOrNull().let { reason ->
        when (reason) {
            DeviceLightErrorReason.STALE_REVISION -> DeviceLightAdaptationFailure.STALE_REVISION
            DeviceLightErrorReason.RTC_NOT_READY -> DeviceLightAdaptationFailure.CLOCK_NOT_READY
            DeviceLightErrorReason.ACCLIMATION_START_PERCENT,
            DeviceLightErrorReason.ACCLIMATION_DURATION ->
                DeviceLightAdaptationFailure.INVALID_REQUEST
            else -> DeviceLightAdaptationFailure.REJECTED
        }
    }

private fun readFailure(failure: DeviceLightAdaptationFailure) =
    DeviceLightAdaptationReadResult.Failed(failure)

private fun mutationFailure(failure: DeviceLightAdaptationFailure) =
    DeviceLightAdaptationMutationResult.Failed(failure)
