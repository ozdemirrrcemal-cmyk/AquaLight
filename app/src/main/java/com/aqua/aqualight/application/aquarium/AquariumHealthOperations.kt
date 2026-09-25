package com.aqua.aqualight.application.aquarium

/** Application boundary for an owner's livestock observation timeline. */
interface AquariumHealthOperations {
    suspend fun addHealthObservation(tankId: Long, observation: LivestockHealthObservation)
    suspend fun addHealthCheck(tankId: Long, observationId: Long, check: LivestockHealthCheck)
    suspend fun closeHealthObservation(tankId: Long, observationId: Long, atMillis: Long)
}
