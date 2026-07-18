package com.aqua.aqualight.data.notifications

import com.aqua.aqualight.data.store.StoreInvariantViolation
import com.aqua.aqualight.data.user.UserDataScope

/** Strict rules for the first commercial owner notification-preference store. */
object NotificationPreferenceStoreRules {

    const val CURRENT_SCHEMA_VERSION = 1

    fun defaultStore(): NotificationPreferencesStore {
        return NotificationPreferencesStore.newBuilder()
            .setSchemaVersion(CURRENT_SCHEMA_VERSION)
            .build()
    }

    fun validateStore(
        store: NotificationPreferencesStore
    ): NotificationPreferencesStore {
        if (store.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw StoreInvariantViolation(
                "Unsupported notification-preference schema version ${store.schemaVersion}."
            )
        }

        val seenOwners = linkedSetOf<String>()
        store.ownerPreferencesList.forEach(::validatePreference)
        store.ownerPreferencesList.forEach { preference ->
            val ownerUid = UserDataScope.normalizeOwnerUid(preference.ownerUid)
            if (!seenOwners.add(ownerUid)) {
                throw StoreInvariantViolation(
                    "Duplicate notification preference for owner $ownerUid."
                )
            }
        }

        return store
    }

    fun validatePreference(
        preference: OwnerNotificationPreference
    ): OwnerNotificationPreference {
        val ownerUid = UserDataScope.normalizeOwnerUid(preference.ownerUid)
        if (ownerUid.isBlank()) {
            throw StoreInvariantViolation(
                "Notification preference owner UID must not be blank."
            )
        }
        if (ownerUid != preference.ownerUid) {
            throw StoreInvariantViolation(
                "Notification preference owner UID must be normalized."
            )
        }
        if (preference.updatedAtMillis <= 0L) {
            throw StoreInvariantViolation(
                "Notification preference update time must be positive."
            )
        }

        return preference
    }
}
