package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.data.aquarium.health.observation.HealthObservationDeletionIntegrity

/** Both histories participate in the same existing tank transaction and recovery path. */
internal class CombinedHealthDeletionIntegrity(
    private val water: WaterAnalysisRecoveryAuthority,
    private val observations: HealthObservationDeletionIntegrity
) : WaterAnalysisDeletionIntegrity {
    override suspend fun prepare(tankId: Long): String {
        val transaction = water.prepare(tankId)
        observations.prepare(tankId, transaction)
        return transaction
    }

    override suspend fun restore(tankId: Long, transactionId: String) {
        observations.restore(tankId, transactionId)
        water.restore(tankId, transactionId)
    }

    override suspend fun finish(tankId: Long, transactionId: String?) {
        observations.finish(tankId, transactionId)
        water.finish(tankId, transactionId)
    }

    suspend fun reconcileResolvedStages(owner: String) {
        observations.reconcileResolvedStages(owner)
        water.reconcileResolvedStages(owner)
    }
}
