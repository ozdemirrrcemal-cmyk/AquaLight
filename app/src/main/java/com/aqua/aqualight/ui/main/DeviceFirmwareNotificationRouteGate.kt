package com.aqua.aqualight.ui.main

import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteDecision
import com.aqua.aqualight.application.devices.DeviceFirmwareNotificationRouteOperations
import com.aqua.aqualight.ui.navigation.DeviceFirmwareNotificationRoutePolicy

/** Revalidates owner and device eligibility immediately before firmware navigation. */
internal class DeviceFirmwareNotificationRouteGate(
    private val sessionSnapshot: () -> MainNavigationSessionSnapshot,
    private val routeOperations: DeviceFirmwareNotificationRouteOperations
) {

    fun evaluate(
        deviceUid: String,
        notificationOwnerUid: String
    ): DeviceFirmwareNotificationRouteDecision {
        val session = sessionSnapshot()
        return when {
            !session.isAuthenticated -> DeviceFirmwareNotificationRouteDecision.DEFER
            !DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = deviceUid,
                notificationOwnerUid = notificationOwnerUid,
                activeOwnerUid = session.activeOwnerUid,
                isAuthenticated = true
            ) -> DeviceFirmwareNotificationRouteDecision.REJECT
            else -> routeOperations.evaluate(deviceUid)
        }
    }

    suspend fun acknowledgeOpened(
        deviceUid: String,
        notificationOwnerUid: String
    ) {
        val normalizedDeviceUid = deviceUid.trim()
        val normalizedOwnerUid = notificationOwnerUid.trim()
        if (normalizedDeviceUid.isBlank() || normalizedOwnerUid.isBlank()) return

        routeOperations.dismissOpenedAvailability(
            ownerUid = normalizedOwnerUid,
            deviceUid = normalizedDeviceUid
        )
    }
}
