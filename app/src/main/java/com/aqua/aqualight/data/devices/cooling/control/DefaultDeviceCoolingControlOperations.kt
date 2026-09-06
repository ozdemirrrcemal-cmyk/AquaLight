package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.DeviceOperationCommandDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationDiagnostic
import com.aqua.aqualight.application.devices.DeviceOperationRuntimeStateDiagnostic
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.data.devices.cooling.v1.DeviceCoolingV1FailureMapper
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.core.toOperationDiagnostic
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentAuthoritativeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Cool Pro 1F application adapter over the single central Cooling runtime owner.
 *
 * Runtime bootstrap and typed events own background freshness. Explicit application refresh
 * boundaries may request a current status document; presentation only observes central authority.
 */
internal class DefaultDeviceCoolingControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return if (uid == null || runtime == null) {
            flowOf(DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable))
        } else {
            combine(
                devicesRepository.observeDevice(uid),
                runtime.states
            ) { device, states ->
                projectRead(device, states[uid], requireAuthority = true)
            }.distinctUntilChanged()
        }
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult {
        val uid = deviceUid.toDeviceUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return if (uid == null || runtime == null) {
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
        } else {
            projectRead(
                device = devicesRepository.currentDevice(uid),
                state = runtime.currentAuthoritativeState(uid),
                requireAuthority = true
            )
        }
    }

    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult =
        when (val resolved = resolveRuntime(deviceUid)) {
            is RuntimeResolution.Failed -> DeviceCoolingControlResult.Failed(
                failure = resolved.failure,
                diagnostic = resolved.diagnostic
            )
            is RuntimeResolution.Ready -> resolved.runtime
                .requestStatus(resolved.deviceUid)
                .toControlResult(resolved.runtime, resolved.deviceUid)
        }

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = when (val resolved = resolveRuntime(deviceUid)) {
        is RuntimeResolution.Failed -> DeviceCoolingControlResult.Failed(
            failure = resolved.failure,
            diagnostic = resolved.diagnostic
        )
        is RuntimeResolution.Ready -> setMode(resolved, mode)
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult = when (val resolved = resolveRuntime(deviceUid)) {
        is RuntimeResolution.Failed -> DeviceCoolingControlResult.Failed(
            failure = resolved.failure,
            diagnostic = resolved.diagnostic
        )
        is RuntimeResolution.Ready -> setManualFanPercent(resolved, percent)
    }

    private suspend fun setMode(
        resolved: RuntimeResolution.Ready,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
        return if (config == null) {
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.NotConnected)
        } else {
            runCatching {
                DeviceCoolingV1ConfigApplyPayload(
                    expectedConfigRevision = config.configRevision,
                    controlMode = mode.toV1()
                )
            }.fold(
                onSuccess = { payload ->
                    resolved.runtime
                        .applyConfig(resolved.deviceUid, payload)
                        .toControlResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = {
                    DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
                }
            )
        }
    }

    private suspend fun setManualFanPercent(
        resolved: RuntimeResolution.Ready,
        percent: Int
    ): DeviceCoolingControlResult {
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
        return if (config == null) {
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.NotConnected)
        } else {
            runCatching {
                DeviceCoolingV1ManualApplyPayload(
                    expectedConfigRevision = config.configRevision,
                    targetPercent = percent.toDouble()
                )
            }.fold(
                onSuccess = { payload ->
                    resolved.runtime
                        .applyManual(resolved.deviceUid, payload)
                        .toControlResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = {
                    DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
                }
            )
        }
    }

    private fun resolveRuntime(deviceUid: String): RuntimeResolution {
        val uid = deviceUid.toDeviceUidOrNull()
        val device = uid?.let { currentUid -> devicesRepository.currentDevice(currentUid) }
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return when {
            uid == null || device == null ->
                resolutionFailure(
                    deviceUid = deviceUid,
                    failure = DeviceCoolingControlFailure.Unavailable,
                    outcome = if (uid == null) "INVALID_DEVICE_UID" else "DEVICE_NOT_REGISTERED"
                )
            !device.isSupportedCoolingV1() ->
                resolutionFailure(
                    deviceUid = deviceUid,
                    failure = DeviceCoolingControlFailure.Unsupported,
                    outcome = "UNSUPPORTED_COOLING_DEVICE"
                )
            runtime == null ->
                resolutionFailure(
                    deviceUid = deviceUid,
                    failure = DeviceCoolingControlFailure.Unavailable,
                    outcome = "COOLING_RUNTIME_UNAVAILABLE"
                )
            else -> RuntimeResolution.Ready(uid, runtime)
        }
    }
}

private sealed interface RuntimeResolution {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceCoolingRuntimeRepository
    ) : RuntimeResolution

    data class Failed(
        val failure: DeviceCoolingControlFailure,
        val diagnostic: DeviceOperationDiagnostic
    ) : RuntimeResolution
}

private fun resolutionFailure(
    deviceUid: String,
    failure: DeviceCoolingControlFailure,
    outcome: String
): RuntimeResolution.Failed = RuntimeResolution.Failed(
    failure = failure,
    diagnostic = DeviceOperationDiagnostic(
        stage = "COOLING_RUNTIME_RESOLUTION",
        outcome = outcome,
        command = DeviceOperationCommandDiagnostic(
            deviceUid = deviceUid,
            module = "cooling",
            action = "resolve"
        ),
        detail = "failure=${failure.diagnosticName()}"
    )
)

