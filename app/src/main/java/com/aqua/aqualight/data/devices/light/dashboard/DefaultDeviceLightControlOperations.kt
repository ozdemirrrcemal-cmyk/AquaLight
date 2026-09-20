package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeMutationResult
import com.aqua.aqualight.application.devices.light.dashboard.matchesLightControlSurface
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemOperations
import com.aqua.aqualight.application.devices.light.system.DeviceLightSystemReadResult
import com.aqua.aqualight.data.devices.DefaultDeviceRootOperations
import com.aqua.aqualight.data.devices.light.supportsLightSystem
import com.aqua.aqualight.data.devices.light.system.DefaultDeviceLightSystemOperations
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightControlSetPayload
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightControlSetResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightDashboardReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraph
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightCustomDocument
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightLibraryReadAuthority
import com.aqua.aqualight.data.devices.runtime.modules.light.currentDashboard
import com.aqua.aqualight.data.devices.runtime.modules.light.currentLibrary
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Stateless application adapter over the owner-scoped central Light V1 runtime repository. */
internal class DefaultDeviceLightControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceLightControlOperations {

    private val rootOperations = DefaultDeviceRootOperations(devicesRepository)
    private val systemOperations: DeviceLightSystemOperations =
        DefaultDeviceLightSystemOperations(devicesRepository)

    override fun observeControl(deviceUid: String): Flow<DeviceLightControlResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.light }
        return if (uid == null || runtime == null) {
            flowOf(DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE))
        } else {
            runtime.stateRevision
                .map { projectLightControlPresentationRead(uid, runtime) }
                .distinctUntilChanged()
        }
    }

    override fun currentControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> DeviceLightControlResult.Failed(resolved.failure)
            is RuntimeResolution.Ready -> resolved.currentControl(systemOperations)
        }

    override suspend fun refreshControl(deviceUid: String): DeviceLightControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> DeviceLightControlResult.Failed(resolved.failure)
            is RuntimeResolution.Ready -> try {
                resolved.refreshControl(systemOperations)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
            }
        }

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceLightControlMode
    ): DeviceLightModeMutationResult = when (val resolved = resolveRuntime(deviceUid)) {
        is RuntimeResolution.Failed -> DeviceLightModeMutationResult.Failed(resolved.failure)
        is RuntimeResolution.Ready -> resolved.commitMode(mode, systemOperations)
    }

    private fun resolveRuntime(deviceUid: String): RuntimeResolution {
        val uid = deviceUid.toDeviceUidOrNull()
        val root = uid?.let { rootOperations.current(it.value) }
        val modules = uid?.let { devicesRepository.runtimeModules() }
        return when {
            uid == null || root == null ->
                RuntimeResolution.Failed(DeviceLightControlFailure.UNAVAILABLE)
            !root.isSupportedLightRoot() ->
                RuntimeResolution.Failed(DeviceLightControlFailure.UNSUPPORTED)
            modules == null ->
                RuntimeResolution.Failed(DeviceLightControlFailure.UNAVAILABLE)
            else -> RuntimeResolution.Ready(
                deviceUid = uid,
                root = root,
                runtime = modules.light,
                modules = modules
            )
        }
    }
}

private suspend fun RuntimeResolution.Ready.commitMode(
    mode: DeviceLightControlMode,
    systemOperations: DeviceLightSystemOperations
): DeviceLightModeMutationResult = try {
    val firmwareMode = mode.toFirmwareMode()
    runtime.setControl(
        deviceUid,
        DeviceLightControlSetPayload(firmwareMode)
    ).toModeMutationResult(
        resolution = this,
        requestedMode = mode,
        firmwareMode = firmwareMode,
        systemOperations = systemOperations
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    DeviceLightModeMutationResult.Failed(DeviceLightControlFailure.INVALID_DATA)
}

private fun DeviceRuntimeCommandOutcome<DeviceLightControlSetResult>.toModeMutationResult(
    resolution: RuntimeResolution.Ready,
    requestedMode: DeviceLightControlMode,
    firmwareMode: DeviceLightMode,
    systemOperations: DeviceLightSystemOperations
): DeviceLightModeMutationResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> resolution.confirmCommittedMode(
        outcome = this,
        requestedMode = requestedMode,
        firmwareMode = firmwareMode,
        systemOperations = systemOperations
    )
    else -> DeviceLightModeMutationResult.Failed(toControlFailure())
}

