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
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentAuthoritativeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentState
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
            failed(DeviceCoolingControlFailure.Unavailable)
        } else {
            // Current seeds presentation before collection starts. A retained snapshot may be shown
            // here; the first observe emission immediately marks it stale if authority is revoked.
            projectRead(
                device = devicesRepository.currentDevice(uid),
                state = runtime.currentState(uid),
                requireAuthority = false
            )
        }
    }

    /** Compatibility boundary only; current-state freshness is runtime-owned and never polled here. */
    override suspend fun refreshControl(deviceUid: String): DeviceCoolingControlResult =
        currentControl(deviceUid)

    override suspend fun setMode(
        deviceUid: String,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult = when (val resolved = resolveWritableRuntime(deviceUid)) {
        is WritableRuntime.Failed -> failed(resolved.failure)
        is WritableRuntime.Ready -> setMode(resolved, mode)
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult = when (val resolved = resolveWritableRuntime(deviceUid)) {
        is WritableRuntime.Failed -> failed(resolved.failure)
        is WritableRuntime.Ready -> setManualFanPercent(resolved, percent)
    }

    private suspend fun setMode(
        resolved: WritableRuntime.Ready,
        mode: DeviceCoolingControlMode
    ): DeviceCoolingControlResult {
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
        return if (config == null) {
            failed(DeviceCoolingControlFailure.NotConnected)
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
                        .toMutationResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = { failed(DeviceCoolingControlFailure.InvalidData) }
            )
        }
    }

    private suspend fun setManualFanPercent(
        resolved: WritableRuntime.Ready,
        percent: Int
    ): DeviceCoolingControlResult {
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
        return if (config == null) {
            failed(DeviceCoolingControlFailure.NotConnected)
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
                        .toMutationResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = { failed(DeviceCoolingControlFailure.InvalidData) }
            )
        }
    }

    private fun resolveWritableRuntime(deviceUid: String): WritableRuntime {
        val uid = deviceUid.toDeviceUidOrNull()
        val device = uid?.let { currentUid -> devicesRepository.currentDevice(currentUid) }
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return when {
            uid == null || device == null ->
                WritableRuntime.Failed(DeviceCoolingControlFailure.Unavailable)
            !device.isSupportedCoolingV1() ->
                WritableRuntime.Failed(DeviceCoolingControlFailure.Unsupported)
            runtime == null ->
                WritableRuntime.Failed(DeviceCoolingControlFailure.Unavailable)
            else -> WritableRuntime.Ready(uid, runtime)
        }
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
        value in MIN_PERCENT..MAX_PERCENT &&
            kotlin.math.abs(this - value.toDouble()) <= PERCENT_ROUNDING_EPSILON
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
private const val PERCENT_ROUNDING_EPSILON = 0.0001
