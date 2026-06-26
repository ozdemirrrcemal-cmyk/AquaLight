package com.aqua.aqualight.data.devices.model

/** Product family is supplied by firmware. Android must not infer it from model names. */
enum class DeviceFamily(val wireValue: String) {
    LIGHT("light"),
    TIMER("timer"),
    DOSING("dosing"),
    COOLING("cooling"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String?): DeviceFamily = when (value?.trim()?.lowercase()) {
            LIGHT.wireValue -> LIGHT
            TIMER.wireValue -> TIMER
            DOSING.wireValue -> DOSING
            COOLING.wireValue -> COOLING
            else -> UNKNOWN
        }
    }
}
