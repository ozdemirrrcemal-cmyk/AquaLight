package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider

/**
 * Process-level owner-aware tank/device relationship repository.
 *
 * The repository resolves the active owner for every operation, so UI surfaces,
 * deletion flows and startup repair all use the same durable assignment source.
 */
object TankDeviceAssignmentRepositoryProvider {

    @Volatile
    private var instance: TankDeviceAssignmentRepository? = null

    fun get(
        context: Context
    ): TankDeviceAssignmentRepository {
        return instance ?: synchronized(this) {
            instance ?: createRepository(
                context = context.applicationContext
            ).also { repository ->
                instance = repository
            }
        }
    }

    private fun createRepository(
        context: Context
    ): TankDeviceAssignmentRepository {
        return TankDeviceAssignmentRepository(
            devicesRepository = DevicesRepositoryProvider.get(context),
            assignmentStore = TankDeviceAssignmentStore.get(context),
            tankDataStoreManager = AquariumTankDataStoreManager(context)
        )
    }
}
