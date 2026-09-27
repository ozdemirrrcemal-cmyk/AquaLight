package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationSubjectSnapshot
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterRelation
import com.aqua.aqualight.application.aquarium.health.observation.RecordedObservationContext
import com.aqua.aqualight.application.aquarium.health.observation.RecordedObservationEvidence
import com.aqua.aqualight.data.aquarium.health.WaterHealthContextDocument

internal object HealthObservationRecordCodec {
    fun encode(owner: String, id: Long, createdAt: Long, prepared: PreparedHealthObservation): StoredHealthObservation {
        val context = prepared.preparation.context
        val water = prepared.preparation.water
        val document = WaterHealthContextDocument.encode(context)
        val evidence = StoredHealthObservationEvidence.newBuilder()
            .setContextCapturedAtMillis(context.capturedAtMillis).setContextRevision(context.revision)
            .setOriginalTankId(context.tankId).setContextJson(document)
            .setContextSha256(WaterHealthContextDocument.sha256(document))
            .setWaterRelation(water.relation.name).setWaterPolicyRevision(water.policyRevision)
            .setWaterMaximumAgeMillis(water.maximumAgeMillis).apply {
                water.ageAtObservationMillis?.let(::setWaterAgeAtObservationMillis)
                water.analysis?.let { setWater(HealthObservationWaterCodec.encode(it)) }
            }
        return StoredHealthObservation.newBuilder().setSchemaVersion(1).setId(id).setOwnerUid(owner)
            .setCreatedAtMillis(createdAt).setInput(HealthObservationInputCodec.encode(prepared.input))
            .setEvidence(evidence).setAssessment(HealthObservationAssessmentCodec.encode(prepared.assessment))
            .apply { prepared.subject?.let { setSubject(encodeSubject(it)) } }.build()
            .also(HealthObservationRecordRules::validate)
    }

    fun decode(stored: StoredHealthObservation): HealthObservationSnapshot {
        HealthObservationRecordRules.validate(stored)
        val evidence = stored.evidence
        return HealthObservationSnapshot(stored.id, HealthObservationInputCodec.decode(stored.input),
            stored.createdAtMillis, stored.subject.takeIf { stored.hasSubject() }?.let(::decodeSubject),
            RecordedObservationEvidence(RecordedObservationContext(evidence.contextCapturedAtMillis,
                evidence.contextRevision, evidence.originalTankId),
                evidence.water.takeIf { evidence.hasWater() }?.let(HealthObservationWaterCodec::decode),
                enumValueOf<ObservationWaterRelation>(evidence.waterRelation),
                evidence.waterAgeAtObservationMillis.takeIf { evidence.hasWaterAgeAtObservationMillis() },
                evidence.waterPolicyRevision, evidence.waterMaximumAgeMillis),
            HealthObservationAssessmentCodec.decode(stored.assessment))
    }

    private fun encodeSubject(value: HealthObservationSubjectSnapshot): StoredHealthObservationSubject =
        StoredHealthObservationSubject.newBuilder().setOriginalLocalId(value.originalLocalId)
            .setCatalogId(value.catalogId).setDisplayName(value.displayName).setResolution(value.resolution.name)
            .apply { value.quantity?.let(::setQuantity) }.build()

    private fun decodeSubject(value: StoredHealthObservationSubject): HealthObservationSubjectSnapshot =
        HealthObservationSubjectSnapshot(value.originalLocalId, value.catalogId, value.displayName,
            value.quantity.takeIf { value.hasQuantity() }, enumValueOf<HealthEntityResolution>(value.resolution))
}
