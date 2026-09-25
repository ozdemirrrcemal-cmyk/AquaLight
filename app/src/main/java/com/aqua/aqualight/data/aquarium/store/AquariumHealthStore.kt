package com.aqua.aqualight.data.aquarium.store

import com.aqua.aqualight.application.aquarium.LivestockHealthCheck
import com.aqua.aqualight.application.aquarium.LivestockHealthObservation
import com.aqua.aqualight.application.aquarium.LivestockHealthTrend

/** Transactional health timeline mutations backed by the owner-scoped tank store. */
internal class AquariumHealthStore(private val tankStore: AquariumTankDataStoreManager) {
    suspend fun addHealthObservation(tankId: Long, observation: LivestockHealthObservation) {
        require(observation.checks.isEmpty() && observation.closedAtMillis == null)
        tankStore.updateCurrentOwnerTank(tankId) { tank ->
            require(tank.livestockList.any { it.id == observation.livestockId }) {
                "Observation requires a livestock record in the active tank."
            }
            require(tank.healthObservationsList.none { it.id == observation.id }) {
                "Duplicate health observation id."
            }
            val livestock = tank.livestockList.first { it.id == observation.livestockId }
            require(observation.affectedCount in 1..livestock.quantity)
            require(observation.livestockName == livestock.name)
            require(observation.livestockCategory == livestock.category)
            require(observation.catalogEntryId == livestock.catalogEntryId)
            tank.toBuilder().addHealthObservations(observation.toStored()).build()
        }
    }

    /** Restores an archived timeline in one validated, owner-scoped transaction. */
    suspend fun restoreHealthObservation(tankId: Long, observation: LivestockHealthObservation) {
        tankStore.updateCurrentOwnerTank(tankId) { tank ->
            require(tank.healthObservationsList.none { it.id == observation.id })
            tank.toBuilder().addHealthObservations(observation.toStored()).build()
        }
    }

    suspend fun addHealthCheck(tankId: Long, observationId: Long, check: LivestockHealthCheck) {
        tankStore.updateCurrentOwnerTank(tankId) { tank ->
            val index = tank.healthObservationsList.indexOfFirst { it.id == observationId }
            require(index >= 0) { "Health observation not found." }
            val observation = tank.getHealthObservations(index)
            require(observation.closedAtMillis == 0L) { "Health observation is closed." }
            require(check.observedAtMillis >= observation.observedAtMillis)
            require(observation.checksList.none { it.id == check.id })
            val currentQuantity = tank.livestockList.firstOrNull {
                it.id == observation.livestockId
            }?.quantity ?: observation.affectedCount
            require(check.affectedCount in 1..currentQuantity)
            val updated = observation.toBuilder()
                .addChecks(check.toStored())
                .apply {
                    if (check.trend == LivestockHealthTrend.RESOLVED) {
                        closedAtMillis = check.observedAtMillis
                        outcomeCode = LivestockHealthTrend.RESOLVED.code
                    }
                }.build()
            tank.toBuilder().setHealthObservations(index, updated).build()
        }
    }

    suspend fun closeHealthObservation(tankId: Long, observationId: Long, atMillis: Long) {
        tankStore.updateCurrentOwnerTank(tankId) { tank ->
            val index = tank.healthObservationsList.indexOfFirst { it.id == observationId }
            require(index >= 0) { "Health observation not found." }
            val observation = tank.getHealthObservations(index)
            require(observation.closedAtMillis == 0L) { "Health observation is already closed." }
            val lastAt = observation.checksList.lastOrNull()?.observedAtMillis
                ?: observation.observedAtMillis
            require(atMillis >= lastAt)
            tank.toBuilder().setHealthObservations(
                index,
                observation.toBuilder().setClosedAtMillis(atMillis)
                    .setOutcomeCode("ended").build()
            ).build()
        }
    }

}
