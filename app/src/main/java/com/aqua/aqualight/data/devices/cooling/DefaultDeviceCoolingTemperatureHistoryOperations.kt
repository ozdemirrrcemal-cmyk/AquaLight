package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingDailyTemperatureSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1Contract
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1History
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1HistoryGetPayload
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1HistoryRange

/**
 * Cool Pro 1F history adapter over the single central Cooling runtime repository.
 *
 * Historical data is an on-demand query rather than mutable runtime state. The command still runs
 * through the shared Cooling repository and its connection-generation authority so a response from
 * a superseded socket generation can never be projected into presentation.
 */
internal class DefaultDeviceCoolingTemperatureHistoryOperations(
    private val devicesRepository: DevicesRepository
) : DeviceCoolingTemperatureHistoryOperations {

    override suspend fun loadTemperatureHistory(
        deviceUid: String,
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult {
        val uid = deviceUid.toCoolingHistoryUidOrNull()
        val device = uid?.let(devicesRepository::currentDevice)
        val runtime = uid?.let { devicesRepository.runtimeModules()?.cooling }
        return when {
            uid == null || device == null -> DeviceCoolingTemperatureHistoryLoadResult.Unavailable
            !device.isSupportedCoolingHistoryV1() ->
                DeviceCoolingTemperatureHistoryLoadResult.Unsupported
            runtime == null -> DeviceCoolingTemperatureHistoryLoadResult.Unavailable
            else -> when (
                val outcome = runtime.requestHistory(
                    uid,
                    DeviceCoolingV1HistoryGetPayload(range.toV1())
                )
            ) {
                is DeviceRuntimeCommandOutcome.Success -> {
                    if (!runtime.isAuthoritative(uid, outcome.generation)) {
                        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
                    } else {
                        runCatching {
                            outcome.value.toApplicationSnapshot(expectedRange = range)
                        }.fold(
                            onSuccess = DeviceCoolingTemperatureHistoryLoadResult::Loaded,
                            onFailure = { DeviceCoolingTemperatureHistoryLoadResult.Unavailable }
                        )
                    }
                }
                is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                    DeviceCoolingTemperatureHistoryLoadResult.Unsupported
                is DeviceRuntimeCommandOutcome.NotConnected,
                is DeviceRuntimeCommandOutcome.NotAuthenticated,
                is DeviceRuntimeCommandOutcome.FirmwareError,
                is DeviceRuntimeCommandOutcome.ProtocolError,
                is DeviceRuntimeCommandOutcome.SendFailed,
                is DeviceRuntimeCommandOutcome.Timeout,
                is DeviceRuntimeCommandOutcome.Cancelled ->
                    DeviceCoolingTemperatureHistoryLoadResult.Unavailable
            }
        }
    }
}

private fun DeviceCoolingV1History.toApplicationSnapshot(
    expectedRange: DeviceCoolingTemperatureHistoryRange
): DeviceCoolingTemperatureHistorySnapshot {
    require(range == expectedRange.toV1()) {
        "Cooling history response range does not match the requested range."
    }
    return DeviceCoolingTemperatureHistorySnapshot(
        range = expectedRange,
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
        dailySummaries = days.map { day ->
            DeviceCoolingDailyTemperatureSummary(
                dayStartEpochMillis = day.dayStartAtMs,
                minimumTemperatureC = day.minimumTemperatureC,
                averageTemperatureC = day.averageTemperatureC,
                maximumTemperatureC = day.maximumTemperatureC
            )
        }
    )
}

private fun DeviceCoolingTemperatureHistoryRange.toV1(): DeviceCoolingV1HistoryRange = when (this) {
    DeviceCoolingTemperatureHistoryRange.HOURS_24 -> DeviceCoolingV1HistoryRange.HOURS_24
    DeviceCoolingTemperatureHistoryRange.DAYS_7 -> DeviceCoolingV1HistoryRange.DAYS_7
    DeviceCoolingTemperatureHistoryRange.DAYS_30 -> DeviceCoolingV1HistoryRange.DAYS_30
}

private fun DeviceSnapshot.isSupportedCoolingHistoryV1(): Boolean =
    product.family == DeviceFamily.COOLING &&
        product.productKey == DeviceCoolingV1Contract.PRODUCT_KEY

private fun String.toCoolingHistoryUidOrNull(): DeviceUid? = trim()
    .takeIf(String::isNotBlank)
    ?.let(::DeviceUid)
