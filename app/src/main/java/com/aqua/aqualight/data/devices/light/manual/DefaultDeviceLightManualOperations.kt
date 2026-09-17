package com.aqua.aqualight.data.devices.light.manual

import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualFailure
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualMutationResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualOperations
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualReadResult
import com.aqua.aqualight.application.devices.light.manual.DeviceLightManualScene
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightManualSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stateless projection and serialized mutation adapter over the central Light runtime owner. */
internal class DefaultDeviceLightManualOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightManualOperations {

    private val mutationMutex = Mutex()

    override fun observe(deviceUid: String): Flow<DeviceLightManualReadResult> {
        val uid = deviceUid.toUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.light }
        return if (uid == null) {
            flowOf(readFailure(DeviceLightManualFailure.INVALID_DATA))
        } else if (runtime == null) {
            flowOf(readFailure(DeviceLightManualFailure.UNAVAILABLE))
        } else {
            runtime.stateRevision.map {
                runtime.currentStatus(uid)
                    ?.toManualSnapshot(uid)
                    ?.let(DeviceLightManualReadResult::Available)
                    ?: readFailure(DeviceLightManualFailure.NOT_CONNECTED)
            }.distinctUntilChanged()
        }
    }

    override suspend fun setScene(
        deviceUid: String,
        scene: DeviceLightManualScene
    ): DeviceLightManualMutationResult = mutate(deviceUid) { uid, runtime, status ->
        val requestedScene = scene.toRuntimeScene(status.product)
        runtime.setManual(uid, DeviceLightManualSetPayload(requestedScene))
            .confirmManualMutation(uid, runtime, requestedScene)
    }

    override suspend fun turnOff(
        deviceUid: String
    ): DeviceLightManualMutationResult = mutate(deviceUid) { uid, runtime, _ ->
        when (val outcome = runtime.manualOff(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> outcome.confirmManualMutation(
                uid = uid,
                runtime = runtime,
                expectedScene = outcome.value.scene
            )
            else -> manualMutationFailure(outcome.toManualFailure())
        }
    }

    private suspend fun mutate(
        deviceUid: String,
        command: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository,
            DeviceLightStatus
        ) -> DeviceLightManualMutationResult
    ): DeviceLightManualMutationResult = mutationMutex.withLock {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentStatus(uid)
        when {
            uid == null -> manualMutationFailure(DeviceLightManualFailure.INVALID_DATA)
            runtime == null -> manualMutationFailure(DeviceLightManualFailure.UNAVAILABLE)
            status == null -> manualMutationFailure(DeviceLightManualFailure.NOT_CONNECTED)
            else -> try {
                command(uid, runtime, status)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                manualMutationFailure(DeviceLightManualFailure.INVALID_DATA)
            }
        }
    }
}

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private fun readFailure(failure: DeviceLightManualFailure) =
    DeviceLightManualReadResult.Failed(failure)
