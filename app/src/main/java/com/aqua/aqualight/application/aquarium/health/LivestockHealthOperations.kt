package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

data class LivestockObservationInput(
    val requestId: String,
    val tankId: Long,
    val livestockId: Long,
    val symptomKeys: List<String>,
    val onsetKey: String,
    val otherObservation: String,
    val note: String,
    val photoUris: List<String>,
    val affectedCount: Int
)

data class LivestockCheckInput(
    val requestId: String,
    val status: String,
    val affectedCount: Int,
    val checkedAtMillis: Long,
    val note: String,
    val photoUri: String?
)

data class LivestockCheckSnapshot(
    val status: String,
    val affectedCount: Int,
    val checkedAtMillis: Long,
    val note: String,
    val photoUri: String?
)

data class LivestockObservationSnapshot(
    val id: Long,
    val tankId: Long,
    val livestockId: Long,
    val symptomKeys: List<String>,
    val onsetKey: String,
    val otherObservation: String,
    val note: String,
    val photoUris: List<String>,
    val affectedCount: Int,
    val totalCount: Int,
    val createdAtMillis: Long,
    val closedAtMillis: Long?,
    val closeReason: String?,
    val checks: List<LivestockCheckSnapshot>
) {
    val currentAffectedCount: Int
        get() = checks.maxByOrNull { it.checkedAtMillis }?.affectedCount ?: affectedCount
}

interface LivestockHealthOperations {
    fun observationsForTank(tankId: Long): Flow<List<LivestockObservationSnapshot>>
    suspend fun createObservation(input: LivestockObservationInput): Long
    suspend fun addCheck(tankId: Long, observationId: Long, input: LivestockCheckInput)
    suspend fun closeObservation(tankId: Long, observationId: Long, reason: String)
}
