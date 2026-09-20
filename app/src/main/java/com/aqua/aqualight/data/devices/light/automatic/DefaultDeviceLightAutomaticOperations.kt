package com.aqua.aqualight.data.devices.light.automatic

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticPolicy
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgramDraft
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramCreatePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramDeletePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramEnabledSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramUpdatePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutomaticReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutomaticRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightErrorReason
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.createAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.currentAutomatic
import com.aqua.aqualight.data.devices.runtime.modules.light.deleteAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.setAutoProgramEnabled
import com.aqua.aqualight.data.devices.runtime.modules.light.updateAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.lightV1Data
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceLightAutomaticOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightAutomaticOperations {

    override fun observe(deviceUid: String): Flow<DeviceLightAutomaticReadResult> {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return if (uid == null || runtime == null) {
            flowOf(DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.UNAVAILABLE))
        } else {
            runtime.stateRevision.map {
                projectCurrent(
                    devicesRepository,
                    uid,
                    runtime,
                    DeviceLightAutomaticReadAuthority.PRESENTATION
                )
            }.distinctUntilChanged()
        }
    }

    override fun current(deviceUid: String): DeviceLightAutomaticReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return if (uid == null || runtime == null) {
            DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.UNAVAILABLE)
        } else {
            projectCurrent(
                devicesRepository,
                uid,
                runtime,
                DeviceLightAutomaticReadAuthority.AUTHORITATIVE
            )
        }
    }

    override suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult {
        val uid = deviceUid.toUidOrNull()
        val modules = devicesRepository.runtimeModules()
        val runtime = modules?.light
        return when {
            uid == null -> DeviceLightAutomaticReadResult.Failed(
                DeviceLightAutomaticFailure.INVALID_DATA
            )
            modules == null || runtime == null -> DeviceLightAutomaticReadResult.Failed(
                DeviceLightAutomaticFailure.UNAVAILABLE
            )
            else -> try {
                when (val refresh = modules.refreshLightRuntime(uid)) {
                    is DeviceLightRuntimeRefreshResult.Success -> projectCurrent(
                        devicesRepository,
                        uid,
                        runtime,
                        DeviceLightAutomaticReadAuthority.AUTHORITATIVE
                    )
                    is DeviceLightRuntimeRefreshResult.Failed ->
                        DeviceLightAutomaticReadResult.Failed(refresh.outcome.toFailure())
                    DeviceLightRuntimeRefreshResult.RejectedStale ->
                        DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.UNAVAILABLE)
                    DeviceLightRuntimeRefreshResult.Malformed ->
                        DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.INVALID_DATA)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.INVALID_DATA)
            }
        }
    }

    override suspend fun create(
        deviceUid: String,
        expectedRevision: Long,
        enabled: Boolean,
        draft: DeviceLightAutomaticProgramDraft
    ): DeviceLightAutomaticMutationResult = programCommand(deviceUid) { uid, runtime, status ->
        runtime.createAutoProgram(
            uid,
            DeviceLightAutoProgramCreatePayload(
                expectedRevision = expectedRevision,
                enabled = enabled,
                weekdaysMask = draft.weekdaysMask,
                startTimeMs = draft.startTimeMs,
                endTimeMs = draft.endTimeMs,
                rampDurationMs = draft.rampDurationMs,
                scene = draft.scene.toRuntimeScene(status)
            )
        )
    }

    override suspend fun update(
        deviceUid: String,
        expectedRevision: Long,
        programId: String,
        draft: DeviceLightAutomaticProgramDraft
    ): DeviceLightAutomaticMutationResult = programCommand(deviceUid) { uid, runtime, status ->
        runtime.updateAutoProgram(
            uid,
            DeviceLightAutoProgramUpdatePayload(
                expectedRevision = expectedRevision,
                programId = programId,
                weekdaysMask = draft.weekdaysMask,
                startTimeMs = draft.startTimeMs,
                endTimeMs = draft.endTimeMs,
                rampDurationMs = draft.rampDurationMs,
                scene = draft.scene.toRuntimeScene(status)
            )
        )
    }

    override suspend fun setEnabled(
        deviceUid: String,
        expectedRevision: Long,
        programId: String,
        enabled: Boolean
    ): DeviceLightAutomaticMutationResult = command(deviceUid) { uid, runtime ->
        runtime.setAutoProgramEnabled(
            uid,
            DeviceLightAutoProgramEnabledSetPayload(
                expectedRevision = expectedRevision,
                programId = programId,
                enabled = enabled
            )
        )
    }

    override suspend fun delete(
        deviceUid: String,
        expectedRevision: Long,
        programId: String
    ): DeviceLightAutomaticMutationResult = command(deviceUid) { uid, runtime ->
        runtime.deleteAutoProgram(
            uid,
            DeviceLightAutoProgramDeletePayload(
                expectedRevision = expectedRevision,
                programId = programId
            )
        )
    }

    private suspend fun command(
        deviceUid: String,
        execute: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository
        ) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceLightAutomaticMutationResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return when {
            uid == null -> DeviceLightAutomaticMutationResult.Failed(
                DeviceLightAutomaticFailure.INVALID_DATA
            )
            runtime == null -> DeviceLightAutomaticMutationResult.Failed(
                DeviceLightAutomaticFailure.UNAVAILABLE
            )
            runtime.currentAutomatic(
                uid,
                DeviceLightAutomaticReadAuthority.AUTHORITATIVE
            ) == null -> mutationFailure(DeviceLightAutomaticFailure.NOT_CONNECTED)
            else -> executeSafely(uid, runtime, execute)
        }
    }

    private suspend fun programCommand(
        deviceUid: String,
        execute: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository,
            DeviceLightStatus
        ) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceLightAutomaticMutationResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentAutomatic(
            uid,
            DeviceLightAutomaticReadAuthority.AUTHORITATIVE
        )?.status
        return when {
            uid == null -> mutationFailure(DeviceLightAutomaticFailure.INVALID_DATA)
            runtime == null -> mutationFailure(DeviceLightAutomaticFailure.UNAVAILABLE)
            status == null -> mutationFailure(DeviceLightAutomaticFailure.NOT_CONNECTED)
            else -> executeSafely(uid, runtime) { resolvedUid, resolvedRuntime ->
                execute(resolvedUid, resolvedRuntime, status)
            }
        }
    }

    private suspend fun executeSafely(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository,
        execute: suspend (
            DeviceUid,
            DeviceLightRuntimeRepository
        ) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceLightAutomaticMutationResult = try {
        when (val result = execute(uid, runtime)) {
            is DeviceRuntimeCommandOutcome.Success -> when (
                val refresh = devicesRepository.runtimeModules()?.refreshLightRuntime(uid)
            ) {
                is DeviceLightRuntimeRefreshResult.Success ->
                    DeviceLightAutomaticMutationResult.Success
                is DeviceLightRuntimeRefreshResult.Failed ->
                    DeviceLightAutomaticMutationResult.Failed(refresh.outcome.toFailure())
                DeviceLightRuntimeRefreshResult.RejectedStale ->
                    DeviceLightAutomaticMutationResult.Failed(
                        DeviceLightAutomaticFailure.UNAVAILABLE
                    )
                DeviceLightRuntimeRefreshResult.Malformed,
                null -> DeviceLightAutomaticMutationResult.Failed(
                    DeviceLightAutomaticFailure.INVALID_DATA
                )
            }
            else -> DeviceLightAutomaticMutationResult.Failed(result.toFailure())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DeviceLightAutomaticMutationResult.Failed(DeviceLightAutomaticFailure.UNAVAILABLE)
    }
}

private fun projectCurrent(
    devicesRepository: DevicesRepository,
    uid: DeviceUid,
    runtime: DeviceLightRuntimeRepository,
    authority: DeviceLightAutomaticReadAuthority
): DeviceLightAutomaticReadResult {
    val frame = runtime.currentAutomatic(uid, authority)
        ?: return DeviceLightAutomaticReadResult.Failed(
            DeviceLightAutomaticFailure.NOT_CONNECTED
        )
    val writeAuthoritative = runtime.currentAutomatic(
        uid,
        DeviceLightAutomaticReadAuthority.AUTHORITATIVE
    ) == frame
    return runCatching {
        DeviceLightAutomaticReadResult.Available(
            frame.toApplicationSnapshot(
                uid = uid,
                productDisplayName = devicesRepository.currentDevice(uid)
                    ?.product
                    ?.displayName
                    .orEmpty(),
                firmwareWriteAuthoritative = writeAuthoritative
            )
        )
    }.getOrElse {
        DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.INVALID_DATA)
    }
}

