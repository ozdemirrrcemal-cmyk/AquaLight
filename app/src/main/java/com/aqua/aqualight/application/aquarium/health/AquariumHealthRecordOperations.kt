package com.aqua.aqualight.application.aquarium.health

import kotlinx.coroutines.flow.Flow

enum class HealthWaterParameter {
    TEMPERATURE_C,
    PH,
    GH_DGH,
    KH_DKH,
    TDS_PPM,
    TOTAL_AMMONIA_PPM,
    NITRITE_PPM,
    NITRATE_PPM,
    PHOSPHATE_PPM,
    DISSOLVED_OXYGEN_MG_L,
    CO2_MG_L,
    SPECIFIC_GRAVITY,
    ALKALINITY_DKH,
    CALCIUM_PPM,
    MAGNESIUM_PPM,
    PAR_UMOL_M2_S
}

data class AquariumWaterReading(
    val parameter: HealthWaterParameter,
    val value: Double
)

data class AquariumWaterTestRecord(
    val id: Long,
    val tankId: Long,
    val measuredAtMillis: Long,
    val readings: List<AquariumWaterReading>,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class AquariumWaterTestInput(
    val tankId: Long,
    val measuredAtMillis: Long,
    val readings: List<AquariumWaterReading>,
    val note: String = ""
)

enum class ObservationIntensity {
    MILD,
    MODERATE,
    SEVERE
}

data class LivestockHealthObservation(
    val id: Long,
    val tankId: Long,
    val livestockId: Long?,
    val categoryKey: String,
    val symptomKey: String,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class LivestockHealthObservationInput(
    val tankId: Long,
    val livestockId: Long?,
    val categoryKey: String,
    val symptomKey: String,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String = ""
)

interface AquariumHealthRecordOperations {
    fun waterTests(tankId: Long): Flow<List<AquariumWaterTestRecord>>

    fun livestockObservations(
        tankId: Long
    ): Flow<List<LivestockHealthObservation>>

    suspend fun addWaterTest(input: AquariumWaterTestInput): Long

    suspend fun updateWaterTest(
        testId: Long,
        input: AquariumWaterTestInput
    )

    suspend fun deleteWaterTest(testId: Long)

    suspend fun addLivestockObservation(
        input: LivestockHealthObservationInput
    ): Long

    suspend fun updateLivestockObservation(
        observationId: Long,
        input: LivestockHealthObservationInput
    )

    suspend fun deleteLivestockObservation(observationId: Long)
}
