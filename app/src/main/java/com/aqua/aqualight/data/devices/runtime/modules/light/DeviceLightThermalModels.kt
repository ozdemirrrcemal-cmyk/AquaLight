package com.aqua.aqualight.data.devices.runtime.modules.light

enum class DeviceLightThermalMode(val wireValue: String) {
    AUTO("Auto"),
    ON("On"),
    OFF("Off");

    companion object {
        fun fromWireExact(value: String): DeviceLightThermalMode? =
            entries.firstOrNull { mode -> mode.wireValue == value }
    }
}

data class DeviceLightThermalTopology(
    val fanOutputCount: Int,
    val temperatureSensorCount: Int
)

data class DeviceLightThermalConfig(
    val mode: DeviceLightThermalMode,
    val minTemperatureC: Double,
    val maxTemperatureC: Double
)

data class DeviceLightThermalTemperature(
    val sensorKey: String,
    val sensorIndex: Int,
    val readingValid: Boolean,
    val temperatureC: Double?,
    val sampledAtMs: Long
)

data class DeviceLightThermalProtection(
    val enabled: Boolean,
    val active: Boolean,
    val thresholdC: Double
)

data class DeviceLightThermalFanHardware(
    val editable: Boolean,
    val gpio: Int,
    val ledcChannel: Int,
    val pwmFrequencyHz: Long,
    val pwmResolutionBits: Int,
    val invert: Boolean,
    val pwmOutputHealth: String,
    val health: String,
    val physicalFeedbackAvailable: Boolean
)

data class DeviceLightThermalFanStatus(
    val fanKey: String,
    val index: Int,
    val name: String,
    val regime: String,
    val valueNow: Double,
    val valueAuto: Double,
    val percentNow: Double,
    val percentAuto: Double,
    val hardware: DeviceLightThermalFanHardware
)

data class DeviceLightThermalRuntimeCapabilities(
    val event: String,
    val statusEvent: String,
    val sensorFailSafeActive: Boolean,
    val automaticOutputCycleHealthy: Boolean,
    val hardwareEditable: Boolean,
    val fanMappingEditable: Boolean,
    val sensorMappingEditable: Boolean
)

data class DeviceLightThermalStatus(
    val schema: String,
    val schemaVersion: Int,
    val productKey: String,
    val uptimeMs: Long,
    val topology: DeviceLightThermalTopology,
    val config: DeviceLightThermalConfig,
    val temperature: DeviceLightThermalTemperature,
    val lightProtection: DeviceLightThermalProtection,
    val fans: List<DeviceLightThermalFanStatus>,
    val runtime: DeviceLightThermalRuntimeCapabilities
)
