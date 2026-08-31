package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticSettingsSnapshot
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingAutomaticTemperaturePolicy
import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_FAN_PERCENT_MAXIMUM
import com.aqua.aqualight.application.devices.cooling.DEVICE_COOLING_FAN_PERCENT_MINIMUM
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingConfigApplyResult
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingRuntimeContract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingStatus
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Firmware-backed automatic Cooling adapter. No local shadow configuration is persisted here. */
internal class DefaultDeviceCoolingAutomaticSettingsOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingAutomaticSettingsOperations {

    override fun observeAutomaticSettings(
        deviceUid: String
    ): Flow<DeviceCoolingAutomaticSettingsSnapshot> = deviceUid.toDeviceUidOrNull()
        ?.let { uid ->
            devicesRepository.runtimeModules()?.cooling?.states?.map { states ->
                states[uid]?.status.toAutomaticSnapshot()
            }
        }
        ?.distinctUntilChanged()
        ?: flowOf(DeviceCoolingAutomaticSettingsSnapshot())

    override fun currentAutomaticSettings(
        deviceUid: String
    ): DeviceCoolingAutomaticSettingsSnapshot = deviceUid.toDeviceUidOrNull()
        ?.let { uid -> devicesRepository.runtimeModules()?.cooling?.states?.value?.get(uid)?.status }
        .toAutomaticSnapshot()

    override suspend fun refreshAutomaticSettings(deviceUid: String): Result<Unit> = runCatching {
        val uid = requireRegisteredDeviceUid(deviceUid)
        val modules = checkNotNull(devicesRepository.runtimeModules()) {
            "Device runtime is not configured."
        }
        devicesRepository.connectRuntime(uid).getOrThrow()
        requestStatusWithRetry { modules.cooling.requestStatus(uid) }.requireSuccessValue()
        Unit
    }

    override suspend fun saveAutomaticTemperatureRange(
        deviceUid: String,
        startTemperatureC: Double,
        maximumSpeedTemperatureC: Double
    ): Result<Unit> = runCatching {
        val uid = requireRegisteredDeviceUid(deviceUid)
        val modules = checkNotNull(devicesRepository.runtimeModules()) {
            "Device runtime is not configured."
        }
        devicesRepository.connectRuntime(uid).getOrThrow()

        val status = modules.cooling.states.value[uid]?.status
            ?: requestStatusWithRetry { modules.cooling.requestStatus(uid) }.requireSuccessValue()
        check(status.isAutomaticRangeEditable()) {
            "Automatic Cooling temperature range is not editable."
        }
        validateRequestedRange(
            startTemperatureC = startTemperatureC,
            maximumSpeedTemperatureC = maximumSpeedTemperatureC
        )

        val result = modules.cooling.setTemperatureRange(
            deviceUid = uid,
            minTemperatureC = startTemperatureC,
            maxTemperatureC = maximumSpeedTemperatureC,
            save = true
        ).requireSuccessValue()
        validateSavedResult(
            result = result,
            requestedStartC = startTemperatureC,
            requestedMaximumC = maximumSpeedTemperatureC
        )
    }

    private fun requireRegisteredDeviceUid(deviceUid: String): DeviceUid {
        val uid = checkNotNull(deviceUid.toDeviceUidOrNull()) { "Device uid is missing." }
        checkNotNull(devicesRepository.currentDevice(uid)) { "Device is not registered." }
        return uid
    }
}

private fun DeviceCoolingStatus?.toAutomaticSnapshot(): DeviceCoolingAutomaticSettingsSnapshot {
    val status = this ?: return DeviceCoolingAutomaticSettingsSnapshot()
    val temperature = status.temperature
        .takeIf { snapshot -> snapshot.readingValid }
        ?.temperatureC
        ?.takeIf(Double::isFinite)
    val fanPercent = status.fans
        .firstOrNull()
        ?.percentNow
        ?.takeIf(Double::isFinite)
        ?.coerceIn(
            DEVICE_COOLING_FAN_PERCENT_MINIMUM.toDouble(),
            DEVICE_COOLING_FAN_PERCENT_MAXIMUM.toDouble()
        )
    val available = status.supported && status.fanSupported && status.temperatureSupported
    return DeviceCoolingAutomaticSettingsSnapshot(
        available = available,
        loaded = true,
        editable = available && status.isAutomaticRangeEditable(),
        startTemperatureC = status.minTemperatureC.takeIf(Double::isFinite),
        maximumSpeedTemperatureC = status.maxTemperatureC.takeIf(Double::isFinite),
        tankTemperatureC = temperature,
        fanPercentNow = fanPercent,
        policy = AUTOMATIC_TEMPERATURE_POLICY.takeIf { available }
    )
}

