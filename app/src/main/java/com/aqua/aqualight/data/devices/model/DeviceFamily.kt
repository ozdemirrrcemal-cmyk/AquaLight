package com.aqua.aqualight.data.devices.model

/** Product family is supplied by firmware. Android must not infer it from model names. */
enum class DeviceFamily(val wireValue: String) {
    LIGHT("light"),
    TIMER("timer"),
    DOSING("dosing"),
    COOLING("cooling"),
    UNKNOWN("unknown");

    companion object {
        private val exactFamilies = entries
            .filterNot { family -> family == UNKNOWN }
            .associateBy(DeviceFamily::wireValue)

        /** Strict commercial parser. No trimming, casing conversion, aliases, or fallback. */
        fun fromWireExact(value: String): DeviceFamily? = exactFamilies[value]

        fun fromWire(value: String?): DeviceFamily = when (value?.trim()?.lowercase()) {
            LIGHT.wireValue -> LIGHT
            TIMER.wireValue -> TIMER
            DOSING.wireValue -> DOSING
            COOLING.wireValue -> COOLING
            else -> UNKNOWN
        }
    }
}
