package com.aqua.aqualight.data.user

import android.content.Context
import com.aqua.aqualight.data.care.CareTaskDataStoreManager
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager

/**
 * One-time migration bridge for records created before local stores had ownerUid.
 *
 * The first authenticated user after the upgrade becomes the owner of legacy
 * local records. After this, screens and background work only read/write records
 * whose ownerUid matches FirebaseAuth.currentUser.uid.
 */
class UserDataOwnershipMigrator private constructor(
    private val appContext: Context
) {

    companion object {
        fun create(
            context: Context
        ): UserDataOwnershipMigrator {
            return UserDataOwnershipMigrator(
                appContext = context.applicationContext
            )
        }
    }

    suspend fun migrateLegacyRecordsToOwner(
        ownerUid: String
    ) {
        val targetOwnerUid = UserDataScope.normalizeOwnerUid(ownerUid)

        if (targetOwnerUid.isBlank()) {
            return
        }

        AquariumTankDataStoreManager(
            appContext
        ).assignLegacyTanksToOwner(
            ownerUid = targetOwnerUid
        )

        CareTaskDataStoreManager.create(
            appContext
        ).assignLegacyTasksToOwner(
            ownerUid = targetOwnerUid
        )
    }
}
