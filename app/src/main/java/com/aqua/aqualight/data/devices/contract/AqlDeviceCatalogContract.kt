package com.aqua.aqualight.data.devices.contract

/** Exact, case-sensitive feature keys emitted by the commercial firmware catalog. */
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
    COOLING_PROGRAM("COOLING_PROGRAM"),
    COOLING_HISTORY("COOLING_HISTORY"),
    COOLING_SILENT_MODE("COOLING_SILENT_MODE"),
    COOLING_POWER_ESTIMATE("COOLING_POWER_ESTIMATE"),
    ROOM_AMBIENT_READ("ROOM_AMBIENT_READ"),
    HUMIDITY_READ("HUMIDITY_READ"),
    TIMER_CONTROL("TIMER_CONTROL"),
    TIMER_MANUAL_RUN("TIMER_MANUAL_RUN"),
    TIMER_CHANNEL_DISPLAY_NAME("TIMER_CHANNEL_DISPLAY_NAME"),
    DOSING_CONTROL("DOSING_CONTROL"),
    DOSING_CALIBRATION("DOSING_CALIBRATION"),
    DOSING_RESERVOIR_TRACKING("DOSING_RESERVOIR_TRACKING"),
    DOSING_CHANNEL_DISPLAY_NAME("DOSING_CHANNEL_DISPLAY_NAME"),
    COOLING_FAN_DISPLAY_NAME("COOLING_FAN_DISPLAY_NAME"),
    OTA_UPDATE("OTA_UPDATE");

    companion object {
        private val byWireValue = entries.associateBy(AqlDeviceFeatureKey::wireValue)

        fun fromWireExact(value: String): AqlDeviceFeatureKey? = byWireValue[value]
    }
}

/** Exact, case-sensitive screen keys emitted by the commercial firmware catalog. */
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
    COOLING_PROGRAM("COOLING_PROGRAM"),
    COOLING_HISTORY("COOLING_HISTORY"),
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

        fun fromWireExact(value: String): AqlDeviceScreenKey? = byWireValue[value]
    }
}

sealed interface AqlCatalogKeySet<out T> {
    data class Valid<T>(val values: Set<T>) : AqlCatalogKeySet<T>

    data class Invalid(val unknownWireValues: Set<String>) : AqlCatalogKeySet<Nothing>
}

internal fun Iterable<String>.parseAqlDeviceFeatureKeysExact(): AqlCatalogKeySet<AqlDeviceFeatureKey> =
    parseExact(AqlDeviceFeatureKey::fromWireExact)

internal fun Iterable<String>.parseAqlDeviceScreenKeysExact(): AqlCatalogKeySet<AqlDeviceScreenKey> =
    parseExact(AqlDeviceScreenKey::fromWireExact)

private fun <T> Iterable<String>.parseExact(parser: (String) -> T?): AqlCatalogKeySet<T> {
    val values = linkedSetOf<T>()
    val unknown = linkedSetOf<String>()
    for (wireValue in this) {
        val parsed = parser(wireValue)
        if (parsed == null) {
            unknown += wireValue
        } else {
            values += parsed
        }
    }
    return if (unknown.isEmpty()) {
        AqlCatalogKeySet.Valid(values)
    } else {
        AqlCatalogKeySet.Invalid(unknown)
    }
}
