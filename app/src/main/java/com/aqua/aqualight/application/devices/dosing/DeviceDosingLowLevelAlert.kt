package com.aqua.aqualight.application.devices.dosing

/** Localized user-facing copy for one Dosing reservoir low-level alert. */
data class DeviceDosingLowLevelAlertCopy(
    val title: String,
    val message: String
) {
    init {
        require(title.isNotBlank())
        require(message.isNotBlank())
    }
}

/** Presentation-independent text boundary for low-level reservoir notifications. */
fun interface DeviceDosingLowLevelAlertTextResolver {
    fun resolve(channelTitle: String): DeviceDosingLowLevelAlertCopy
}
