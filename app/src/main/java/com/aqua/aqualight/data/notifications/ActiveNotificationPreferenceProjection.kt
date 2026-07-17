package com.aqua.aqualight.data.notifications

import android.content.Context
import com.aqua.aqualight.data.user.UserDataScope
import com.aqua.aqualight.data.user.UserPreferencesManager

/**
 * Active-session compatibility projection for legacy care-task write paths.
 *
 * The owner-scoped notification preference store is the only source of truth.
 * This projection is cleared at session shutdown and refreshed for the committed
 * owner before background reconciliation, preventing an outgoing owner's value
 * from influencing a new account.
 */
class ActiveNotificationPreferenceProjection private constructor(
    private val ownerPreferences: OwnerNotificationPreferences,
    private val legacyPreferences: UserPreferencesManager
) {

    suspend fun refreshForOwner(ownerUid: String): Boolean {
        val owner = requireOwnerUid(ownerUid)
        val enabled = ownerPreferences.isEnabled(owner)
        legacyPreferences.updateNotificationsEnabled(enabled)
        return enabled
    }

    suspend fun publishForActiveOwner(
        ownerUid: String,
        enabled: Boolean
    ) {
        requireOwnerUid(ownerUid)
        legacyPreferences.updateNotificationsEnabled(enabled)
    }

    suspend fun clear() {
        legacyPreferences.updateNotificationsEnabled(false)
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }
    }

    companion object {
        fun create(context: Context): ActiveNotificationPreferenceProjection {
            val appContext = context.applicationContext
            return ActiveNotificationPreferenceProjection(
                ownerPreferences = OwnerNotificationPreferences.create(appContext),
                legacyPreferences = UserPreferencesManager.create(appContext)
            )
        }
    }
}
