package com.aqua.aqualight.data.devices.light.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomOperations
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomPoint
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomScene
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightPreviewSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.clearPreview
import com.aqua.aqualight.data.devices.runtime.modules.light.requestCustom
import com.aqua.aqualight.data.devices.runtime.modules.light.setPreview
import java.util.concurrent.CancellationException

internal class DefaultDeviceLightCustomOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightCustomOperations {

    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        val status = if (uid == null) null else runtime?.currentStatus(uid)
        return when {
            uid == null -> DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
            runtime == null -> DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
            status == null -> DeviceLightCustomReadResult.Failed(
                DeviceLightCustomFailure.NOT_CONNECTED
            )
            else -> readAvailable(uid, runtime, status)
        }
    }

    private suspend fun readAvailable(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository,
        status: DeviceLightStatus
    ): DeviceLightCustomReadResult = try {
        when (val result = runtime.requestCustom(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> DeviceLightCustomReadResult.Available(
                result.value.toApplicationSnapshot(uid, status)
            )
            else -> DeviceLightCustomReadResult.Failed(result.toFailure())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
    }

    override suspend fun preview(
        deviceUid: String,
        virtualTimeMs: Long
    ): DeviceLightCustomMutationResult = command(deviceUid) { uid ->
        val runtime = requireNotNull(devicesRepository.runtimeModules()?.light)
        runtime.setPreview(uid, DeviceLightPreviewSetPayload.VirtualTime(virtualTimeMs))
    }

    override suspend fun clearPreview(deviceUid: String): DeviceLightCustomMutationResult =
        command(deviceUid) { uid ->
            val runtime = requireNotNull(devicesRepository.runtimeModules()?.light)
            runtime.clearPreview(uid)
        }

    private suspend fun command(
        deviceUid: String,
        execute: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceLightCustomMutationResult {
        val uid = deviceUid.toUidOrNull()
        val runtimeAvailable = devicesRepository.runtimeModules()?.light != null
        return when {
            uid == null -> DeviceLightCustomMutationResult.Failed(
                DeviceLightCustomFailure.INVALID_DATA
            )
            !runtimeAvailable -> DeviceLightCustomMutationResult.Failed(
                DeviceLightCustomFailure.UNAVAILABLE
            )
            else -> try {
                when (val result = execute(uid)) {
                    is DeviceRuntimeCommandOutcome.Success -> DeviceLightCustomMutationResult.Success
                    else -> DeviceLightCustomMutationResult.Failed(result.toFailure())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
            }
        }
    }
}

private fun DeviceLightCustomDocument.toApplicationSnapshot(
    uid: DeviceUid,
    status: DeviceLightStatus
): DeviceLightCustomSnapshot {
    require(pointCount == points.size)
    require(installed || points.isEmpty())
    return DeviceLightCustomSnapshot(
        deviceUid = uid.value,
        productKey = status.product.wireValue,
        revision = revision,
        installed = installed,
        weekdaysMask = if (installed) weekdaysMask else EVERY_DAY_MASK,
        maxPoints = status.policy.custom.maxPoints,
        timeStepMs = status.policy.custom.timeStepMs,
        currentTimeMs = status.scheduler.currentTimeMs,
        channels = status.product.sceneFields.map(String::toCustomChannel),
        points = points.takeIf { installed }.orEmpty().map { point ->
            DeviceLightCustomPoint(
                timeMs = point.timeMs,
                scene = DeviceLightCustomScene(
                    point.scene.percents.mapKeys { (key, _) -> key.toCustomChannel() }
                )
            )
        }
    )
}

private fun String.toCustomChannel(): DeviceLightCustomChannel =
    requireNotNull(DeviceLightCustomChannel.entries.singleOrNull { channel ->
        channel.sceneKey == this
    })

private fun String.toUidOrNull(): DeviceUid? = trim().takeIf(String::isNotBlank)?.let(::DeviceUid)

private const val EVERY_DAY_MASK = 127

private fun DeviceRuntimeCommandOutcome<*>.toFailure(): DeviceLightCustomFailure = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> DeviceLightCustomFailure.NOT_CONNECTED
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> DeviceLightCustomFailure.UNSUPPORTED
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceLightCustomFailure.REJECTED
    is DeviceRuntimeCommandOutcome.ProtocolError -> DeviceLightCustomFailure.INVALID_DATA
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> DeviceLightCustomFailure.UNAVAILABLE
    is DeviceRuntimeCommandOutcome.Success -> error("A successful outcome has no failure.")
}
