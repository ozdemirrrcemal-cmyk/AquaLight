package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticCommandResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.application.devices.cooling.control.DeviceCoolingOperatingState
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
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigApplyPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ConfigSnapshot
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1OperatingState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

/**
 * Cool Pro 1F automatic-settings adapter over the central Cooling state owner.
 *
 * Status hydration and event freshness are runtime responsibilities. Presentation observes this
 * projection; only explicit user mutations cross back into the command layer.
 */
internal class DefaultDeviceCoolingAutomaticSettingsOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingAutomaticSettingsOperations {

    override fun observeAutomaticSettings(
        deviceUid: String
    ): Flow<DeviceCoolingAutomaticSettingsSnapshot> {
        val uid = deviceUid.toCoolingUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return if (uid == null || runtime == null) {
            flowOf(DeviceCoolingAutomaticSettingsSnapshot())
        } else {
            combine(
                devicesRepository.observeDevice(uid),
                runtime.states
            ) { device, states ->
                projectAutomatic(device, states[uid])
            }.distinctUntilChanged()
        }
    }

    override fun currentAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticSettingsSnapshot {
        val uid = deviceUid.toCoolingUidOrNull()
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return if (uid == null || runtime == null) {
            DeviceCoolingAutomaticSettingsSnapshot()
        } else {
            projectAutomatic(
                devicesRepository.currentDevice(uid),
                runtime.currentState(uid)
            )
        }
    }

    /** Compatibility boundary only. Runtime bootstrap owns status freshness; no command is sent. */
    override suspend fun refreshAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticCommandResult {
        val resolved = resolveAutomaticRuntime(deviceUid)
        if (resolved is AutomaticRuntime.Failed) return failed(resolved.failure)
        resolved as AutomaticRuntime.Ready
        return if (resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config != null) {
            DeviceCoolingAutomaticCommandResult.Success
        } else {
            failed(DeviceCoolingAutomaticFailure.NotConnected)
        }
    }

