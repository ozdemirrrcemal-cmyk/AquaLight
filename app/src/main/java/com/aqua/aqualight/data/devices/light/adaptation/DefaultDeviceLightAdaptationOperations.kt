package com.aqua.aqualight.data.devices.light.adaptation

import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationFailure
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationMutationResult
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationOperations
import com.aqua.aqualight.application.devices.light.adaptation.DeviceLightAdaptationReadResult
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationPolicy
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAcclimationStopPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatusReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.currentStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.startAcclimation
import com.aqua.aqualight.data.devices.runtime.modules.light.stopAcclimation
import java.util.concurrent.CancellationException
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
            is AdaptationRuntimeResolution.Ready -> resolution.runtime.stateRevision.map {
                val presentation = resolution.runtime.currentStatus(
                    resolution.deviceUid,
                    DeviceLightStatusReadAuthority.PRESENTATION
                )
                val authoritative = resolution.runtime.currentStatus(
                    resolution.deviceUid,
                    DeviceLightStatusReadAuthority.AUTHORITATIVE
                )
                project(
                    resolution.deviceUid,
                    presentation,
                    presentation != null && presentation == authoritative
                )
            }.distinctUntilChanged()
        }
    }

    override fun current(deviceUid: String): DeviceLightAdaptationReadResult =
        when (val resolution = resolve(deviceUid)) {
            is AdaptationRuntimeResolution.Failed -> readFailure(resolution.failure)
            is AdaptationRuntimeResolution.Ready -> project(
                resolution.deviceUid,
                resolution.runtime.currentStatus(resolution.deviceUid),
                firmwareWriteAuthoritative = true
            )
        }

    override suspend fun refresh(deviceUid: String): DeviceLightAdaptationReadResult =
        when (val resolution = resolve(deviceUid)) {
            is AdaptationRuntimeResolution.Failed -> readFailure(resolution.failure)
            is AdaptationRuntimeResolution.Ready -> try {
                when (val refresh = resolution.modules.refreshLightRuntime(resolution.deviceUid)) {
                    is DeviceLightRuntimeRefreshResult.Success -> project(
                        resolution.deviceUid,
                        resolution.runtime.currentStatus(resolution.deviceUid),
                        firmwareWriteAuthoritative = true
                    )
                    is DeviceLightRuntimeRefreshResult.Failed ->
                        readFailure(refresh.outcome.toAdaptationFailure())
                    DeviceLightRuntimeRefreshResult.RejectedStale ->
                        readFailure(DeviceLightAdaptationFailure.UNAVAILABLE)
                    DeviceLightRuntimeRefreshResult.Malformed ->
                        readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                readFailure(DeviceLightAdaptationFailure.INVALID_DATA)
            }
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
                resolution.runtime.currentStatus(resolution.deviceUid),
                firmwareWriteAuthoritative = true
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
        val modules = devicesRepository.runtimeModules()
        return if (uid == null || modules == null) {
            AdaptationRuntimeResolution.Failed(DeviceLightAdaptationFailure.UNAVAILABLE)
        } else {
            AdaptationRuntimeResolution.Ready(uid, modules.light, modules)
        }
    }
}

internal sealed interface AdaptationRuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceLightRuntimeRepository,
        val modules: DeviceRuntimeModuleProvider
    ) : AdaptationRuntimeResolution {
        fun requirePolicy(): DeviceLightAcclimationPolicy =
            requireNotNull(runtime.currentStatus(deviceUid)).policy.acclimation
    }

    data class Failed(val failure: DeviceLightAdaptationFailure) : AdaptationRuntimeResolution
}
