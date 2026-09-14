package com.aqua.aqualight.data.devices.light.automatic

import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticChannel
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticFailure
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticMutationResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticOperations
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticProgram
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticReadResult
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticScene
import com.aqua.aqualight.application.devices.light.automatic.DeviceLightAutomaticSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramDeletePayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoProgramEnabledSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightAutoPrograms
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.deleteAutoProgram
import com.aqua.aqualight.data.devices.runtime.modules.light.requestAutoPrograms
import com.aqua.aqualight.data.devices.runtime.modules.light.setAutoProgramEnabled
import java.util.concurrent.CancellationException

internal class DefaultDeviceLightAutomaticOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightAutomaticOperations {

    override suspend fun read(deviceUid: String): DeviceLightAutomaticReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentStatus(uid)
        return when {
            uid == null -> DeviceLightAutomaticReadResult.Failed(
                DeviceLightAutomaticFailure.INVALID_DATA
            )
            runtime == null -> DeviceLightAutomaticReadResult.Failed(
                DeviceLightAutomaticFailure.UNAVAILABLE
            )
            status == null -> DeviceLightAutomaticReadResult.Failed(
                DeviceLightAutomaticFailure.NOT_CONNECTED
            )
            else -> readAvailable(uid, runtime, status)
        }
    }

    private suspend fun readAvailable(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository,
        status: DeviceLightStatus
    ): DeviceLightAutomaticReadResult = try {
        when (val result = runtime.requestAutoPrograms(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightAutomaticReadResult.Available(
                result.value.toApplicationSnapshot(uid, status)
            )
            else -> DeviceLightAutomaticReadResult.Failed(result.toFailure())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DeviceLightAutomaticReadResult.Failed(DeviceLightAutomaticFailure.INVALID_DATA)
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
            else -> executeSafely(uid, runtime, execute)
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
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightAutomaticMutationResult.Success
            else -> DeviceLightAutomaticMutationResult.Failed(result.toFailure())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DeviceLightAutomaticMutationResult.Failed(DeviceLightAutomaticFailure.UNAVAILABLE)
    }
}

private fun DeviceLightAutoPrograms.toApplicationSnapshot(
    uid: DeviceUid,
    status: DeviceLightStatus
): DeviceLightAutomaticSnapshot {
    require(programCount == programs.size)
    require(enabledCount == programs.count(DeviceLightAutoProgram::enabled))
    require(capacity == status.policy.auto.capacity)
    val channels = status.product.sceneFields.map(String::toAutomaticChannel)
    return DeviceLightAutomaticSnapshot(
        deviceUid = uid.value,
        revision = revision,
        capacity = capacity,
        channels = channels,
        programs = programs.map { program -> program.toApplicationProgram(status, channels) }
    )
}

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

private fun DeviceRuntimeCommandOutcome<*>.toFailure(): DeviceLightAutomaticFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightAutomaticFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightAutomaticFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceLightAutomaticFailure.REJECTED
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightAutomaticFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightAutomaticFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}
