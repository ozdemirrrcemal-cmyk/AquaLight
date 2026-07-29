package com.aqua.aqualight.data.devices.model

/** Exact boolean keys emitted by `device.status.get.modules`. */
enum class DeviceRuntimeModuleKey(val wireValue: String) {
    LIGHT("light"),
    COOLING("cooling"),
    TEMPERATURE("temperature"),
    TIMER_API("timerApi"),
    TIMER_ENGINE("timerEngine"),
    DOSING("dosing"),
    NETWORK("network"),
    DISCOVERY("discovery"),
    FIRMWARE("firmware"),
    SYSTEM("system");

    companion object {
        private val byWireValue = entries.associateBy(DeviceRuntimeModuleKey::wireValue)

        fun fromWireExact(value: String): DeviceRuntimeModuleKey? = byWireValue[value]
    }
}

/**
 * Complete runtime module object reported by firmware.
 *
 * `timerApi` is the standalone Timer product API. `timerEngine` is an internal scheduling engine and
 * may be enabled by Dosing products without exposing Timer screens or commands.
 */
data class DeviceRuntimeModules(
    val light: Boolean,
    val cooling: Boolean,
    val temperature: Boolean,
    val timerApi: Boolean,
    val timerEngine: Boolean,
    val dosing: Boolean,
    val network: Boolean,
    val discovery: Boolean,
    val firmware: Boolean,
    val system: Boolean
) {
    operator fun get(key: DeviceRuntimeModuleKey): Boolean = when (key) {
        DeviceRuntimeModuleKey.LIGHT -> light
        DeviceRuntimeModuleKey.COOLING -> cooling
        DeviceRuntimeModuleKey.TEMPERATURE -> temperature
        DeviceRuntimeModuleKey.TIMER_API -> timerApi
        DeviceRuntimeModuleKey.TIMER_ENGINE -> timerEngine
        DeviceRuntimeModuleKey.DOSING -> dosing
        DeviceRuntimeModuleKey.NETWORK -> network
        DeviceRuntimeModuleKey.DISCOVERY -> discovery
        DeviceRuntimeModuleKey.FIRMWARE -> firmware
        DeviceRuntimeModuleKey.SYSTEM -> system
    }

    val enabled: Set<DeviceRuntimeModuleKey>
        get() = DeviceRuntimeModuleKey.entries.filterTo(linkedSetOf()) { key -> this[key] }

    val exposesStandaloneTimerApi: Boolean
        get() = timerApi

    val usesInternalTimerEngine: Boolean
        get() = timerEngine
}
