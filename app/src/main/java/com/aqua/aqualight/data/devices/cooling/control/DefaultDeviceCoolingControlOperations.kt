package com.aqua.aqualight.data.devices.cooling.control

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmCode
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSeverity
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAlarmSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingFanHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingSensorHealth
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTelemetrySnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlFailure
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlMode
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlOperations
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlReason
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlResult
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingControlSnapshot
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingManualFanCapabilities
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingProgramRuntimeSnapshot
import com.aqua.aqualight.data.devices.cooling.v1.DeviceCoolingV1FailureMapper
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeRepository
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentAuthoritativeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Alarm
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ControlMode
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ManualApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1OperatingState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1StatusDocument
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Telemetry
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
            DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
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
        is WritableRuntime.Failed -> DeviceCoolingControlResult.Failed(resolved.failure)
        is WritableRuntime.Ready -> setMode(resolved, mode)
    }

    override suspend fun setManualFanPercent(
        deviceUid: String,
        percent: Int
    ): DeviceCoolingControlResult = when (val resolved = resolveWritableRuntime(deviceUid)) {
        is WritableRuntime.Failed -> DeviceCoolingControlResult.Failed(resolved.failure)
        is WritableRuntime.Ready -> setManualFanPercent(resolved, percent)
    }

    private suspend fun setMode(
        resolved: WritableRuntime.Ready,
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
                        .toMutationResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = {
                    DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
                }
            )
        }
    }

    private suspend fun setManualFanPercent(
        resolved: WritableRuntime.Ready,
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
                        .toMutationResult(resolved.runtime, resolved.deviceUid)
                },
                onFailure = {
                    DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
                }
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
    device == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    !device.isSupportedCoolingV1() ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unsupported)
    state == null -> DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    requireAuthority && !state.authoritative ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    else -> state.toControlSnapshot()
        ?.let(DeviceCoolingControlResult::Available)
        ?: DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
}

private fun DeviceCoolingRuntimeState.toControlSnapshot(): DeviceCoolingControlSnapshot? {
    val config = config ?: return null
    val status = status ?: return null
    val live = telemetry
    val mode = (live?.controlMode ?: status.control.controlMode).toApplicationMode()
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
        capabilities = status.toApplicationCapabilities(),
        telemetry = live?.toApplicationTelemetrySnapshot(),
        operatingState = (live?.operatingState ?: status.control.operatingState)
            .toApplicationOperatingState(),
        controlReason = (live?.controlReason ?: status.control.controlReason)
            .toApplicationControlReason(),
        targetFanPercent = (live?.fan?.targetPercent ?: status.control.targetPercent)
            .toIntPercentOrNull(),
        manualActive = live?.manualActive ?: status.control.manualActive,
        programRuntime = status.toApplicationProgramRuntime(live)
    )
}

private fun DeviceCoolingV1StatusDocument.toApplicationCapabilities():
    DeviceCoolingControlCapabilities {
    val supportedModes = policy.controlModes.mapTo(linkedSetOf()) { mode ->
        mode.toApplicationMode()
    }
    val fanPolicy = policy.fanPercent
    val manualSupported = DeviceCoolingControlMode.MANUAL in supportedModes &&
        topology.fanOutputs.any { fan -> fan.fanKey == DeviceCoolingV1Contract.FAN_KEY }
    val minimumPercent = fanPolicy.minimumPercent.toIntPercentOrNull()
    val maximumPercent = fanPolicy.maximumPercent.toIntPercentOrNull()
    val stepPercent = fanPolicy.stepPercent.toPositiveIntPercentOrNull()
    return DeviceCoolingControlCapabilities(
        supportedModes = supportedModes,
        modeSelectionWritable = true,
        manualFan = if (
            manualSupported &&
            minimumPercent != null &&
            maximumPercent != null
        ) {
            DeviceCoolingManualFanCapabilities(
                minimumPercent = minimumPercent,
                maximumPercent = maximumPercent,
                stepPercent = stepPercent,
                writable = stepPercent != null
            )
        } else {
            null
        }
    )
}

private fun DeviceCoolingV1StatusDocument.toApplicationProgramRuntime(
    live: DeviceCoolingV1Telemetry?
): DeviceCoolingProgramRuntimeSnapshot = DeviceCoolingProgramRuntimeSnapshot(
    persistedRevision = program.programRevision,
    evaluatedRevision = live?.programRevision ?: program.evaluatedProgramRevision,
    slotCount = program.slotCount,
    clockReady = live?.clockReady ?: program.clockReady,
    currentMinuteOfDay = live?.currentMinuteOfDay ?: program.currentMinuteOfDay,
    activeSlotIndex = live?.activeProgramSlotIndex ?: program.activeSlotIndex
)

