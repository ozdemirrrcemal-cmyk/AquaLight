package com.aqua.aqualight.application.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterMeasurementSnapshot
import com.aqua.aqualight.application.aquarium.health.WaterTemperatureSource
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import kotlinx.coroutines.flow.Flow

interface HealthObservationOperations {
    suspend fun prepare(tankId: Long, observedAtMillis: Long): HealthObservationPreparation
    fun history(query: HealthObservationQuery): Flow<HealthObservationPage>
    fun observation(tankId: Long, observationId: Long): Flow<HealthObservationSnapshot?>
    suspend fun save(input: HealthObservationInput): Long
    suspend fun delete(tankId: Long, observationId: Long)
}

data class HealthObservationPreparation(val context: AquariumHealthContext, val water: ObservationWaterEvidence)
data class HealthObservationQuery(
    val tankId: Long,
    val kind: HealthObservationKind,
    val subjectId: Long? = null,
    val cursor: HealthObservationCursor? = null
)
data class HealthObservationCursor(val observedAtMillis: Long, val createdAtMillis: Long, val observationId: Long)
data class HealthObservationPage(
    val records: List<HealthObservationSnapshot>,
    val totalCount: Long,
    val next: HealthObservationCursor?
)
data class HealthObservationSubjectSnapshot(
    val originalLocalId: Long,
    val catalogId: String,
    val displayName: String,
    val quantity: Int?,
    val resolution: HealthEntityResolution
)
data class RecordedObservationContext(val capturedAtMillis: Long, val revision: String, val originalTankId: Long)
data class RecordedObservationWaterIdentity(
    val analysisId: Long,
    val originalTankId: Long,
    val measuredAtMillis: Long,
    val createdAtMillis: Long
)
data class RecordedObservationTemperature(val celsius: Double, val source: WaterTemperatureSource)
data class RecordedObservationWater(
    val identity: RecordedObservationWaterIdentity,
    val measurements: List<WaterMeasurementSnapshot>,
    val temperature: RecordedObservationTemperature?
)
data class RecordedObservationEvidence(
    val context: RecordedObservationContext,
    val water: RecordedObservationWater?,
    val relation: ObservationWaterRelation,
    val ageAtObservationMillis: Long?,
    val policyRevision: String,
    val maximumAgeMillis: Long
)

/** History contains frozen labels/evidence; deleting or renaming a live catalog subject cannot rewrite it. */
data class HealthObservationSnapshot(
    val id: Long,
    val input: HealthObservationInput,
    val createdAtMillis: Long,
    val subject: HealthObservationSubjectSnapshot?,
    val evidence: RecordedObservationEvidence,
    val assessment: ObservationAssessment
)