private fun RuntimeResolution.Ready.confirmCommittedMode(
    outcome: DeviceRuntimeCommandOutcome.Success<DeviceLightControlSetResult>,
    requestedMode: DeviceLightControlMode,
    firmwareMode: DeviceLightMode,
    systemOperations: DeviceLightSystemOperations
): DeviceLightModeMutationResult {
    if (outcome.value.mode != firmwareMode) {
        return DeviceLightModeMutationResult.Failed(DeviceLightControlFailure.INVALID_DATA)
    }
    modules.scheduleLightControlReconciliation(
        deviceUid = deviceUid,
        expectedMode = firmwareMode,
        generation = outcome.generation
    )
    val snapshot = (currentControl(systemOperations) as? DeviceLightControlResult.Available)
        ?.snapshot
        ?.takeIf { current -> current.hero.mode == requestedMode }
    return snapshot
        ?.let(DeviceLightModeMutationResult::Reconciled)
        ?: DeviceLightModeMutationResult.Committed(requestedMode)
}

private suspend fun RuntimeResolution.Ready.refreshControl(
    systemOperations: DeviceLightSystemOperations
): DeviceLightControlResult = when (val refresh = modules.refreshLightRuntime(deviceUid)) {
    is DeviceLightRuntimeRefreshResult.Success -> projectRead(
        LightProjectionInput(
            deviceUid = deviceUid,
            root = root,
            status = refresh.dashboard.status,
            graph = refresh.dashboard.graph,
            customDocument = runtime.currentLibrary(
                deviceUid,
                DeviceLightLibraryReadAuthority.AUTHORITATIVE
            )?.custom,
            systemResult = systemOperations.current(deviceUid.value)
        )
    )
    is DeviceLightRuntimeRefreshResult.Failed ->
        DeviceLightControlResult.Failed(refresh.outcome.toControlFailure())
    DeviceLightRuntimeRefreshResult.RejectedStale ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
    DeviceLightRuntimeRefreshResult.Malformed ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
}

private fun DeviceLightControlMode.toFirmwareMode(): DeviceLightMode = when (this) {
    DeviceLightControlMode.MANUAL -> DeviceLightMode.MANUAL
    DeviceLightControlMode.AUTOMATIC -> DeviceLightMode.AUTO
    DeviceLightControlMode.CUSTOM -> DeviceLightMode.CUSTOM
}

private sealed interface RuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val root: DeviceRootSnapshot,
        val runtime: DeviceLightRuntimeRepository,
        val modules: DeviceRuntimeModuleProvider
    ) : RuntimeResolution

    data class Failed(val failure: DeviceLightControlFailure) : RuntimeResolution
}

private fun RuntimeResolution.Ready.currentControl(
    systemOperations: DeviceLightSystemOperations
): DeviceLightControlResult = runtime.currentDashboard(
    deviceUid,
    DeviceLightDashboardReadAuthority.AUTHORITATIVE
).let { dashboard ->
    projectRead(
        LightProjectionInput(
            deviceUid = deviceUid,
            root = root,
            status = dashboard?.status,
            graph = dashboard?.graph,
            customDocument = runtime.currentLibrary(
                deviceUid,
                DeviceLightLibraryReadAuthority.AUTHORITATIVE
            )?.custom,
            systemResult = systemOperations.current(deviceUid.value)
        )
    )
}

private data class LightProjectionInput(
    val deviceUid: DeviceUid,
    val root: DeviceRootSnapshot?,
    val status: DeviceLightStatus?,
    val graph: DeviceLightGraph?,
    val customDocument: DeviceLightCustomDocument?,
    val systemResult: DeviceLightSystemReadResult
)

private fun projectRead(input: LightProjectionInput): DeviceLightControlResult = when {
    input.root == null || input.status == null || input.graph == null ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
    !input.root.isSupportedLightRoot() ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.UNSUPPORTED)
    input.status.product.wireValue != input.root.productKey ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
    else -> input.status.toControlSnapshot(
        deviceUid = input.deviceUid,
        graph = input.graph,
        customDocument = input.customDocument,
        systemSupported = input.root.supportsLightSystem(),
        systemSnapshot = (input.systemResult as? DeviceLightSystemReadResult.Available)?.snapshot
    )
        .takeIf { snapshot ->
            snapshot.matchesLightControlSurface(input.deviceUid.value, input.root)
        }
        ?.let(DeviceLightControlResult::Available)
        ?: DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
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

private val SUPPORTED_LIGHT_PRODUCT_KEYS = DeviceLightProduct.entries
    .mapTo(hashSetOf()) { product -> product.wireValue }