private fun DeviceCoolingV1Telemetry.toApplicationTelemetrySnapshot(): DeviceCoolingTelemetrySnapshot {
    val ambient = sensors.firstOrNull { sensor ->
        sensor.sensorKey == DeviceCoolingV1Contract.AMBIENT_SENSOR_KEY
    }
    return DeviceCoolingTelemetrySnapshot(
        roomTemperatureC = ambient?.temperatureC.takeIf { ambient?.readingValid == true },
        humidityPercent = ambient?.humidityPercent.takeIf { ambient?.readingValid == true },
        powerWatts = power.powerWatts,
        estimatedKwhPerDay = power.estimatedKwhPerDay,
        fanHealth = when (healthSummary.fanHealth) {
            "UNVERIFIED" -> DeviceCoolingFanHealth.UNVERIFIED
            "HARDWARE_FAULT" -> DeviceCoolingFanHealth.HARDWARE_FAULT
            else -> DeviceCoolingFanHealth.UNKNOWN
        },
        sensorHealth = when (healthSummary.sensorHealth) {
            "OK" -> DeviceCoolingSensorHealth.OK
            "WARNING" -> DeviceCoolingSensorHealth.WARNING
            "CRITICAL" -> DeviceCoolingSensorHealth.CRITICAL
            else -> DeviceCoolingSensorHealth.UNKNOWN
        },
        alarms = alarms.map(DeviceCoolingV1Alarm::toApplicationAlarm),
        activeAlarmCount = healthSummary.activeAlarmCount,
        highestAlarmSeverity = healthSummary.highestAlarmSeverity.toApplicationAlarmSeverity()
    )
}

private fun DeviceCoolingV1Alarm.toApplicationAlarm(): DeviceCoolingAlarmSnapshot =
    DeviceCoolingAlarmSnapshot(
        code = when (code) {
            "WATER_SENSOR_FAULT" -> DeviceCoolingAlarmCode.WATER_SENSOR_FAULT
            "AMBIENT_SENSOR_FAULT" -> DeviceCoolingAlarmCode.AMBIENT_SENSOR_FAULT
            "FAN_HARDWARE_FAULT" -> DeviceCoolingAlarmCode.FAN_HARDWARE_FAULT
            "CLOCK_UNSYNCED" -> DeviceCoolingAlarmCode.CLOCK_UNSYNCED
            "HISTORY_STORAGE_FAULT" -> DeviceCoolingAlarmCode.HISTORY_STORAGE_FAULT
            "CONFIG_STORAGE_FAULT" -> DeviceCoolingAlarmCode.CONFIG_STORAGE_FAULT
            else -> DeviceCoolingAlarmCode.UNKNOWN
        },
        severity = severity.toApplicationAlarmSeverity(),
        active = active,
        latched = latched
    )

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

private fun DeviceCoolingV1OperatingState.toApplicationOperatingState():
    DeviceCoolingOperatingState = when (this) {
    DeviceCoolingV1OperatingState.IDLE -> DeviceCoolingOperatingState.IDLE
    DeviceCoolingV1OperatingState.COOLING -> DeviceCoolingOperatingState.COOLING
    DeviceCoolingV1OperatingState.MANUAL -> DeviceCoolingOperatingState.MANUAL
    DeviceCoolingV1OperatingState.PROGRAM -> DeviceCoolingOperatingState.PROGRAM
    DeviceCoolingV1OperatingState.FAULT -> DeviceCoolingOperatingState.FAULT
}

private fun String.toApplicationControlReason(): DeviceCoolingControlReason =
    DeviceCoolingControlReason.values().firstOrNull { reason -> reason.name == this }
        ?: DeviceCoolingControlReason.UNKNOWN

private fun String.toApplicationAlarmSeverity(): DeviceCoolingAlarmSeverity = when (this) {
    "NONE" -> DeviceCoolingAlarmSeverity.NONE
    "WARNING" -> DeviceCoolingAlarmSeverity.WARNING
    "CRITICAL" -> DeviceCoolingAlarmSeverity.CRITICAL
    else -> DeviceCoolingAlarmSeverity.UNKNOWN
}

private fun Double.toIntPercentOrNull(): Int? {
    if (!isFinite()) return null
    val rounded = roundToInt()
    return rounded.takeIf { value ->
        value in MIN_PERCENT..MAX_PERCENT &&
            kotlin.math.abs(this - value.toDouble()) <= PERCENT_ROUNDING_EPSILON
    }
}

private fun Double.toPositiveIntPercentOrNull(): Int? =
    toIntPercentOrNull()?.takeIf { it > 0 }

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
        ?: DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.NotConnected)
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unsupported)
    is DeviceRuntimeCommandOutcome.FirmwareError -> DeviceCoolingControlResult.Failed(
        DeviceCoolingControlFailure.Rejected(DeviceCoolingV1FailureMapper.map(this))
    )
    is DeviceRuntimeCommandOutcome.ProtocolError ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.InvalidData)
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled ->
        DeviceCoolingControlResult.Failed(DeviceCoolingControlFailure.Unavailable)
}

private const val MIN_PERCENT = 0
private const val MAX_PERCENT = 100
private const val PERCENT_ROUNDING_EPSILON = 0.0001
