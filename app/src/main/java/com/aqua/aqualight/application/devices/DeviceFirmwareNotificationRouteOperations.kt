package com.aqua.aqualight.application.devices

enum class DeviceFirmwareNotificationRouteDecision {
    OPEN,
    DEFER,
    REJECT
}

fun interface DeviceFirmwareNotificationRouteOperations {
    fun evaluate(deviceUid: String): DeviceFirmwareNotificationRouteDecision

    suspend fun dismissOpenedAvailability(ownerUid: String, deviceUid: String) = Unit
}

object DeviceFirmwareNotificationDestinationPolicy {

    fun evaluate(
        repositoryReady: Boolean,
        deviceExists: Boolean,
        otaSupported: Boolean
    ): DeviceFirmwareNotificationRouteDecision = when {
        !repositoryReady -> DeviceFirmwareNotificationRouteDecision.DEFER
        !deviceExists -> DeviceFirmwareNotificationRouteDecision.REJECT
        !otaSupported -> DeviceFirmwareNotificationRouteDecision.REJECT
        else -> DeviceFirmwareNotificationRouteDecision.OPEN
    }
}
