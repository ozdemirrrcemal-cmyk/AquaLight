package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentCoverage
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentGap
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentRecommendation
import com.aqua.aqualight.application.aquarium.health.water.WaterHabitatConflict
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterQualityAssessment
import com.aqua.aqualight.application.aquarium.health.water.WaterRequirementConflict
import com.google.gson.JsonParser
import java.util.Collections

/** The saved engine output is decoded, never recomputed when opening history. */
internal object WaterEvaluationCodec {
    private const val SCHEMA_VERSION = 1

    fun encode(
        input: WaterAnalysisInput,
        context: AquariumHealthContext,
        result: WaterQualityAssessment
    ): StoredWaterEvaluation {
        require(input.tankId == context.tankId)
        require(result.contextRevision == context.revision)
        val document = WaterHealthContextDocument.encode(context)
        return StoredWaterEvaluation.newBuilder().setSchemaVersion(SCHEMA_VERSION).setTankId(context.tankId)
            .setCapturedAtMillis(context.capturedAtMillis).setContextRevision(context.revision)
            .setContextJson(document).setContextSha256(WaterHealthContextDocument.sha256(document))
            .setEngineVersion(result.engineVersion).setRuleRevision(result.ruleRevision)
            .setHazardSeverity(result.hazardSeverity?.name.orEmpty()).setCoverage(result.coverage.name)
            .setConflictCoverage(result.conflictCoverage.name).setChemistryGap(result.chemistryGap.name)
            .addAllCanonicalMeasurements(WaterCanonicalSnapshotCodec.encode(input))
            .addAllFindings(result.findings.map(WaterEvaluationFindingCodec::encode))
            .addAllConflicts(result.conflicts.map { conflict ->
                StoredWaterRequirementConflict.newBuilder().setFirstRuleId(conflict.first.ruleId)
                    .setSecondRuleId(conflict.second.ruleId).setAuthoritative(conflict.authoritative).build()
            })
            .addAllRecommendations(result.recommendations.map {
                StoredWaterRecommendation.newBuilder().setCode(it.code).addAllFindingRuleIds(it.findingRuleIds).build()
            })
            .addAllHabitatConflicts(result.habitatConflicts.map {
                StoredWaterHabitatConflict.newBuilder().setEntityKind(it.entity.kind.name)
                    .setEntityId(it.entity.localId).setCatalogId(it.entity.catalogId)
                    .setEntityDisplayName(it.entity.displayName)
                    .setTankEnvironment(it.tankEnvironment).setSourceWaterGroup(it.sourceWaterGroup).build()
            }).build().also { decode(it, context.tankId) }
    }

    fun decode(stored: StoredWaterEvaluation, tankId: Long): WaterQualityAssessment {
        validateContext(stored, tankId)
        val findings = stored.findingsList.map(WaterEvaluationFindingCodec::decode)
        val byId = findings.associateBy { it.ruleId }
        require(byId.size == findings.size)
        val severity = WaterEvaluationFindingCodec.optionalEnum<WaterHazardSeverity>(stored.hazardSeverity)
        require(severity == findings.mapNotNull { it.severity }.maxOrNull())
        val conflicts = stored.conflictsList.map {
            val first = requireNotNull(byId[it.firstRuleId])
            val second = requireNotNull(byId[it.secondRuleId])
            require(first.ruleId != second.ruleId && first.parameter == second.parameter)
            WaterRequirementConflict(first.parameter, first, second, it.authoritative)
        }
        val recommendations = stored.recommendationsList.map {
            require(it.code.isNotBlank() && it.findingRuleIdsList.all(byId::containsKey))
            WaterAssessmentRecommendation(it.code, Collections.unmodifiableList(it.findingRuleIdsList.toList()))
        }
        val habitat = stored.habitatConflictsList.map {
            require(it.entityId > 0 && it.catalogId.isNotBlank())
            require(it.tankEnvironment.isNotBlank() && it.sourceWaterGroup.isNotBlank())
            WaterHabitatConflict(WaterAssessmentEntity(
                WaterEvaluationFindingCodec.enumValue<WaterAssessmentEntityKind>(it.entityKind),
                it.entityId, it.catalogId, it.entityDisplayName), it.tankEnvironment, it.sourceWaterGroup)
        }
        return WaterQualityAssessment(stored.engineVersion, stored.ruleRevision, stored.contextRevision, severity,
            WaterEvaluationFindingCodec.enumValue<WaterAssessmentCoverage>(stored.coverage),
            WaterEvaluationFindingCodec.enumValue<WaterAssessmentCoverage>(stored.conflictCoverage),
            Collections.unmodifiableList(findings), Collections.unmodifiableList(conflicts),
            Collections.unmodifiableList(recommendations),
            WaterEvaluationFindingCodec.enumValue<WaterAssessmentGap>(stored.chemistryGap),
            Collections.unmodifiableList(habitat))
    }

    private fun validateContext(stored: StoredWaterEvaluation, tankId: Long) {
        if (stored.schemaVersion != SCHEMA_VERSION) {
            throw WaterAnalysisReadFailure.UnsupportedValue("evaluation.schemaVersion", stored.schemaVersion.toString())
        }
        require(stored.tankId == tankId && stored.capturedAtMillis > 0)
        require(listOf(stored.contextRevision, stored.engineVersion, stored.ruleRevision).all { it.isNotBlank() })
        require(WaterHealthContextDocument.sha256(stored.contextJson) == stored.contextSha256)
        val element = JsonParser.parseString(stored.contextJson)
        require(element.isJsonObject)
        val context = element.asJsonObject
        require(listOf("schemaVersion", "tankId", "capturedAtMillis", "timeBasis").all {
            context.get(it)?.isJsonPrimitive == true
        })
        require(context.get("schemaVersion").asInt == SCHEMA_VERSION)
        require(context.get("tankId").asLong == tankId)
        require(context.get("capturedAtMillis").asLong == stored.capturedAtMillis)
        require(context.get("timeBasis").asString == "CURRENT_AT_ENTRY")
    }
}
