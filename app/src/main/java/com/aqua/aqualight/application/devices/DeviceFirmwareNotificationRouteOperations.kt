package com.aqua.aqualight.application.devices

import com.aqua.aqualight.application.notifications.DeviceFirmwareNotificationKind

enum class DeviceFirmwareNotificationRouteDecision {
    OPEN,
    DEFER,
    REJECT
}

data class DeviceFirmwareNotificationRouteRequest(
    val deviceUid: String,
    val kind: DeviceFirmwareNotificationKind,
    val targetVersion: String = ""
)

fun interface DeviceFirmwareNotificationRouteOperations {
    fun evaluate(
        request: DeviceFirmwareNotificationRouteRequest
    ): DeviceFirmwareNotificationRouteDecision

    suspend fun dismissOpenedAvailability(ownerUid: String, deviceUid: String) = Unit
}

object DeviceFirmwareNotificationDestinationPolicy {

    fun evaluate(
        repositoryReady: Boolean,
        deviceExists: Boolean,
        otaSupported: Boolean,
        actionable: Boolean
    ): DeviceFirmwareNotificationRouteDecision = when {
        !repositoryReady -> DeviceFirmwareNotificationRouteDecision.DEFER
        !deviceExists -> DeviceFirmwareNotificationRouteDecision.REJECT
        !otaSupported -> DeviceFirmwareNotificationRouteDecision.REJECT
        !actionable -> DeviceFirmwareNotificationRouteDecision.REJECT
        else -> DeviceFirmwareNotificationRouteDecision.OPEN
    }
}
