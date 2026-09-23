package com.aqua.aqualight.data.aquarium.health

import android.content.Context

class AquariumHealthDataStoreManager private constructor(
    context: Context
) {
    private val access = AquariumHealthStoreAccess(context.applicationContext)

    internal val waterTests = AquariumWaterTestStore(access)
    internal val livestockObservations =
        LivestockHealthObservationStore(access)
    internal val plantObservations =
        PlantHealthObservationStore(access)
    internal val integrity =
        AquariumHealthIntegrityStore(access)

    companion object {
        fun create(context: Context): AquariumHealthDataStoreManager {
            return AquariumHealthDataStoreManager(context.applicationContext)
        }
    }
}