    override suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): DeviceCoolingAutomaticCommandResult = saveAutomaticConfig(
        deviceUid = deviceUid,
        startTemperatureC = startTemperatureC,
        maximumSpeedTemperatureC = maximumSpeedTemperatureC,
        silentModeEnabled = null
    )

    override suspend fun saveAutomaticSettings(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult = saveAutomaticConfig(
        deviceUid = deviceUid,
        startTemperatureC = startTemperatureC,
        maximumSpeedTemperatureC = maximumSpeedTemperatureC,
        silentModeEnabled = silentModeEnabled
    )

    private suspend fun saveAutomaticConfig(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult = when (val resolved = resolveAutomaticRuntime(deviceUid)) {
        is AutomaticRuntime.Failed -> failed(resolved.failure)
        is AutomaticRuntime.Ready -> saveAutomaticConfig(
            resolved = resolved,
            startTemperatureC = startTemperatureC,
            maximumSpeedTemperatureC = maximumSpeedTemperatureC,
            silentModeEnabled = silentModeEnabled
        )
    }

    private suspend fun saveAutomaticConfig(
        resolved: AutomaticRuntime.Ready,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double,
        silentModeEnabled: Boolean?
    ): DeviceCoolingAutomaticCommandResult {
        val config = resolved.runtime.currentAuthoritativeState(resolved.deviceUid)?.config
        return if (config == null) {
            failed(DeviceCoolingAutomaticFailure.NotConnected)
        } else {
            runCatching {
                DeviceCoolingV1ConfigApplyPayload(
                    expectedConfigRevision = config.configRevision,
                    startTemperatureC = startTemperatureC,
                    fullSpeedTemperatureC = maximumSpeedTemperatureC,
                    silentModeEnabled = silentModeEnabled
                )
            }.fold(
                onSuccess = { payload ->
                    when (val outcome = resolved.runtime.applyConfig(resolved.deviceUid, payload)) {
                        is DeviceRuntimeCommandOutcome.Success -> {
                            val committed = resolved.runtime
                                .currentAuthoritativeState(resolved.deviceUid)
                                ?.config
                            if (
                                committed?.matchesAutomaticRequest(
                                    startTemperatureC,
                                    maximumSpeedTemperatureC,
                                    silentModeEnabled
                                ) == true
                            ) {
                                DeviceCoolingAutomaticCommandResult.Success
                            } else {
                                failed(DeviceCoolingAutomaticFailure.TemporaryFailure)
                            }
                        }
                        else -> outcome.toAutomaticFailure()
                    }
                },
                onFailure = {
                    failed(DeviceCoolingAutomaticFailure.InvalidConfiguration)
                }
            )
        }
    }

    private fun resolveAutomaticRuntime(deviceUid: String): AutomaticRuntime {
        val uid = deviceUid.toCoolingUidOrNull()
        val device = uid?.let { currentUid -> devicesRepository.currentDevice(currentUid) }
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return when {
            uid == null || device == null ->
                AutomaticRuntime.Failed(DeviceCoolingAutomaticFailure.Unavailable)
            !device.isCoolingV1() ->
                AutomaticRuntime.Failed(DeviceCoolingAutomaticFailure.Unsupported)
            runtime == null ->
                AutomaticRuntime.Failed(DeviceCoolingAutomaticFailure.Unavailable)
            else -> AutomaticRuntime.Ready(uid, runtime)
        }
    }
}

private sealed interface AutomaticRuntime {
    data class Ready(
        val deviceUid: DeviceUid,
        val runtime: DeviceCoolingRuntimeRepository
    ) : AutomaticRuntime

    data class Failed(val failure: DeviceCoolingAutomaticFailure) : AutomaticRuntime
}

private fun projectAutomatic(
    device: DeviceSnapshot?,
    state: DeviceCoolingRuntimeState?
): DeviceCoolingAutomaticSettingsSnapshot = when {
    device == null -> DeviceCoolingAutomaticSettingsSnapshot()
    !device.isCoolingV1() -> DeviceCoolingAutomaticSettingsSnapshot(
        available = false,
        loaded = true
    )
    state?.config == null || state.status == null -> DeviceCoolingAutomaticSettingsSnapshot(
        available = true,
        loaded = false,
        editable = false
    )
    else -> {
        val waterTemperature = state.telemetry
            ?.sensors
            ?.firstOrNull { sensor ->
                sensor.sensorKey == DeviceCoolingV1Contract.WATER_SENSOR_KEY && sensor.readingValid
            }
            ?.temperatureC
        val statusPolicy = state.status.policy
        val automaticPolicy = statusPolicy.temperature
        val silentModeSupported = statusPolicy.silentMode.supported
        DeviceCoolingAutomaticSettingsSnapshot(
            available = true,
            loaded = true,
            editable = state.authoritative,
            startTemperatureC = state.config.startTemperatureC,
            maximumSpeedTemperatureC = state.config.fullSpeedTemperatureC,
            tankTemperatureC = waterTemperature,
            fanPercentNow = state.telemetry?.fan?.outputPercent,
            operatingState = (state.telemetry?.operatingState ?: state.status.control.operatingState)
                .toApplicationOperatingState(),
            silentModeEnabled = state.config.silentModeEnabled.takeIf { silentModeSupported },
            silentModeMaximumFanPercent = statusPolicy.silentMode.maximumPercent
                .roundToInt()
                .takeIf { silentModeSupported },
            policy = DeviceCoolingAutomaticTemperaturePolicy(
                startMinimumC = automaticPolicy.minimumC,
                startMaximumC = automaticPolicy.maximumC - automaticPolicy.minimumGapC,
                maximumSpeedMinimumC = automaticPolicy.minimumC + automaticPolicy.minimumGapC,
                maximumSpeedMaximumC = automaticPolicy.maximumC,
                stepC = automaticPolicy.stepC,
                minimumGapC = automaticPolicy.minimumGapC,
                hysteresisC = automaticPolicy.hysteresisC
            )
        )
    }
}

private fun DeviceCoolingV1OperatingState.toApplicationOperatingState():
    DeviceCoolingOperatingState = when (this) {
    DeviceCoolingV1OperatingState.IDLE -> DeviceCoolingOperatingState.IDLE
    DeviceCoolingV1OperatingState.COOLING -> DeviceCoolingOperatingState.COOLING
    DeviceCoolingV1OperatingState.MANUAL -> DeviceCoolingOperatingState.MANUAL
    DeviceCoolingV1OperatingState.PROGRAM -> DeviceCoolingOperatingState.PROGRAM
    DeviceCoolingV1OperatingState.FAULT -> DeviceCoolingOperatingState.FAULT
}

private fun DeviceCoolingV1ConfigSnapshot.matchesAutomaticRequest(
    startTemperatureC: Double,
    maximumSpeedTemperatureC: Double,
    requestedSilentModeEnabled: Boolean?
): Boolean {
    val temperaturesMatch =
        this.startTemperatureC.sameCoolingValue(startTemperatureC) &&
            fullSpeedTemperatureC.sameCoolingValue(maximumSpeedTemperatureC)
    val silentModeMatches =
        requestedSilentModeEnabled == null || silentModeEnabled == requestedSilentModeEnabled
    return temperaturesMatch && silentModeMatches
}

private fun DeviceSnapshot.isCoolingV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun String.toCoolingUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private fun DeviceRuntimeCommandOutcome<*>.toAutomaticFailure(): DeviceCoolingAutomaticCommandResult =
    when (this) {
        is DeviceRuntimeCommandOutcome.Success -> DeviceCoolingAutomaticCommandResult.Success
        is DeviceRuntimeCommandOutcome.NotConnected,
        is DeviceRuntimeCommandOutcome.NotAuthenticated -> failed(DeviceCoolingAutomaticFailure.NotConnected)
        is DeviceRuntimeCommandOutcome.UnsupportedByDevice -> failed(DeviceCoolingAutomaticFailure.Unsupported)
        is DeviceRuntimeCommandOutcome.FirmwareError -> failed(
            DeviceCoolingAutomaticFailure.Rejected(DeviceCoolingV1FailureMapper.map(this))
        )
        is DeviceRuntimeCommandOutcome.ProtocolError -> failed(
            DeviceCoolingAutomaticFailure.Rejected(
                com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure.PROTOCOL_ERROR
            )
        )
        is DeviceRuntimeCommandOutcome.SendFailed,
        is DeviceRuntimeCommandOutcome.Timeout,
        is DeviceRuntimeCommandOutcome.Cancelled ->
            failed(DeviceCoolingAutomaticFailure.TemporaryFailure)
    }

private fun failed(failure: DeviceCoolingAutomaticFailure): DeviceCoolingAutomaticCommandResult =
    DeviceCoolingAutomaticCommandResult.Failed(failure)

private fun Double.sameCoolingValue(other: Double): Boolean =
    abs(this - other) <= DeviceCoolingV1Contract.Limit.ALIGNMENT_EPSILON
