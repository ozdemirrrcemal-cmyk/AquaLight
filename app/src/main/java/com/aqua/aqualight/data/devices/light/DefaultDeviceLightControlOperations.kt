package com.aqua.aqualight.data.devices.light

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.DeviceLightControlSnapshot
import com.aqua.aqualight.application.devices.light.matchesLightControlSurface
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.isAuthoritative
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/** Stateless application adapter over the owner-scoped central Light V1 runtime repository. */
internal class DefaultDeviceLightControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightControlOperations {

    private val rootOperations = DefaultDeviceRootOperations(devicesRepository)

    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.light }
        return if (uid == null || runtime == null) {
            flowOf(failed(DeviceLightControlFailure.UNAVAILABLE))
        } else {
            combine(rootOperations.observe(uid.value), runtime.states) { root, _ ->
                projectRead(uid, root, runtime.currentStatus(uid))
            }.distinctUntilChanged()
        }
    }

    override fun currentControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> projectRead(
                resolved.deviceUid,
                resolved.root,
                resolved.runtime.currentStatus(resolved.deviceUid)
            )
        }

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> runCatching {
                resolved.runtime.requestStatus(resolved.deviceUid)
            }.fold(
                onSuccess = { outcome -> outcome.toRefreshedControlResult(resolved) },
                onFailure = { failed(DeviceLightControlFailure.INVALID_DATA) }
            )
        }

    private fun resolveRuntime(deviceUid: String): RuntimeResolution {
        val uid = deviceUid.toDeviceUidOrNull()
        val root = uid?.let { rootOperations.current(it.value) }
        return when {
            uid == null || root == null ->
                RuntimeResolution.Failed(DeviceLightControlFailure.UNAVAILABLE)
            !root.isSupportedLightRoot() ->
                RuntimeResolution.Failed(DeviceLightControlFailure.UNSUPPORTED)
            else -> devicesRepository.runtimeModules()?.light
                ?.let { runtime -> RuntimeResolution.Ready(uid, root, runtime) }
                ?: RuntimeResolution.Failed(DeviceLightControlFailure.UNAVAILABLE)
        }
    }
}

private sealed interface RuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val root: DeviceRootSnapshot,
        val runtime: DeviceLightRuntimeRepository
    ) : RuntimeResolution

    data class Failed(val failure: DeviceLightControlFailure) : RuntimeResolution
}

private fun projectRead(
    deviceUid: DeviceUid,
    root: DeviceRootSnapshot?,
    status: DeviceLightStatus?
): DeviceLightControlResult = when {
    root == null || status == null -> failed(DeviceLightControlFailure.UNAVAILABLE)
    !root.isSupportedLightRoot() -> failed(DeviceLightControlFailure.UNSUPPORTED)
    status.product.wireValue != root.productKey -> failed(DeviceLightControlFailure.INVALID_DATA)
    else -> status.toControlSnapshot(deviceUid)
        .takeIf { snapshot -> snapshot.matchesLightControlSurface(deviceUid.value, root) }
        ?.let(DeviceLightControlResult::Available)
        ?: failed(DeviceLightControlFailure.INVALID_DATA)
}

private fun DeviceRuntimeCommandOutcome<DeviceLightStatus>.toRefreshedControlResult(
    resolved: RuntimeResolution.Ready
): DeviceLightControlResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> {
        val acceptedStatus = resolved.runtime.currentStatus(resolved.deviceUid)
        if (
            acceptedStatus == value &&
            resolved.runtime.isAuthoritative(resolved.deviceUid, generation)
        ) {
            projectRead(resolved.deviceUid, resolved.root, acceptedStatus)
        } else {
            failed(DeviceLightControlFailure.UNAVAILABLE)
        }
    }
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated ->
        failed(DeviceLightControlFailure.NOT_CONNECTED)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        failed(DeviceLightControlFailure.UNSUPPORTED)
    is DeviceRuntimeCommandOutcome.FirmwareError ->
        failed(DeviceLightControlFailure.REJECTED)
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        failed(DeviceLightControlFailure.INVALID_DATA)
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled ->
        failed(DeviceLightControlFailure.UNAVAILABLE)
}

private fun DeviceLightStatus.toControlSnapshot(
    deviceUid: DeviceUid
) = DeviceLightControlSnapshot(
    deviceUid = deviceUid.value,
    productKey = product.wireValue,
    physicalChannelCount = runtime.physicalChannelCount,
    channelKeys = channels.sortedBy { channel -> channel.order }.map { channel -> channel.key }
)

private fun DeviceRootSnapshot.isSupportedLightRoot(): Boolean = when {
    catalogState != DeviceRootCatalogState.VALID -> false
    family != OwnerDeviceFamily.LIGHT -> false
    productKey !in SUPPORTED_LIGHT_PRODUCT_KEYS -> false
    lightChannelCount <= 0 -> false
    else -> lightChannelCount == channelSlots.lightChannels.size
}

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private fun failed(failure: DeviceLightControlFailure) =
    DeviceLightControlResult.Failed(failure)

private val SUPPORTED_LIGHT_PRODUCT_KEYS = DeviceLightProduct.entries
    .mapTo(hashSetOf()) { product -> product.wireValue }