private fun projectRead(
    device: DeviceSnapshot?,
    state: DeviceCoolingRuntimeState?,
    requireAuthority: Boolean
): DeviceCoolingControlResult = when {
    device == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    !device.isSupportedCoolingV1() ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unsupported)
    state == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    requireAuthority && !state.authoritative ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    else -> DeviceCoolingControlSnapshotMapper.map(state)
        ?.let(DeviceCoolingControlResult::Available)
        ?: DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
}

private fun DeviceSnapshot.isSupportedCoolingV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun DeviceCoolingControlMode.toV1(): DeviceCoolingV1ControlMode = when (this) {
    DeviceCoolingControlMode.AUTOMATIC -> DeviceCoolingV1ControlMode.AUTOMATIC
    DeviceCoolingControlMode.MANUAL -> DeviceCoolingV1ControlMode.MANUAL
    DeviceCoolingControlMode.PROGRAM -> DeviceCoolingV1ControlMode.PROGRAM
}

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private suspend fun DeviceRuntimeCommandOutcome<*>.toControlResult(
    runtime: DeviceCoolingRuntimeRepository,
    deviceUid: DeviceUid
): DeviceCoolingControlResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> toSuccessfulControlResult(runtime, deviceUid)
    is DeviceRuntimeCommandOutcome.NotConnected ->
        failed(DeviceCoolingControlFailure.NotConnected)
    is DeviceRuntimeCommandOutcome.NotAuthenticated ->
        failed(DeviceCoolingControlFailure.NotConnected)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        failed(DeviceCoolingControlFailure.Unsupported)
    is DeviceRuntimeCommandOutcome.FirmwareError -> failed(
        DeviceCoolingControlFailure.Rejected(DeviceCoolingV1FailureMapper.map(this))
    )
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        failed(DeviceCoolingControlFailure.InvalidData)
    is DeviceRuntimeCommandOutcome.SendFailed ->
        failed(DeviceCoolingControlFailure.Unavailable)
    is DeviceRuntimeCommandOutcome.Timeout ->
        failed(DeviceCoolingControlFailure.Unavailable)
    is DeviceRuntimeCommandOutcome.Cancelled ->
        failed(DeviceCoolingControlFailure.Unavailable)
}

private fun DeviceRuntimeCommandOutcome.Success<*>.toSuccessfulControlResult(
    runtime: DeviceCoolingRuntimeRepository,
    deviceUid: DeviceUid
): DeviceCoolingControlResult {
    val currentState = runtime.currentState(deviceUid)
    val authoritativeState = runtime.currentAuthoritativeState(deviceUid)
    val runtimeDiagnostic = DeviceOperationRuntimeStateDiagnostic(
        connectionGeneration = currentState?.connectionGeneration?.value,
        authoritative = currentState?.authoritative
    )
    return when {
        currentState == null -> failedAfterSuccess(
            outcome = "SUCCESS_WITHOUT_RUNTIME_STATE",
            runtimeState = runtimeDiagnostic
        )
        authoritativeState == null -> failedAfterSuccess(
            outcome = "SUCCESS_NOT_ACCEPTED_AS_AUTHORITATIVE",
            runtimeState = runtimeDiagnostic
        )
        else -> DeviceCoolingControlSnapshotMapper.map(authoritativeState)
            ?.let(DeviceCoolingControlResult::Available)
            ?: failedAfterSuccess(
                outcome = "SUCCESS_SNAPSHOT_MAPPING_FAILED",
                runtimeState = runtimeDiagnostic
            )
    }
}

private fun DeviceRuntimeCommandOutcome<*>.failed(
    failure: DeviceCoolingControlFailure
): DeviceCoolingControlResult.Failed = DeviceCoolingControlResult.Failed(
    failure = failure,
    diagnostic = toOperationDiagnostic(
        stage = "COOLING_COMMAND",
        detailOverride = "failure=${failure.diagnosticName()}"
    )
)

private fun DeviceRuntimeCommandOutcome.Success<*>.failedAfterSuccess(
    outcome: String,
    runtimeState: DeviceOperationRuntimeStateDiagnostic
): DeviceCoolingControlResult.Failed = DeviceCoolingControlResult.Failed(
    failure = DeviceCoolingControlFailure.Unavailable,
    diagnostic = toOperationDiagnostic(
        stage = "COOLING_RUNTIME_OWNER",
        outcomeOverride = outcome,
        detailOverride = "The successful response did not produce a renderable authoritative snapshot.",
        runtimeState = runtimeState
    )
)

private fun DeviceCoolingControlFailure.diagnosticName(): String = when (this) {
    DeviceCoolingControlFailure.Unsupported -> "Unsupported"
    DeviceCoolingControlFailure.Unavailable -> "Unavailable"
    DeviceCoolingControlFailure.NotConnected -> "NotConnected"
    is DeviceCoolingControlFailure.Rejected -> "Rejected"
    DeviceCoolingControlFailure.InvalidData -> "InvalidData"
}
