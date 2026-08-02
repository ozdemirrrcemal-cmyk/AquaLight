package com.aqua.aqualight.data.devices.contract

/** Exact Android mirror of every authenticated event emitted by firmware v1. */
object AqlWsEventContract {
    const val MODULE_TEMPERATURE = "temperature"

    const val ACTION_STATUS_CHANGED = "status.changed"
    const val ACTION_NETWORK_STATE_CHANGED = "state.changed"
    const val ACTION_TEMPERATURE_CHANGED = "changed"
    const val ACTION_OTA_PROGRESS = "ota.progress"
    const val ACTION_OTA_COMPLETED = "ota.completed"
    const val ACTION_SYSTEM_RESTARTING = "restarting"

    data class Definition(
        val module: String,
        val action: String
    ) {
        val qualifiedName: String = "$module.$action"
    }

    private val registeredEvents = linkedSetOf(
        Definition(AqlWsContract.MODULE_DEVICE, ACTION_STATUS_CHANGED),
        Definition(AqlWsContract.MODULE_NETWORK, ACTION_NETWORK_STATE_CHANGED),
        Definition(AqlWsContract.MODULE_LIGHT, ACTION_STATUS_CHANGED),
        Definition(AqlWsContract.MODULE_COOLING, ACTION_STATUS_CHANGED),
        Definition(AqlWsContract.MODULE_TIMER, ACTION_STATUS_CHANGED),
        Definition(AqlWsContract.MODULE_DOSING, ACTION_STATUS_CHANGED),
        Definition(MODULE_TEMPERATURE, ACTION_TEMPERATURE_CHANGED),
        Definition(AqlWsContract.MODULE_TIME, ACTION_STATUS_CHANGED),
        Definition(AqlWsContract.MODULE_FIRMWARE, ACTION_OTA_PROGRESS),
        Definition(AqlWsContract.MODULE_FIRMWARE, ACTION_OTA_COMPLETED),
        Definition(AqlWsContract.MODULE_SYSTEM, ACTION_SYSTEM_RESTARTING)
    )

    fun isRegisteredEvent(module: String, action: String): Boolean =
        Definition(module, action) in registeredEvents

    fun definitions(): Set<Definition> = registeredEvents.toSet()

    fun qualifiedNames(): Set<String> = registeredEvents
        .mapTo(linkedSetOf()) { definition -> definition.qualifiedName }
}
