package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingDailyTemperatureSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.DeviceRuntimeModuleProvider
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingHistoryRange
import com.aqua.aqualight.data.devices.runtime.modules.cooling.DeviceCoolingHistorySnapshot
import kotlinx.coroutines.delay

/** Firmware-only Cooling history adapter. No local samples or synthetic history are produced here. */
internal class DefaultDeviceCoolingTemperatureHistoryOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingTemperatureHistoryOperations {

    override suspend fun loadTemperatureHistory(
        deviceUid: String,
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult = resolveRuntime(deviceUid)
        ?.load(range)
        ?: DeviceCoolingTemperatureHistoryLoadResult.Unavailable

    private fun resolveRuntime(deviceUid: String): CoolingHistoryRuntime? = deviceUid
        .trim()
        .takeIf(String::isNotBlank)
        ?.let(::DeviceUid)
        ?.takeIf { uid -> devicesRepository.currentDevice(uid) != null }
        ?.let { uid ->
            devicesRepository.runtimeModules()?.let { modules ->
                CoolingHistoryRuntime(uid = uid, modules = modules)
            }
        }

    private suspend fun CoolingHistoryRuntime.load(
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult = if (
        devicesRepository.connectRuntime(deviceUid).isFailure
    ) {
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
    } else {
        requestWithRetry {
            modules.cooling.requestHistory(
                deviceUid = deviceUid,
                range = range.toRuntimeRange()
            )
        }.toLoadResult()
    }
}

private data class CoolingHistoryRuntime(
    val deviceUid: DeviceUid,
    val modules: DeviceRuntimeModuleProvider
)

private fun DeviceRuntimeCommandOutcome<DeviceCoolingHistorySnapshot>.toLoadResult():
    DeviceCoolingTemperatureHistoryLoadResult = when (this) {
    is DeviceRuntimeCommandOutcome.Success ->
        DeviceCoolingTemperatureHistoryLoadResult.Loaded(value.toApplicationSnapshot())
    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
        DeviceCoolingTemperatureHistoryLoadResult.Unsupported
    is DeviceRuntimeCommandOutcome.FirmwareError -> if (code.isUnsupportedHistoryCode()) {
        DeviceCoolingTemperatureHistoryLoadResult.Unsupported
    } else {
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
    }
    is DeviceRuntimeCommandOutcome.NotConnected,
    is DeviceRuntimeCommandOutcome.NotAuthenticated,
    is DeviceRuntimeCommandOutcome.SendFailed,
    is DeviceRuntimeCommandOutcome.Timeout,
    is DeviceRuntimeCommandOutcome.ProtocolError,
    is DeviceRuntimeCommandOutcome.Cancelled ->
        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
}

private suspend fun requestWithRetry(
    request: suspend () -> DeviceRuntimeCommandOutcome<DeviceCoolingHistorySnapshot>
): DeviceRuntimeCommandOutcome<DeviceCoolingHistorySnapshot> {
    var outcome = request()
    repeat(HISTORY_REQUEST_MAX_ATTEMPTS - 1) {
        if (!outcome.isTransientReadFailure()) return outcome
        delay(HISTORY_REQUEST_RETRY_DELAY_MILLIS)
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

private fun DeviceCoolingTemperatureHistoryRange.toRuntimeRange(): DeviceCoolingHistoryRange = when (this) {
    DeviceCoolingTemperatureHistoryRange.HOURS_24 -> DeviceCoolingHistoryRange.HOURS_24
    DeviceCoolingTemperatureHistoryRange.DAYS_7 -> DeviceCoolingHistoryRange.DAYS_7
    DeviceCoolingTemperatureHistoryRange.DAYS_30 -> DeviceCoolingHistoryRange.DAYS_30
}

private fun DeviceCoolingHistorySnapshot.toApplicationSnapshot():
    DeviceCoolingTemperatureHistorySnapshot = DeviceCoolingTemperatureHistorySnapshot(
    range = when (range) {
        DeviceCoolingHistoryRange.HOURS_24 -> DeviceCoolingTemperatureHistoryRange.HOURS_24
        DeviceCoolingHistoryRange.DAYS_7 -> DeviceCoolingTemperatureHistoryRange.DAYS_7
        DeviceCoolingHistoryRange.DAYS_30 -> DeviceCoolingTemperatureHistoryRange.DAYS_30
    },
    generatedAtEpochMillis = generatedAtMs,
    minimumTemperatureC = summary.minimumTemperatureC,
    averageTemperatureC = summary.averageTemperatureC,
    maximumTemperatureC = summary.maximumTemperatureC,
    points = samples.map { sample ->
        DeviceCoolingTemperatureHistoryPoint(
            sampledAtEpochMillis = sample.sampledAtMs,
            temperatureC = sample.temperatureC
        )
    },
    dailySummaries = days
        .sortedByDescending { day -> day.dayStartAtMs }
        .map { day ->
            DeviceCoolingDailyTemperatureSummary(
                dayStartEpochMillis = day.dayStartAtMs,
                minimumTemperatureC = day.minimumTemperatureC,
                averageTemperatureC = day.averageTemperatureC,
                maximumTemperatureC = day.maximumTemperatureC
            )
        }
)

private fun String.isUnsupportedHistoryCode(): Boolean {
    val normalized = lowercase()
    return "unsupported" in normalized || "unknown" in normalized || "not_implemented" in normalized
}

private const val HISTORY_REQUEST_MAX_ATTEMPTS = 6
private const val HISTORY_REQUEST_RETRY_DELAY_MILLIS = 250L
