package com.aqua.aqualight.data.aquarium.health

/** The tank deletion coordinator holds the owner/tank gate for all three operations. */
internal interface WaterAnalysisDeletionIntegrity {
    suspend fun prepare(tankId: Long): String
    suspend fun restore(tankId: Long, transactionId: String)
    suspend fun finish(tankId: Long, transactionId: String? = null)
}
