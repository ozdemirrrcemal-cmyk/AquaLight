package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyPayload
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Cool Pro 1F application adapter over the single central Cooling runtime owner.
 *
 * Runtime bootstrap and typed events own freshness. This adapter never issues a status read because
 * a screen appeared; it only observes current authority and executes explicit user mutations.
 */
internal class DefaultDeviceCoolingControlOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingControlOperations {

    override fun observeControl(deviceUid: String): Flow<DeviceCoolingControlResult> {
        val uid = deviceUid.toDeviceUidOrNull()
            ?: return flowOf(DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable))
        val runtime = devicesRepository.runtimeModules()?.cooling
            ?: return flowOf(DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable))
        return combine(
            devicesRepository.observeDevice(uid),
            runtime.states
        ) { device, states ->
            projectRead(device, states[uid], requireAuthority = true)
        }.distinctUntilChanged()
    }

    override fun currentControl(deviceUid: String): DeviceCoolingControlResult {
        val uid = deviceUid.toDeviceUidOrNull()
            ?: return failed(DeviceCoolingControlFailure.Unavailable)
        val runtime = devicesRepository.runtimeModules()?.cooling
            ?: return failed(DeviceCoolingControlFailure.Unavailable)
        // Current is used to seed presentation before collection starts. A retained snapshot may be
        // shown here; the first observe emission immediately marks it stale if authority is revoked.
        return projectRead(
            device = devicesRepository.currentDevice(uid),
            state = runtime.currentState(uid),
            requireAuthority = false
        )
    }

    /** Compatibility boundary only; current-state freshness is runtime-owned and never polled here. */
    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult =
        currentControl(deviceUid)

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val resolved = resolveWritableRuntime(deviceUid)
        if (resolved is WritableRuntime.Failed) return failed(resolved.failure)
        resolved as WritableRuntime.Ready
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
            ?: return failed(DeviceCoolingControlFailure.NotConnected)
        val payload = runCatching {
            DeviceCoolingV1ConfigApplyPayload(
                expectedConfigRevision = config.configRevision,
                controlMode = mode.toV1()
            )
        }.getOrElse { return failed(DeviceCoolingControlFailure.InvalidData) }
        val outcome = resolved.runtime.applyConfig(resolved.deviceUid, payload)
        return outcome.toMutationResult(resolved.runtime, resolved.deviceUid)
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult {
        val resolved = resolveWritableRuntime(deviceUid)
        if (resolved is WritableRuntime.Failed) return failed(resolved.failure)
        resolved as WritableRuntime.Ready
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
            ?: return failed(DeviceCoolingControlFailure.NotConnected)
        val payload = runCatching {
            DeviceCoolingV1ManualApplyPayload(
                expectedConfigRevision = config.configRevision,
                targetPercent = percent.toDouble()
            )
        }.getOrElse { return failed(DeviceCoolingControlFailure.InvalidData) }
        val outcome = resolved.runtime.applyManual(resolved.deviceUid, payload)
        return outcome.toMutationResult(resolved.runtime, resolved.deviceUid)
    }

    private fun resolveWritableRuntime(deviceUid: String): WritableRuntime {
        val uid = deviceUid.toDeviceUidOrNull()
            ?: return WritableRuntime.Failed(DeviceCoolingControlFailure.Unavailable)
        val device = devicesRepository.currentDevice(uid)
            ?: return WritableRuntime.Failed(DeviceCoolingControlFailure.Unavailable)
        if (!device.isSupportedCoolingV1()) {
            return WritableRuntime.Failed(DeviceCoolingControlFailure.Unsupported)
        }
        val runtime = devicesRepository.runtimeModules()?.cooling
            ?: return WritableRuntime.Failed(DeviceCoolingControlFailure.Unavailable)
        return WritableRuntime.Ready(uid, runtime)
    }
}

private sealed interface WritableRuntime {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceCoolingRuntimeRepository
    ) : WritableRuntime

    data class Failed(val failure: DeviceCoolingControlFailure) : WritableRuntime
}

