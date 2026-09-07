package com.aqua.aqualight.application.devices.cooling

private const val MINIMUM_PERCENT = 0.0
private const val MAXIMUM_PERCENT = 100.0

/** Firmware-reported fan output and physical-feedback diagnostics. */
data class DeviceCoolingFanTelemetrySnapshot(
    val targetPercent: Double,
    val outputPercent: Double?,
    val rpm: Double?,
    val rpmAvailable: Boolean,
    val pwmOutputHealth: DeviceCoolingPwmOutputHealth,
    val physicalHealth: DeviceCoolingFanHealth
) {
    init {
        require(targetPercent.isValidPercent())
        require(outputPercent == null || outputPercent.isValidPercent())
        require(rpm == null || rpm.isFinite() && rpm >= 0.0)
        require(rpmAvailable || rpm == null)
    }
}

enum class DeviceCoolingPwmOutputHealth {
    OK,
    FAULT,
    UNKNOWN
}

/** Exact per-sensor state reported by firmware, without Android health inference. */
data class DeviceCoolingSensorTelemetrySnapshot(
    val kind: DeviceCoolingSensorKind,
    val present: Boolean?,
    val health: DeviceCoolingSensorReadingHealth,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val humidityPercent: Double?,
    val sampledAtUptimeMillis: Long
) {
    init {
        require(sampledAtUptimeMillis >= 0L)
        require(temperatureC == null || temperatureC.isFinite())
        require(humidityPercent == null || humidityPercent.isFinite())
    }
}

enum class DeviceCoolingSensorKind {
    WATER,
    AMBIENT,
    UNKNOWN
}

enum class DeviceCoolingSensorReadingHealth {
    OK,
    MISSING,
    TOPOLOGY_INVALID,
    CRC_ERROR,
    STALE,
    OUT_OF_RANGE,
    WARMING_UP,
    IO_ERROR,
    UNKNOWN
}

/** Firmware-owned power estimate and its availability. */
data class DeviceCoolingPowerTelemetrySnapshot(
    val source: DeviceCoolingPowerSource,
    val available: Boolean,
    val ratedPowerWatts: Double?,
    val powerWatts: Double?,
    val estimatedKwhPerDay: Double?
) {
    init {
        require(ratedPowerWatts == null || ratedPowerWatts.isFinite() && ratedPowerWatts >= 0.0)
        require(powerWatts == null || powerWatts.isFinite() && powerWatts >= 0.0)
        require(estimatedKwhPerDay == null || estimatedKwhPerDay.isFinite() && estimatedKwhPerDay >= 0.0)
    }
}

enum class DeviceCoolingPowerSource {
    ESTIMATED,
    UNKNOWN
}

private fun Double.isValidPercent(): Boolean =
    isFinite() && this in MINIMUM_PERCENT..MAXIMUM_PERCENT
