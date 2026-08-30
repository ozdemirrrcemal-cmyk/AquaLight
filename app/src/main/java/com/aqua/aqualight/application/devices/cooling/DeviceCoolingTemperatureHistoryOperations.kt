package com.aqua.aqualight.application.devices.cooling

interface DeviceCoolingTemperatureHistoryOperations {
    suspend fun loadTemperatureHistory(
        deviceUid: String,
        range: DeviceCoolingTemperatureHistoryRange
    ): DeviceCoolingTemperatureHistoryLoadResult
}

enum class DeviceCoolingTemperatureHistoryRange {
    HOURS_24,
    DAYS_7,
    DAYS_30
}

sealed interface DeviceCoolingTemperatureHistoryLoadResult {
    data class Loaded(
        val snapshot: DeviceCoolingTemperatureHistorySnapshot
    ) : DeviceCoolingTemperatureHistoryLoadResult

    data object Unsupported : DeviceCoolingTemperatureHistoryLoadResult
    data object Unavailable : DeviceCoolingTemperatureHistoryLoadResult
}

data class DeviceCoolingTemperatureHistorySnapshot(
    val range: DeviceCoolingTemperatureHistoryRange,
    val generatedAtEpochMillis: Long,
    val minimumTemperatureC: Double?,
    val averageTemperatureC: Double?,
    val maximumTemperatureC: Double?,
    val points: List<DeviceCoolingTemperatureHistoryPoint>,
    val dailySummaries: List<DeviceCoolingDailyTemperatureSummary>
)

data class DeviceCoolingTemperatureHistoryPoint(
    val sampledAtEpochMillis: Long,
    val temperatureC: Double
)

data class DeviceCoolingDailyTemperatureSummary(
    val dayStartEpochMillis: Long,
    val minimumTemperatureC: Double,
    val averageTemperatureC: Double,
    val maximumTemperatureC: Double
)
