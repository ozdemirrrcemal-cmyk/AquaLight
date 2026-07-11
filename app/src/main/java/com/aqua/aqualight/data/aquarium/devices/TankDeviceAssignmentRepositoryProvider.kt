package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider
import com.aqua.aqualight.data.user.UserDataScope

object TankDeviceAssignmentRepositoryProvider {

    private data class Entry(
        val ownerUid: String,
        val repository: TankDeviceAssignmentRepository
    )

    @Volatile
    private var entry: Entry? = null

    fun get(
        context: Context
    ): TankDeviceAssignmentRepository {
        val appContext = context.applicationContext
        val ownerUid = UserDataScope.requireCurrentUid()
        val currentEntry = entry

        if (currentEntry?.ownerUid == ownerUid) {
            return currentEntry.repository
        }

        return synchronized(this) {
            val synchronizedEntry = entry

            if (synchronizedEntry?.ownerUid == ownerUid) {
                synchronizedEntry.repository
            } else {
                TankDeviceAssignmentRepository(
                    ownerUid = ownerUid,
                    devicesRepository = DevicesRepositoryProvider.get(appContext),
                    assignmentStore = TankDeviceAssignmentStore.get(appContext),
                    tankStore = AquariumTankDataStoreManager(appContext)
                ).also { repository ->
                    entry = Entry(
                        ownerUid = ownerUid,
                        repository = repository
                    )
                }
            }
        }
    }

    fun clear(
        expectedOwnerUid: String? = null
    ): Boolean {
        val normalizedExpected = expectedOwnerUid
            ?.trim()
            ?.takeIf(String::isNotBlank)

        return synchronized(this) {
            val current = entry

            if (
                normalizedExpected != null &&
                current?.ownerUid != normalizedExpected
            ) {
                false
            } else {
                entry = null
                current != null
            }
        }
    }

    fun currentOwnerUid(): String? {
        return entry?.ownerUid
    }
}
