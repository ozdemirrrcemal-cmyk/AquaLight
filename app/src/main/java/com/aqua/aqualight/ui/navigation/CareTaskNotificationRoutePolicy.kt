package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.data.user.UserDataScope

/** Fail-closed owner validation for care-task notification deep links. */
object CareTaskNotificationRoutePolicy {

    fun canOpen(
        taskId: Long,
        notificationOwnerUid: String?,
        activeOwnerUid: String?,
        isAuthenticated: Boolean
    ): Boolean {
        if (!isAuthenticated || taskId <= 0L) {
            return false
        }

        val notificationOwner = UserDataScope.normalizeOwnerUid(
            notificationOwnerUid
        )
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
