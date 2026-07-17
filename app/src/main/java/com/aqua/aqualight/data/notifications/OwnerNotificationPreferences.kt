package com.aqua.aqualight.data.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.aqua.aqualight.data.recovery.LocalDataRecoveryTracker
import com.aqua.aqualight.data.user.UserDataScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationPreferencesDataStore: DataStore<NotificationPreferencesStore> by dataStore(
    fileName = "notification_preferences.pb",
    serializer = NotificationPreferencesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler {
        LocalDataRecoveryTracker.markRecovered(
            LocalDataRecoveryTracker.Area.NOTIFICATION_PREFERENCES
        )
        NotificationPreferenceStoreRules.defaultStore()
    }
)

/** Owner-isolated AquaLight notification preference store. */
class OwnerNotificationPreferences private constructor(
    private val context: Context
) {

    fun enabledFlow(ownerUid: String): Flow<Boolean> {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        return context.notificationPreferencesDataStore.data.map { store ->
            NotificationPreferenceStoreRules.validateStore(store)
                .ownerPreferencesList
                .firstOrNull { preference ->
                    preference.ownerUid == normalizedOwnerUid
                }
                ?.enabled == true
        }
    }

    suspend fun isEnabled(ownerUid: String): Boolean {
        return enabledFlow(ownerUid).first()
    }

    suspend fun setEnabled(
        ownerUid: String,
        enabled: Boolean,
        updatedAtMillis: Long = System.currentTimeMillis()
    ) {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        require(updatedAtMillis > 0L) {
            "updatedAtMillis must be positive"
        }

        val preference = OwnerNotificationPreference.newBuilder()
            .setOwnerUid(normalizedOwnerUid)
            .setEnabled(enabled)
            .setUpdatedAtMillis(updatedAtMillis)
            .build()
            .also(NotificationPreferenceStoreRules::validatePreference)

        context.notificationPreferencesDataStore.updateData { currentStore ->
            val validated = NotificationPreferenceStoreRules.validateStore(currentStore)
            val updated = validated.ownerPreferencesList
                .filterNot { current -> current.ownerUid == normalizedOwnerUid }
                .plus(preference)
                .sortedBy(OwnerNotificationPreference::getOwnerUid)

            NotificationPreferenceStoreRules.validateStore(
                validated.toBuilder()
                    .clearOwnerPreferences()
                    .addAllOwnerPreferences(updated)
                    .build()
            )
        }
    }

    suspend fun snapshotForOwner(
        ownerUid: String
    ): OwnerNotificationPreference? {
        val normalizedOwnerUid = requireOwnerUid(ownerUid)
        return context.notificationPreferencesDataStore.data.first()
            .let(NotificationPreferenceStoreRules::validateStore)
            .ownerPreferencesList
            .firstOrNull { preference ->
                preference.ownerUid == normalizedOwnerUid
            }
    }

    private fun requireOwnerUid(ownerUid: String): String {
        return UserDataScope.normalizeOwnerUid(ownerUid).also { normalized ->
            require(normalized.isNotBlank()) {
                "ownerUid must not be blank"
            }
        }
    }

    companion object {
        fun create(context: Context): OwnerNotificationPreferences {
            return OwnerNotificationPreferences(context.applicationContext)
        }
    }
}
