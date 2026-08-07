package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.data.user.UserDataScope

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

        val notificationOwner = UserDataScope.normalizeOwnerUid(notificationOwnerUid)
        val activeOwner = UserDataScope.normalizeOwnerUid(activeOwnerUid)

        return notificationOwner.isNotBlank() &&
            activeOwner.isNotBlank() &&
            UserDataScope.belongsToOwner(
                recordOwnerUid = notificationOwner,
                ownerUid = activeOwner,
                includeLegacy = false
            )
    }
}