private fun DeviceLightAutomaticRuntimeState.toApplicationSnapshot(
    uid: DeviceUid,
    productDisplayName: String,
    firmwareWriteAuthoritative: Boolean
): DeviceLightAutomaticSnapshot {
    val lightStatus = status
    val programsDocument = programs
    require(programsDocument.programCount == programsDocument.programs.size)
    require(
        programsDocument.enabledCount == programsDocument.programs.count(DeviceLightAutoProgram::enabled)
    )
    require(programsDocument.capacity == lightStatus.policy.auto.capacity)
    val channels = lightStatus.product.sceneFields.map(String::toAutomaticChannel)
    return DeviceLightAutomaticSnapshot(
        deviceUid = uid.value,
        productDisplayName = productDisplayName.ifBlank { lightStatus.product.wireValue },
        revision = programsDocument.revision,
        policy = DeviceLightAutomaticPolicy(
            capacity = lightStatus.policy.auto.capacity,
            timeStepMs = lightStatus.policy.auto.timeStepMs,
            rampDurationsMs = lightStatus.policy.auto.rampDurationsMs
        ),
        channels = channels,
        programs = programsDocument.programs.map { program ->
            program.toApplicationProgram(lightStatus, channels)
        },
        firmwareWriteAuthoritative = firmwareWriteAuthoritative
    )
}

