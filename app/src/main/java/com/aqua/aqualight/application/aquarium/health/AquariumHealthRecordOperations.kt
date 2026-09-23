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

data class PlantHealthObservation(
    val id: Long,
    val tankId: Long,
    val plantId: Long?,
    val symptomKey: String,
    val algaeTypeKey: String?,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

data class PlantHealthObservationInput(
    val tankId: Long,
    val plantId: Long?,
    val symptomKey: String,
    val algaeTypeKey: String?,
    val intensity: ObservationIntensity,
    val observedAtMillis: Long,
    val note: String = ""
)

interface AquariumHealthRecordOperations {
    val waterTests: AquariumWaterTestOperations
    val livestockObservations: LivestockHealthObservationOperations
    val plantObservations: PlantHealthObservationOperations
}

interface AquariumWaterTestOperations {
    fun observe(tankId: Long): Flow<List<AquariumWaterTestRecord>>
    suspend fun add(input: AquariumWaterTestInput): Long
    suspend fun update(testId: Long, input: AquariumWaterTestInput)
    suspend fun delete(testId: Long)
}

interface LivestockHealthObservationOperations {
    fun observe(tankId: Long): Flow<List<LivestockHealthObservation>>
    suspend fun add(input: LivestockHealthObservationInput): Long
    suspend fun update(
        observationId: Long,
        input: LivestockHealthObservationInput
    )
    suspend fun delete(observationId: Long)
}

interface PlantHealthObservationOperations {
    fun observe(tankId: Long): Flow<List<PlantHealthObservation>>
    suspend fun add(input: PlantHealthObservationInput): Long
    suspend fun update(
        observationId: Long,
        input: PlantHealthObservationInput
    )
    suspend fun delete(observationId: Long)
}
