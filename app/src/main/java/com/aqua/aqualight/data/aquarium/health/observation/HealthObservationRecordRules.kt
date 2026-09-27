package com.aqua.aqualight.data.aquarium.health.observation

import com.aqua.aqualight.application.aquarium.health.WaterAnalysisPolicy
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationKind
import com.aqua.aqualight.application.aquarium.health.observation.HealthObservationPolicy
import com.aqua.aqualight.application.aquarium.health.observation.ObservationPhase
import com.aqua.aqualight.application.aquarium.health.observation.ObservationWaterRelation
import com.aqua.aqualight.data.aquarium.health.WaterHealthContextDocument
import com.google.gson.JsonParser

internal object HealthObservationRecordRules {
    private const val MAX_PHOTO_URI_LENGTH = 2048

    fun validate(stored: StoredHealthObservation) {
        require(stored.schemaVersion == 1) { "Unsupported health observation schema." }
        require(stored.id > 0L && stored.ownerUid.isNotBlank() && stored.ownerUid == stored.ownerUid.trim())
        require(stored.hasInput() && stored.hasEvidence() && stored.hasAssessment())
        val input = HealthObservationInputCodec.decode(stored.input)
        require(input.identity.tankId > 0L && WaterAnalysisPolicy.isValidRequestId(input.identity.requestId))
        require(stored.createdAtMillis in WaterAnalysisPolicy.MIN_DATE_MILLIS..WaterAnalysisPolicy.MAX_DATE_MILLIS)
        require(input.identity.observedAtMillis in
            WaterAnalysisPolicy.MIN_DATE_MILLIS..WaterAnalysisPolicy.MAX_DATE_MILLIS)
        require(input.identity.observedAtMillis <= stored.createdAtMillis + WaterAnalysisPolicy.FUTURE_TOLERANCE_MILLIS)
        require(input.notes.text.length <= HealthObservationPolicy.MAX_NOTE_LENGTH)
        require(stored.input.photoUri.length <= MAX_PHOTO_URI_LENGTH)
        require(stored.input.photoUri.isEmpty() || stored.input.photoUri.isNotBlank())
        validateFollowUp(stored)
        validateContext(stored)
        validateSubject(stored)
        validateWater(stored)
        HealthObservationAssessmentCodec.decode(stored.assessment)
        HealthObservationImportIdentity.validate(stored)
    }

    private fun validateFollowUp(stored: StoredHealthObservation) {
        val input = stored.input
        val phase = enumValueOf<ObservationPhase>(input.phase)
        require(!input.hasPreviousObservationId() || input.previousObservationId > 0L &&
            input.previousObservationId != stored.id)
        require(phase == ObservationPhase.OBSERVATION || input.hasPreviousObservationId() ||
            stored.hasOrigin() && stored.origin.sourcePreviousObservationId > 0L)
        require(phase != ObservationPhase.INTERVENTION || input.note.isNotBlank())
    }

    private fun validateContext(stored: StoredHealthObservation) {
        val evidence = stored.evidence
        require(evidence.originalTankId > 0L && evidence.contextCapturedAtMillis > 0L)
        require(evidence.contextRevision.isNotBlank())
        require(WaterHealthContextDocument.sha256(evidence.contextJson) == evidence.contextSha256)
        val context = JsonParser.parseString(evidence.contextJson).asJsonObject
        require(context.get("schemaVersion").asInt == 1)
        require(context.get("tankId").asLong == evidence.originalTankId)
        require(context.get("capturedAtMillis").asLong == evidence.contextCapturedAtMillis)
        require(context.get("timeBasis").asString == "CURRENT_AT_ENTRY")
        if (!stored.hasOrigin()) require(stored.input.tankId == evidence.originalTankId)
        else {
            val origin = stored.origin
            require(origin.sourceOwnerUid.isNotBlank() && origin.sourceObservationId > 0L)
            require(origin.sourceTankId == evidence.originalTankId && origin.sourceRecordSha256.length == SHA256_LENGTH)
            require(WaterAnalysisPolicy.isValidRequestId(origin.restoreTransactionId))
        }
    }

    private fun validateSubject(stored: StoredHealthObservation) {
        val kind = enumValueOf<HealthObservationKind>(stored.input.kind)
        require(stored.hasSubject() == (kind != HealthObservationKind.ALGAE))
        if (stored.hasSubject()) {
            val subject = stored.subject
            require(subject.originalLocalId > 0L && subject.displayName.isNotBlank())
            require(subject.hasQuantity() == (kind == HealthObservationKind.LIVESTOCK))
            require(!subject.hasQuantity() || subject.quantity > 0)
            require(!stored.input.hasAffectedQuantity() || stored.input.affectedQuantity <= subject.quantity)
            if (!stored.hasOrigin()) require(stored.input.subjectId == subject.originalLocalId)
        }
        require(stored.assessment.waterFindingsList.all {
            it.entityKind == kind.name && it.entityId == stored.subject.originalLocalId
        })
    }

    private fun validateWater(stored: StoredHealthObservation) {
        val evidence = stored.evidence
        require(evidence.waterPolicyRevision.isNotBlank() && evidence.waterMaximumAgeMillis >= 0L)
        val relation = enumValueOf<ObservationWaterRelation>(evidence.waterRelation)
        require(evidence.hasWater() == evidence.hasWaterAgeAtObservationMillis())
        require(evidence.hasWater() == (relation != ObservationWaterRelation.MISSING))
        if (evidence.hasWater()) {
            HealthObservationWaterCodec.decode(evidence.water)
            require(evidence.water.originalTankId == evidence.originalTankId)
            val age = Math.subtractExact(stored.input.observedAtMillis, evidence.water.measuredAtMillis)
            require(age == evidence.waterAgeAtObservationMillis)
            require((relation == ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION) ==
                (age in 0L..evidence.waterMaximumAgeMillis))
        }
        if (stored.assessment.waterFindingsCount > 0) {
            require(relation == ObservationWaterRelation.AVAILABLE_BEFORE_OBSERVATION)
            require(evidence.water.assessmentContextRevision == evidence.contextRevision)
        }
    }

    private const val SHA256_LENGTH = 64
}
