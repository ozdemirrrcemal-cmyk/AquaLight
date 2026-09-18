package com.aqua.aqualight.data.devices.light.dashboard

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlFailure
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlMode
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlOperations
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightControlResult
import com.aqua.aqualight.application.devices.light.dashboard.DeviceLightModeDiagnostic
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
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightDashboardRefreshResult
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightGraph
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightMode
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightProduct
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.light.DeviceLightStatus
import com.aqua.aqualight.data.devices.runtime.modules.light.currentDashboard
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

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
            combine(rootOperations.observe(uid.value), runtime.stateRevision) { root, _ ->
                val dashboard = runtime.currentDashboard(
                    uid,
                    DeviceLightDashboardReadAuthority.PRESENTATION
                )
                projectRead(
                    uid,
                    root,
                    dashboard?.status,
                    dashboard?.graph,
                    systemOperations.current(uid.value)
                )
            }.distinctUntilChanged()
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
        is RuntimeResolution.Failed -> mutationFailed(
            failure = resolved.failure,
            diagnostic = DeviceLightModeDiagnostic(
                stage = "PRECONDITION_FAILED",
                requestedMode = mode,
                details = listOf(
                    "expectedWireMode=" + mode.toFirmwareMode().wireValue,
                    "failure=" + resolved.failure
                )
            )
        )
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
} catch (error: Exception) {
    mutationFailed(
        failure = DeviceLightControlFailure.INVALID_DATA,
        diagnostic = DeviceLightModeDiagnostic(
            stage = "ANDROID_EXCEPTION",
            requestedMode = mode,
            details = listOf(
                "expectedWireMode=" + mode.toFirmwareMode().wireValue,
                "exception=" + error::class.java.name,
                "message=" + error.message.orEmpty()
            )
        )
    )
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
    else -> {
        val failure = toControlFailure()
        mutationFailed(
            failure = failure,
            diagnostic = toModeDiagnostic(
                stage = "COMMAND_FAILED",
                requestedMode = requestedMode,
                expectedWireMode = firmwareMode.wireValue,
                failure = failure
            )
        )
    }
}

private fun RuntimeResolution.Ready.confirmCommittedMode(
    outcome: DeviceRuntimeCommandOutcome.Success<DeviceLightControlSetResult>,
    requestedMode: DeviceLightControlMode,
    firmwareMode: DeviceLightMode,
    systemOperations: DeviceLightSystemOperations
): DeviceLightModeMutationResult {
    val baseDiagnostic = outcome.toModeDiagnostic(
        stage = "ACK_RECEIVED",
        requestedMode = requestedMode,
        expectedWireMode = firmwareMode.wireValue,
        actualWireMode = outcome.value.mode.wireValue
    )
    if (outcome.value.mode != firmwareMode) {
        return mutationFailed(
            failure = DeviceLightControlFailure.INVALID_DATA,
            diagnostic = baseDiagnostic.copy(
                stage = "ACK_MODE_MISMATCH",
                details = baseDiagnostic.details + "failure=INVALID_DATA"
            )
        )
    }
    modules.scheduleLightControlReconciliation(deviceUid, firmwareMode)
    val current = currentControl(systemOperations)
    val authoritativeMode = (current as? DeviceLightControlResult.Available)
        ?.snapshot
        ?.hero
        ?.mode
    val snapshot = (current as? DeviceLightControlResult.Available)
        ?.snapshot
        ?.takeIf { candidate -> candidate.hero.mode == requestedMode }
    return if (snapshot != null) {
        DeviceLightModeMutationResult.Reconciled(
            snapshot = snapshot,
            diagnostic = baseDiagnostic.copy(
                stage = "ACK_RECONCILED",
                details = baseDiagnostic.details + ("authoritativeMode=" + snapshot.hero.mode)
            )
        )
    } else {
        DeviceLightModeMutationResult.Committed(requestedMode).copy(
            diagnostic = baseDiagnostic.copy(
                stage = "ACK_COMMITTED_READBACK_PENDING",
                details = baseDiagnostic.details +
                    "authoritativeMode=" + (authoritativeMode?.toString() ?: "UNAVAILABLE")
            )
        )
    }
}

private suspend fun RuntimeResolution.Ready.refreshControl(
    systemOperations: DeviceLightSystemOperations
): DeviceLightControlResult = when (val refresh = modules.refreshLightDashboard(deviceUid)) {
    is DeviceLightDashboardRefreshResult.Success -> {
        val result = projectRead(
            deviceUid = deviceUid,
            root = root,
            status = refresh.dashboard.status,
            graph = refresh.dashboard.graph,
            systemResult = systemOperations.current(deviceUid.value)
        )
        if (result is DeviceLightControlResult.Available && root.supportsLightSystem()) {
            systemOperations.refresh(deviceUid.value)
            currentControl(systemOperations)
        } else {
            result
        }
    }
    is DeviceLightDashboardRefreshResult.Failed ->
        DeviceLightControlResult.Failed(refresh.outcome.toControlFailure())
    DeviceLightDashboardRefreshResult.RejectedStale ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
    DeviceLightDashboardRefreshResult.Malformed ->
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
        deviceUid,
        root,
        dashboard?.status,
        dashboard?.graph,
        systemOperations.current(deviceUid.value)
    )
}

private fun projectRead(
    deviceUid: DeviceUid,
    root: DeviceRootSnapshot?,
    status: DeviceLightStatus?,
    graph: DeviceLightGraph?,
    systemResult: DeviceLightSystemReadResult
): DeviceLightControlResult = when {
    root == null || status == null || graph == null ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.UNAVAILABLE)
    !root.isSupportedLightRoot() -> DeviceLightControlResult.Failed(DeviceLightControlFailure.UNSUPPORTED)
    status.product.wireValue != root.productKey ->
        DeviceLightControlResult.Failed(DeviceLightControlFailure.INVALID_DATA)
    else -> status.toControlSnapshot(
        deviceUid = deviceUid,
        graph = graph,
        systemSupported = root.supportsLightSystem(),
        systemSnapshot = (systemResult as? DeviceLightSystemReadResult.Available)?.snapshot
    )
        .takeIf { snapshot -> snapshot.matchesLightControlSurface(deviceUid.value, root) }
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

private fun mutationFailed(
    failure: DeviceLightControlFailure,
    diagnostic: DeviceLightModeDiagnostic? = null
) = DeviceLightModeMutationResult.Failed(
    failure = failure,
    diagnostic = diagnostic
)

private val SUPPORTED_LIGHT_PRODUCT_KEYS = DeviceLightProduct.entries
    .mapTo(hashSetOf()) { product -> product.wireValue }
