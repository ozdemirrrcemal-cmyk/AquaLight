package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.application.user.OwnerIdentityPolicy

/** Fail-closed owner validation for device firmware notification routes. */
object DeviceFirmwareNotificationRoutePolicy {

    fun canOpen(
        deviceUid: String?,
        notificationOwnerUid: String?,
        activeOwnerUid: String?,
        isAuthenticated: Boolean
    ): Boolean {
        if (!isAuthenticated || deviceUid.isNullOrBlank()) {
            return false
        }

        val notificationOwner = OwnerIdentityPolicy.normalize(notificationOwnerUid)
        val activeOwner = OwnerIdentityPolicy.normalize(activeOwnerUid)

        return notificationOwner.isNotBlank() &&
            activeOwner.isNotBlank() &&
            OwnerIdentityPolicy.belongsToOwner(
                recordOwnerUid = notificationOwner,
                ownerUid = activeOwner
            )
    }
}
