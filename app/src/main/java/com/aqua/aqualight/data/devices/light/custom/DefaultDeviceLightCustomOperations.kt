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
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightLibraryReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightLibraryRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomPoint as RuntimeCustomPoint
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightScene
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightPreviewSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.clearPreview
import com.aqua.aqualight.data.devices.runtime.modules.light.currentLibrary
import com.aqua.aqualight.data.devices.runtime.modules.light.requestCustom
import com.aqua.aqualight.data.devices.runtime.modules.light.setPreview
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class DefaultDeviceLightCustomOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightCustomOperations {

    override fun observe(deviceUid: String): Flow<DeviceLightCustomReadResult> {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return if (uid == null || runtime == null) {
            flowOf(DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.UNAVAILABLE))
        } else {
            runtime.stateRevision.map {
                projectCurrent(uid, runtime, DeviceLightLibraryReadAuthority.PRESENTATION)
            }.distinctUntilChanged()
        }
    }

    override fun current(deviceUid: String): DeviceLightCustomReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return if (uid == null || runtime == null) {
            DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
        } else {
            projectCurrent(uid, runtime, DeviceLightLibraryReadAuthority.AUTHORITATIVE)
        }
    }

    override suspend fun read(deviceUid: String): DeviceLightCustomReadResult {
        val uid = deviceUid.toUidOrNull()
        val runtime = devicesRepository.runtimeModules()?.light
        return when {
            uid == null -> DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
            runtime == null -> DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
            runtime.currentStatus(uid) == null -> DeviceLightCustomReadResult.Failed(
                DeviceLightCustomFailure.NOT_CONNECTED
            )
            else -> readAvailable(uid, runtime)
        }
    }

    private suspend fun readAvailable(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository
    ): DeviceLightCustomReadResult = try {
        when (val status = runtime.requestStatus(uid)) {
            is DeviceRuntimeCommandOutcome.Success -> when (val result = runtime.requestCustom(uid)) {
                is DeviceRuntimeCommandOutcome.Success -> projectCurrent(
                    uid,
                    runtime,
                    DeviceLightLibraryReadAuthority.AUTHORITATIVE
                )
                else -> DeviceLightCustomReadResult.Failed(result.toFailure())
            }
            else -> DeviceLightCustomReadResult.Failed(status.toFailure())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
    }

    private fun projectCurrent(
        uid: DeviceUid,
        runtime: DeviceLightRuntimeRepository,
        authority: DeviceLightLibraryReadAuthority
    ): DeviceLightCustomReadResult {
        val frame = runtime.currentLibrary(uid, authority)
            ?: return DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.NOT_CONNECTED)
        val writeAuthoritative = runtime.currentLibrary(
            uid,
            DeviceLightLibraryReadAuthority.AUTHORITATIVE
        ) == frame
        return runCatching {
            DeviceLightCustomReadResult.Available(
                frame.toApplicationSnapshot(uid, writeAuthoritative)
            )
        }.getOrElse {
            DeviceLightCustomReadResult.Failed(DeviceLightCustomFailure.INVALID_DATA)
        }
    }

    override suspend fun preview(
        deviceUid: String,
        points: List<DeviceLightCustomPoint>
    ): DeviceLightCustomMutationResult = command(deviceUid) { uid ->
        val runtime = requireNotNull(devicesRepository.runtimeModules()?.light)
        val product = requireNotNull(runtime.currentStatus(uid)).product
        val runtimePoints = points.map { point ->
            RuntimeCustomPoint(
                timeMs = point.timeMs,
                scene = DeviceLightScene(
                    product = product,
                    percents = product.sceneFields.associateWith { field ->
                        point.scene.channels.getValue(field.toCustomChannel())
                    }
                )
            )
        }
        runtime.setPreview(uid, DeviceLightPreviewSetPayload.CustomDay(runtimePoints))
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

private fun DeviceLightLibraryRuntimeState.toApplicationSnapshot(
    uid: DeviceUid,
    firmwareWriteAuthoritative: Boolean
): DeviceLightCustomSnapshot {
    val document = custom
    require(document.pointCount == document.points.size)
    require(document.installed || document.points.isEmpty())
    return DeviceLightCustomSnapshot(
        deviceUid = uid.value,
        productKey = status.product.wireValue,
        revision = document.revision,
        installed = document.installed,
        weekdaysMask = if (document.installed) document.weekdaysMask else EVERY_DAY_MASK,
        maxPoints = status.policy.custom.maxPoints,
        timeStepMs = status.policy.custom.timeStepMs,
        currentTimeMs = status.scheduler.currentTimeMs,
        channels = status.product.sceneFields.map(String::toCustomChannel),
        points = document.points.takeIf { document.installed }.orEmpty().map { point ->
            DeviceLightCustomPoint(
                timeMs = point.timeMs,
                scene = DeviceLightCustomScene(
                    point.scene.percents.mapKeys { (key, _) -> key.toCustomChannel() }
                )
            )
        },
        firmwareWriteAuthoritative = firmwareWriteAuthoritative
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
