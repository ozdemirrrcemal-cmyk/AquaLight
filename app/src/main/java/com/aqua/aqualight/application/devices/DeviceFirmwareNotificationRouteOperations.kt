package com.aqua.aqualight.application.devices

/** Fail-closed navigation policy for device firmware notification intents. */
fun interface DeviceFirmwareNotificationRouteOperations {
    fun canOpen(notificationOwnerUid: String, deviceUid: String): Boolean

    companion object {
        val DenyAll = DeviceFirmwareNotificationRouteOperations { _, _ -> false }
    }
}
