package com.aqua.aqualight.data.aquarium.devices

import android.content.Context
import com.aqua.aqualight.data.aquarium.store.AquariumTankDataStoreManager
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider

object TankDeviceAssignmentRepositoryProvider {

    @Volatile
    private var instance: TankDeviceAssignmentRepository? = null

    fun get(
        context: Context
    ): TankDeviceAssignmentRepository {
        return instance ?: synchronized(this) {
            instance ?: TankDeviceAssignmentRepository(
                devicesRepository = DevicesRepositoryProvider.get(context),
                assignmentStore = TankDeviceAssignmentStore.get(context),
                tankDataStoreManager = AquariumTankDataStoreManager(
                    context.applicationContext
                )
            ).also { repository ->
                instance = repository
            }
        }
    }
}