private fun DeviceLightAutomaticScene.toRuntimeScene(status: DeviceLightStatus): DeviceLightScene =
    DeviceLightScene(
        product = status.product,
        percents = status.product.sceneFields.associateWith { field ->
            channels.getValue(field.toAutomaticChannel())
        }
    )

private fun DeviceLightAutoProgram.toApplicationProgram(
    status: DeviceLightStatus,
    channels: List<DeviceLightAutomaticChannel>
): DeviceLightAutomaticProgram {
    require(scene.product == status.product)
    return DeviceLightAutomaticProgram(
        programId = programId,
        enabled = enabled,
        weekdaysMask = weekdaysMask,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        rampDurationMs = rampDurationMs,
        scene = DeviceLightAutomaticScene(
            channels.associateWith { channel -> scene.percents.getValue(channel.sceneKey) }
        )
    )
}

private fun String.toAutomaticChannel(): DeviceLightAutomaticChannel =
    requireNotNull(DeviceLightAutomaticChannel.entries.singleOrNull { channel ->
        channel.sceneKey == this
    })

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private fun mutationFailure(
    failure: DeviceLightAutomaticFailure
): DeviceLightAutomaticMutationResult = DeviceLightAutomaticMutationResult.Failed(failure)

private fun DeviceRuntimeCommandOutcome<*>.toFailure(): DeviceLightAutomaticFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightAutomaticFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightAutomaticFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> toAutomaticFailure()
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightAutomaticFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightAutomaticFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}

private fun DeviceRuntimeCommandOutcome.FirmwareError.toAutomaticFailure():
    DeviceLightAutomaticFailure {
    val reason = runCatching { lightV1Data().reason }.getOrNull()
    return when (reason) {
        DeviceLightErrorReason.Known.STALE_REVISION ->
            DeviceLightAutomaticFailure.STALE_REVISION
        DeviceLightErrorReason.Known.AUTO_CAPACITY_REACHED ->
            DeviceLightAutomaticFailure.CAPACITY_REACHED
        DeviceLightErrorReason.Known.AUTO_PROGRAM_OVERLAP ->
            DeviceLightAutomaticFailure.OVERLAP
        DeviceLightErrorReason.Known.AUTO_PROGRAM_NOT_FOUND ->
            DeviceLightAutomaticFailure.NOT_FOUND
        else -> DeviceLightAutomaticFailure.REJECTED
    }
}