private fun DeviceCoolingStatus.isAutomaticRangeEditable(): Boolean =
    runtime.supportsConfigApply &&
        runtime.supportsTemperatureRange &&
        !runtime.readOnly

private fun validateRequestedRange(
    startTemperatureC: Double,
    maximumSpeedTemperatureC: Double
) {
    val policy = AUTOMATIC_TEMPERATURE_POLICY
    require(startTemperatureC.isFinite() && maximumSpeedTemperatureC.isFinite())
    require(startTemperatureC in policy.startMinimumC..policy.startMaximumC)
    require(maximumSpeedTemperatureC in policy.maximumSpeedMinimumC..policy.maximumSpeedMaximumC)
    require(maximumSpeedTemperatureC - startTemperatureC >= policy.minimumGapC - RANGE_EPSILON)
    require(isAlignedToStep(startTemperatureC, policy.startMinimumC, policy.stepC))
    require(isAlignedToStep(maximumSpeedTemperatureC, policy.maximumSpeedMinimumC, policy.stepC))
}

private fun isAlignedToStep(value: Double, origin: Double, step: Double): Boolean {
    val units = (value - origin) / step
    return abs(units - kotlin.math.round(units)) <= RANGE_EPSILON
}

private fun validateSavedResult(
    result: DeviceCoolingConfigApplyResult,
    requestedStartC: Double,
    requestedMaximumC: Double
) {
    check(result.saveRequested && result.saved) {
        "Firmware did not persist the automatic Cooling range."
    }
    check(abs(result.config.minTemperatureC - requestedStartC) <= RANGE_EPSILON)
    check(abs(result.config.maxTemperatureC - requestedMaximumC) <= RANGE_EPSILON)
}

private suspend fun <T> requestStatusWithRetry(
    request: suspend () -> DeviceRuntimeCommandOutcome<T>
): DeviceRuntimeCommandOutcome<T> {
    var outcome = request()
    repeat(STATUS_REQUEST_MAX_ATTEMPTS - 1) {
        if (!outcome.isTransientReadFailure()) return outcome
        delay(STATUS_REQUEST_RETRY_DELAY_MILLIS)
        outcome = request()
    }
    return outcome
}

private fun DeviceRuntimeCommandOutcome<*>.isTransientReadFailure(): Boolean = when (this) {
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.Cancelled -> true
    is DeviceRuntimeCommandOutcome.Success,
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice,
    is DeviceRuntimeCommandOutcome.FirmwareError,
    is DeviceRuntimeCommandOutcome.ProtocolError -> false
}

private fun <T> DeviceRuntimeCommandOutcome<T>.requireSuccessValue(): T = when (this) {
    is DeviceRuntimeCommandOutcome.Success -> value
    else -> error("Cooling runtime request failed: ${javaClass.simpleName}")
}

private fun String.toDeviceUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)

private val AUTOMATIC_TEMPERATURE_POLICY = DeviceCoolingAutomaticTemperaturePolicy(
    startMinimumC = DeviceCoolingRuntimeContract.Limit.LOWEST_MIN_C,
    startMaximumC = DeviceCoolingRuntimeContract.Limit.HIGHEST_MIN_C,
    maximumSpeedMinimumC = DeviceCoolingRuntimeContract.Limit.LOWEST_MAX_C,
    maximumSpeedMaximumC = DeviceCoolingRuntimeContract.Limit.HIGHEST_MAX_C,
    stepC = 0.5,
    minimumGapC = 0.5
)

private const val STATUS_REQUEST_MAX_ATTEMPTS = 6
private const val STATUS_REQUEST_RETRY_DELAY_MILLIS = 250L
private const val RANGE_EPSILON = 0.000_001
