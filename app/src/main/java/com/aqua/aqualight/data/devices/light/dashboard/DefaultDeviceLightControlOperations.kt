package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.matchesLightControlSurface
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightDashboardReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraph
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.currentDashboard
import com.aqua.aqualight.data.devices.runtime.modules.light.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.light.isGraphAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.light.requestGraph
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
            combine(rootOperations.observe(uid.value), runtime.stateRevision) { root, _ ->
                val dashboard = runtime.currentDashboard(
                    uid,
                    DeviceLightDashboardReadAuthority.PRESENTATION
                )
                projectRead(uid, root, dashboard?.status, dashboard?.graph)
            }.distinctUntilChanged()
        }
    }

    override fun currentControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> resolved.runtime
                .currentDashboard(
                    resolved.deviceUid,
                    DeviceLightDashboardReadAuthority.AUTHORITATIVE
                )
                .let { dashboard ->
                    projectRead(
                        resolved.deviceUid,
                        resolved.root,
                        dashboard?.status,
                        dashboard?.graph
                    )
                }
        }

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> failed(resolved.failure)
            is RuntimeResolution.Ready -> runCatching { refresh(resolved) }.fold(
                onSuccess = { result -> result },
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

private suspend fun refresh(
    resolved: RuntimeResolution.Ready
): DeviceLightControlResult {
    val outcome = resolved.runtime.requestStatus(resolved.deviceUid)
    return when (outcome) {
        is DeviceRuntimeCommandOutcome.Success -> {
            val acceptedStatus = resolved.runtime.currentStatus(resolved.deviceUid)
            val generation = outcome.generation
            val value = outcome.value
            if (
                acceptedStatus == value &&
                resolved.runtime.isAuthoritative(resolved.deviceUid, generation)
            ) {
                resolved.refreshGraph()
            } else {
                failed(DeviceLightControlFailure.UNAVAILABLE)
            }
        }
        else -> failed(outcome.toControlFailure())
    }
}

private suspend fun RuntimeResolution.Ready.refreshGraph(): DeviceLightControlResult =
    when (val graphOutcome = runtime.requestGraph(deviceUid)) {
        is DeviceRuntimeCommandOutcome.Success -> graphOutcome.toControlResult(
            resolution = this
        )
        else -> failed(graphOutcome.toControlFailure())
    }

private fun DeviceRuntimeCommandOutcome.Success<DeviceLightGraph>.toControlResult(
    resolution: RuntimeResolution.Ready
): DeviceLightControlResult {
    val dashboard = resolution.runtime.currentDashboard(
        resolution.deviceUid,
        DeviceLightDashboardReadAuthority.AUTHORITATIVE
    )
    val graphAccepted = dashboard?.graph == value &&
        resolution.runtime.isGraphAuthoritative(resolution.deviceUid, generation)
    return if (graphAccepted && dashboard != null) {
        projectRead(resolution.deviceUid, resolution.root, dashboard.status, dashboard.graph)
    } else {
        failed(DeviceLightControlFailure.UNAVAILABLE)
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
    status: DeviceLightStatus?,
    graph: DeviceLightGraph?
): DeviceLightControlResult = when {
    root == null || status == null || graph == null ->
        failed(DeviceLightControlFailure.UNAVAILABLE)
    !root.isSupportedLightRoot() -> failed(DeviceLightControlFailure.UNSUPPORTED)
    status.product.wireValue != root.productKey -> failed(DeviceLightControlFailure.INVALID_DATA)
    else -> status.toControlSnapshot(deviceUid, graph)
        .takeIf { snapshot -> snapshot.matchesLightControlSurface(deviceUid.value, root) }
        ?.let(DeviceLightControlResult::Available)
        ?: failed(DeviceLightControlFailure.INVALID_DATA)
}

private fun DeviceRuntimeCommandOutcome<*>.toControlFailure(): DeviceLightControlFailure =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> DeviceLightControlFailure.UNAVAILABLE
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated ->
            DeviceLightControlFailure.NOT_CONNECTED
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
            DeviceLightControlFailure.UNSUPPORTED
        is DeviceRuntimeCommandOutcome.FirmwareError ->
            DeviceLightControlFailure.REJECTED
        is DeviceRuntimeCommandOutcome.ProtocolError ->
            DeviceLightControlFailure.INVALID_DATA
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled ->
            DeviceLightControlFailure.UNAVAILABLE
    }

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
