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
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightPreviewSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.clearPreview
import com.aqua.aqualight.data.devices.runtime.modules.light.requestCustom
import com.aqua.aqualight.data.devices.runtime.modules.light.setPreview
import java.util.concurrent.CancellationException

internal class DefaultDeviceLightCustomOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightCustomOperations {

    @Suppress("ReturnCount")
    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult {
        val uid = deviceUid.toUidOrNull()
            ?: return DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
        val runtime = devicesRepository.runtimeModules()?.light
            ?: return DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
        val status = runtime.currentStatus(uid)
            ?: return DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.NOT_CONNECTED)
        return try {
            when (val result = runtime.requestCustom(uid)) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    require(result.value.pointCount == result.value.points.size)
                    require(result.value.installed || result.value.points.isEmpty())
                    DeviceLightCustomReadResult.Available(
                        DeviceLightCustomSnapshot(
                        deviceUid = uid.value,
                        productKey = status.product.wireValue,
                        revision = result.value.revision,
                        installed = result.value.installed,
                        weekdaysMask = if (result.value.installed) {
                            result.value.weekdaysMask
                        } else {
                            EVERY_DAY_MASK
                        },
                        maxPoints = status.policy.custom.maxPoints,
                        timeStepMs = status.policy.custom.timeStepMs,
                        currentTimeMs = status.scheduler.currentTimeMs,
                        channels = status.product.sceneFields.map { field ->
                            requireNotNull(DeviceLightCustomChannel.entries.singleOrNull {
                                channel -> channel.sceneKey == field
                            })
                        },
                        points = result.value.points.takeIf { result.value.installed }
                            .orEmpty().map { point ->
                            DeviceLightCustomPoint(
                                timeMs = point.timeMs,
                                scene = DeviceLightCustomScene(
                                    point.scene.percents.mapKeys { (key, _) ->
                                        requireNotNull(DeviceLightCustomChannel.entries.singleOrNull {
                                            channel -> channel.sceneKey == key
                                        })
                                    }
                                )
                            )
                        }
                        )
                    )
                }
                else -> DeviceLightCustomReadResult.Failed(result.toFailure())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
        }
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

    @Suppress("ReturnCount")
    private suspend fun command(
        deviceUid: String,
        execute: suspend (DeviceUid) -> DeviceRuntimeCommandOutcome<*>
    ): DeviceLightCustomMutationResult {
        val uid = deviceUid.toUidOrNull()
            ?: return DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
        if (devicesRepository.runtimeModules()?.light == null) {
            return DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
        }
        return try {
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
