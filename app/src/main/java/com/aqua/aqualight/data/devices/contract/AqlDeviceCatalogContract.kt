package com.aqua.aqualight.data.devices.contract

/**
 * Exact feature keys emitted by the commercial firmware product catalog.
 *
 * These values are intentionally case-sensitive. Unknown values remain available in the raw
 * snapshot lists for forward compatibility, but they never unlock a menu by accident.
 */
enum class AqlDeviceFeatureKey(val wireValue: String) {
    WIFI_SETUP("WIFI_SETUP"),
    LAN_DISCOVERY("LAN_DISCOVERY"),
    LIGHT_CONTROL("LIGHT_CONTROL"),
    LIGHT_QUICK_SETUP("LIGHT_QUICK_SETUP"),
    LIGHT_PRESETS("LIGHT_PRESETS"),
    LIGHT_MOONLIGHT("LIGHT_MOONLIGHT"),
    LIGHT_ACCLIMATION("LIGHT_ACCLIMATION"),
    LIGHT_TEMPERATURE_PROTECTION("LIGHT_TEMPERATURE_PROTECTION"),
    LIGHT_FAN_CONTROL("LIGHT_FAN_CONTROL"),
    TEMPERATURE_READ("TEMPERATURE_READ"),
    COOLING_CONTROL("COOLING_CONTROL"),
    TIMER_CONTROL("TIMER_CONTROL"),
    TIMER_MANUAL_RUN("TIMER_MANUAL_RUN"),
    DOSING_CONTROL("DOSING_CONTROL"),
    DOSING_CALIBRATION("DOSING_CALIBRATION"),
    DOSING_RESERVOIR_TRACKING("DOSING_RESERVOIR_TRACKING"),
    COOLING_FAN_DISPLAY_NAME("COOLING_FAN_DISPLAY_NAME"),
    OTA_UPDATE("OTA_UPDATE");

    companion object {
        private val byWireValue = entries.associateBy(AqlDeviceFeatureKey::wireValue)

        fun fromWire(value: String): AqlDeviceFeatureKey? = byWireValue[value.trim()]
    }
}

/**
 * Exact screen keys emitted by the commercial firmware product catalog.
 */
enum class AqlDeviceScreenKey(val wireValue: String) {
    OVERVIEW("OVERVIEW"),
    LIGHT_CONTROL("LIGHT_CONTROL"),
    LIGHT_CHANNELS("LIGHT_CHANNELS"),
    LIGHT_SCHEDULE("LIGHT_SCHEDULE"),
    LIGHT_PRESETS("LIGHT_PRESETS"),
    LIGHT_QUICK_SETUP("LIGHT_QUICK_SETUP"),
    LIGHT_MOONLIGHT("LIGHT_MOONLIGHT"),
    LIGHT_ACCLIMATION("LIGHT_ACCLIMATION"),
    LIGHT_TEMPERATURE_PROTECTION("LIGHT_TEMPERATURE_PROTECTION"),
    LIGHT_FAN_CONTROL("LIGHT_FAN_CONTROL"),
    COOLING_CONTROL("COOLING_CONTROL"),
    TIMER_CONTROL("TIMER_CONTROL"),
    TIMER_CHANNELS("TIMER_CHANNELS"),
    TIMER_SCHEDULES("TIMER_SCHEDULES"),
    TIMER_MANUAL_RUN("TIMER_MANUAL_RUN"),
    DOSING_CONTROL("DOSING_CONTROL"),
    DOSING_CHANNELS("DOSING_CHANNELS"),
    DOSING_SCHEDULES("DOSING_SCHEDULES"),
    DOSING_CALIBRATION("DOSING_CALIBRATION"),
    DOSING_RESERVOIR("DOSING_RESERVOIR"),
    DOSING_MANUAL_RUN("DOSING_MANUAL_RUN"),
    COOLING_RULES("COOLING_RULES"),
    COOLING_FANS("COOLING_FANS"),
    COOLING_SENSOR_STATUS("COOLING_SENSOR_STATUS"),
    ADVANCED("ADVANCED");

    companion object {
        private val byWireValue = entries.associateBy(AqlDeviceScreenKey::wireValue)

        fun fromWire(value: String): AqlDeviceScreenKey? = byWireValue[value.trim()]
    }
}

internal fun Iterable<String>.toAqlDeviceFeatureKeys(): Set<AqlDeviceFeatureKey> =
    mapNotNull(AqlDeviceFeatureKey::fromWire).toSet()

internal fun Iterable<String>.toAqlDeviceScreenKeys(): Set<AqlDeviceScreenKey> =
    mapNotNull(AqlDeviceScreenKey::fromWire).toSet()