private fun projectRead(
    device: DeviceSnapshot?,
    state: DeviceCoolingRuntimeState?,
    requireAuthority: Boolean
): DeviceCoolingControlResult = when {
    device == null -> failed(DeviceCoolingControlFailure.Unavailable)
    !device.isSupportedCoolingV1() -> failed(DeviceCoolingControlFailure.Unsupported)
    state == null -> failed(DeviceCoolingControlFailure.Unavailable)
    requireAuthority && !state.authoritative -> failed(DeviceCoolingControlFailure.Unavailable)
    else -> state.toControlSnapshot()
        ?.let(DeviceCoolingControlResult::Available)
        ?: failed(DeviceCoolingControlFailure.InvalidData)
}

private fun DeviceCoolingRuntimeState.toControlSnapshot(): DeviceCoolingControlSnapshot? {
    val config = config ?: return null
    val live = telemetry
    val mode = (live?.controlMode ?: config.controlMode).toApplicationMode()
    val waterTemperature = live
        ?.sensors
        ?.firstOrNull { sensor ->
            sensor.sensorKey == DeviceCoolingV1Contract.WATER_SENSOR_KEY && sensor.readingValid
        }
        ?.temperatureC
    return DeviceCoolingControlSnapshot(
        mode = mode,
        manualFanPercent = config.manualTargetPercent.toIntPercentOrNull(),
        actualFanPercent = live?.fan?.outputPercent?.toIntPercentOrNull(),
        tankTemperatureC = waterTemperature,
        capabilities = COOLING_V1_CONTROL_CAPABILITIES
    )
}

private fun DeviceSnapshot.isSupportedCoolingV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun DeviceCoolingControlMode.toV1(): DeviceCoolingV1ControlMode = when (this) {
    DeviceCoolingControlMode.AUTOMATIC -> DeviceCoolingV1ControlMode.AUTOMATIC
    DeviceCoolingControlMode.MANUAL -> DeviceCoolingV1ControlMode.MANUAL
    DeviceCoolingControlMode.PROGRAM -> DeviceCoolingV1ControlMode.PROGRAM
}

private fun DeviceCoolingV1ControlMode.toApplicationMode(): DeviceCoolingControlMode = when (this) {
    DeviceCoolingV1ControlMode.AUTOMATIC -> DeviceCoolingControlMode.AUTOMATIC
    DeviceCoolingV1ControlMode.MANUAL -> DeviceCoolingControlMode.MANUAL
    DeviceCoolingV1ControlMode.PROGRAM -> DeviceCoolingControlMode.PROGRAM
}

private fun Double.toIntPercentOrNull(): Int? {
    if (!isFinite()) return null
    val rounded = roundToInt()
    return rounded.takeIf { value ->
        value in MIN_PERCENT..MAX_PERCENT && kotlin.math.abs(this - value.toDouble()) <= 0.0001
    }
}

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private suspend fun DeviceRuntimeCommandOutcome<*>.toMutationResult(
    runtime: DeviceCoolingRuntimeRepository,
    deviceUid: DeviceUid
): DeviceCoolingControlResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> runtime.currentAuthoritativeState(deviceUid)
        ?.toControlSnapshot()
        ?.let(DeviceCoolingControlResult::Available)
        ?: failed(DeviceCoolingControlFailure.Unavailable)
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated -> failed(DeviceCoolingControlFailure.NotConnected)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> failed(DeviceCoolingControlFailure.Unsupported)
    is DeviceRuntimeCommandOutcome.FirmwareError -> failed(DeviceCoolingControlFailure.Rejected)
    is DeviceRuntimeCommandOutcome.ProtocolError -> failed(DeviceCoolingControlFailure.InvalidData)
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> failed(DeviceCoolingControlFailure.Unavailable)
}

private fun failed(failure: DeviceCoolingControlFailure): DeviceCoolingControlResult =
    DeviceCoolingControlResult.Failed(failure)

private val COOLING_V1_CONTROL_CAPABILITIES = DeviceCoolingControlCapabilities(
    supportedModes = setOf(
        DeviceCoolingControlMode.AUTOMATIC,
        DeviceCoolingControlMode.MANUAL,
        DeviceCoolingControlMode.PROGRAM
    ),
    modeSelectionWritable = true,
    manualFan = DeviceCoolingManualFanCapabilities(
        minimumPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MINIMUM.toInt(),
        maximumPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_MAXIMUM.toInt(),
        stepPercent = DeviceCoolingV1Contract.Limit.FAN_PERCENT_STEP.toInt(),
        writable = true
    )
)

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
