package com.aqua.aqualight.ui.navigation

import com.aqua.aqualight.application.user.OwnerIdentityPolicy

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

        val notificationOwner = OwnerIdentityPolicy.normalize(
            notificationOwnerUid
        )
        val activeOwner = OwnerIdentityPolicy.normalize(activeOwnerUid)

        return notificationOwner.isNotBlank() &&
            activeOwner.isNotBlank() &&
            OwnerIdentityPolicy.belongsToOwner(
                recordOwnerUid = notificationOwner,
                ownerUid = activeOwner
            )
    }
}
