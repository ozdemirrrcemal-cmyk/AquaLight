package com.aqua.aqualight.data.devices.cooling

import com.aqua.aqualight.application.devices.cooling.DeviceCoolingCommandFailure
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingDailyTemperatureSummary
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryLoadResult
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryOperations
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryPoint
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistoryRange
import com.aqua.aqualight.application.devices.cooling.DeviceCoolingTemperatureHistorySnapshot
import com.aqua.aqualight.data.devices.cooling.v1.DeviceCoolingV1FailureMapper
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import com.aqua.aqualight.data.devices.runtime.modules.cooling.currentAuthoritativeState
import com.aqua.aqualight.data.devices.runtime.modules.cooling.isAuthoritative
import com.aqua.aqualight.data.devices.runtime.modules.cooling.v1.DeviceCoolingV1ChartSource
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
            else -> {
                val requestedRange = range.toV1()
                val status = runtime.currentAuthoritativeState(uid)?.status
                    ?: return DeviceCoolingTemperatureHistoryLoadResult.Unavailable
                val expectedChartSource = status.history.chartSources[requestedRange]
                    ?: return DeviceCoolingTemperatureHistoryLoadResult.Unsupported
                when (
                    val outcome = runtime.requestHistory(
                        uid,
                        DeviceCoolingV1HistoryGetPayload(requestedRange)
                    )
                ) {
                    is DeviceRuntimeCommandOutcome.Success -> {
                        if (!runtime.isAuthoritative(uid, outcome.generation)) {
                            DeviceCoolingTemperatureHistoryLoadResult.Unavailable
                        } else {
                            runCatching {
                                val currentAuthority = runtime.currentAuthoritativeState(uid)
                                require(
                                    currentAuthority?.connectionGeneration == outcome.generation
                                )
                                require(
                                    currentAuthority.status
                                        ?.history
                                        ?.chartSources
                                        ?.get(requestedRange) == expectedChartSource
                                )
                                outcome.value.toApplicationSnapshot(
                                    expectedRange = range,
                                    expectedChartSource = expectedChartSource
                                )
                            }.fold(
                                onSuccess = DeviceCoolingTemperatureHistoryLoadResult::Loaded,
                                onFailure = {
                                    DeviceCoolingTemperatureHistoryLoadResult.Rejected(
                                        DeviceCoolingCommandFailure.PROTOCOL_ERROR
                                    )
                                }
                            )
                        }
                    }
                    is DeviceRuntimeCommandOutcome.UnsupportedByDevice ->
                        DeviceCoolingTemperatureHistoryLoadResult.Unsupported
                    is DeviceRuntimeCommandOutcome.NotConnected,
                    is DeviceRuntimeCommandOutcome.NotAuthenticated,
                    is DeviceRuntimeCommandOutcome.SendFailed,
                    is DeviceRuntimeCommandOutcome.Timeout,
                    is DeviceRuntimeCommandOutcome.Cancelled ->
                        DeviceCoolingTemperatureHistoryLoadResult.Unavailable
                    is DeviceRuntimeCommandOutcome.FirmwareError ->
                        DeviceCoolingTemperatureHistoryLoadResult.Rejected(
                            DeviceCoolingV1FailureMapper.map(outcome)
                        )
                    is DeviceRuntimeCommandOutcome.ProtocolError ->
                        DeviceCoolingTemperatureHistoryLoadResult.Rejected(
                            DeviceCoolingCommandFailure.PROTOCOL_ERROR
                        )
                }
            }
        }
    }
}

private fun DeviceCoolingV1History.toApplicationSnapshot(
    expectedRange: DeviceCoolingTemperatureHistoryRange,
    expectedChartSource: DeviceCoolingV1ChartSource
): DeviceCoolingTemperatureHistorySnapshot {
    require(range == expectedRange.toV1()) {
        "Cooling history response range does not match the requested range."
    }
    require(chartSource == expectedChartSource) {
        "Cooling history chart source does not match firmware status policy."
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
