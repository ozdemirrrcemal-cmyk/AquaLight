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
        if (!session.isAuthenticated) {
            return DeviceFirmwareNotificationRouteDecision.DEFER
        }
        if (!DeviceFirmwareNotificationRoutePolicy.canOpen(
                deviceUid = deviceUid,
                notificationOwnerUid = notificationOwnerUid,
                activeOwnerUid = session.activeOwnerUid,
                isAuthenticated = true
            )
        ) {
            return DeviceFirmwareNotificationRouteDecision.REJECT
        }
        return routeOperations.evaluate(deviceUid)
    }
}
