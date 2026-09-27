package com.aqua.aqualight.application.aquarium.health.water

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.AquariumWaterSnapshot
import com.aqua.aqualight.application.aquarium.LivestockParameterCoverage
import com.aqua.aqualight.application.aquarium.LivestockWaterCompatibilityEvaluator
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.health.WaterAnalysisInput
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import java.util.Collections

/** Pure catalog evaluation. No chemical threshold, score, treatment or dosing is invented. */
object WaterQualityAssessmentEngine {
    const val ENGINE_VERSION = "water-quality-1"
    const val RULE_REVISION = "catalog-guidance-2026-09-27.1"

    fun assess(input: WaterAnalysisInput, context: AquariumHealthContext): WaterQualityAssessment {
        require(input.tankId == context.tankId)
        require(input.measurements.map { it.parameter }.distinct().size == input.measurements.size)
        val water = WaterAssessmentSourceProjection.project(input)
        val recorded = WaterAssessmentSourceProjection.recordedParameters(input)
        val habitatConflicts = mutableListOf<WaterHabitatConflict>()
        val findings = buildList {
            context.livestock.forEach { animal ->
                val identity = WaterAssessmentEntity(
                    WaterAssessmentEntityKind.LIVESTOCK, animal.livestockId, animal.catalogId, animal.displayName
                )
                val habitatGap = WaterHabitatPolicy.gap(context.waterEnvironment, animal.waterGroup)
                if (habitatGap == WaterAssessmentGap.INCOMPATIBLE_HABITAT &&
                    animal.resolution == HealthEntityResolution.RESOLVED
                ) {
                    habitatConflicts += WaterHabitatConflict(identity, requireNotNull(context.waterEnvironment),
                        requireNotNull(animal.waterGroup))
                }
                addAll(evaluate(EntityRequirements(
                    identity,
                    animal.requirements ?: LivestockWaterRequirements(), animal.resolution,
                    context.livestockCatalogRevision, AquariumWaterParameter.entries, habitatGap
                ), water, context.waterEnvironment != null, recorded))
            }
            context.plants.forEach { plant ->
                addAll(evaluate(EntityRequirements(
                    WaterAssessmentEntity(WaterAssessmentEntityKind.PLANT,
                        plant.plantId, plant.catalogId, plant.displayName),
                    WaterAssessmentRequirements.plant(plant), plant.resolution,
                    context.plantCatalogRevision, WaterAssessmentRequirements.plantScope,
                    if (context.waterEnvironment == "Freshwater") null else WaterAssessmentGap.UNKNOWN_HABITAT
                ), water, context.waterEnvironment != null, recorded))
            }
        }.sortedBy { it.ruleId }
        val evaluated = findings.count { it.direction != null }
        val conflicts = WaterRequirementIntersection.conflicts(findings)
        val comparableRequirements = findings.count(WaterRequirementIntersection::hasComparableRequirement)
        return WaterQualityAssessment(
            ENGINE_VERSION, RULE_REVISION, context.revision,
            findings.mapNotNull { it.severity }.maxOrNull(),
            // Chemistry has no verified rule catalog yet: overall coverage cannot be COMPLETE.
            if (evaluated == 0) WaterAssessmentCoverage.NONE else WaterAssessmentCoverage.PARTIAL,
            coverage(comparableRequirements, findings.size),
            Collections.unmodifiableList(findings), Collections.unmodifiableList(conflicts),
            recommendations(findings, conflicts),
            habitatConflicts = Collections.unmodifiableList(habitatConflicts.sortedBy { it.entity.localId })
        )
    }

    private fun evaluate(
        entity: EntityRequirements,
        water: AquariumWaterSnapshot,
        knownTank: Boolean,
        recorded: Set<AquariumWaterParameter>
    ): List<WaterRuleFinding> {
        val compatibility = LivestockWaterCompatibilityEvaluator.evaluate(entity.requirements, water)
        val values = water.values().toMap()
        return entity.scope.map { parameter ->
            val range = WaterAssessmentRequirements.range(entity.requirements, parameter)
            val comparisonGap = compatibility.coverage[parameter].gap()
            val gap = entity.gap(parameter, knownTank) ?: if (
                comparisonGap == WaterAssessmentGap.MEASUREMENT_MISSING && parameter in recorded
            ) WaterAssessmentGap.SOURCE_UNRESOLVED else comparisonGap
            val value = values[parameter]
            val direction = if (gap != null || range == null || value == null) null else {
                when {
                    range.contains(value) -> WaterAssessmentDirection.WITHIN
                    range.minimum?.let { value <= it } == true -> WaterAssessmentDirection.BELOW
                    else -> WaterAssessmentDirection.ABOVE
                }
            }
            val severity = direction?.let {
                if (it == WaterAssessmentDirection.WITHIN) WaterHazardSeverity.NONE else WaterHazardSeverity.ADVISORY
            }
            WaterRuleFinding(entity.identity, parameter,
                "catalog:${entity.identity.kind}:${entity.identity.localId}:$parameter", RULE_REVISION,
                entity.catalogRevision, value, range, direction, severity, gap)
        }
    }

    private fun EntityRequirements.gap(parameter: AquariumWaterParameter, knownTank: Boolean): WaterAssessmentGap? =
        resolution.gap() ?: when {
            !knownTank -> WaterAssessmentGap.UNKNOWN_TANK_TYPE
            habitatGap != null -> habitatGap
            catalogRevision == null -> WaterAssessmentGap.CATALOG_UNAVAILABLE
            parameter !in WaterAssessmentRequirements.comparable -> WaterAssessmentGap.UNKNOWN_REQUIREMENT_SEMANTIC
            else -> null
        }

    private fun recommendations(
        findings: List<WaterRuleFinding>,
        conflicts: List<WaterRequirementConflict>
    ): List<WaterAssessmentRecommendation> = buildList {
        val outside = findings.filter { it.severity == WaterHazardSeverity.ADVISORY }.map { it.ruleId }
        if (outside.isNotEmpty()) {
            add(WaterAssessmentRecommendation("VERIFY_TEST_RESULT", Collections.unmodifiableList(outside)))
        }
        val conflicting = conflicts.flatMap { listOf(it.first.ruleId, it.second.ruleId) }.distinct().sorted()
        if (conflicting.isNotEmpty()) {
            add(WaterAssessmentRecommendation("REVIEW_HABITAT_REQUIREMENTS", Collections.unmodifiableList(conflicting)))
        }
    }.let(Collections::unmodifiableList)

    private fun coverage(compared: Int, required: Int): WaterAssessmentCoverage = when {
        compared == 0 -> WaterAssessmentCoverage.NONE
        compared == required -> WaterAssessmentCoverage.COMPLETE
        else -> WaterAssessmentCoverage.PARTIAL
    }

    private fun HealthEntityResolution.gap(): WaterAssessmentGap? = when (this) {
        HealthEntityResolution.RESOLVED -> null
        HealthEntityResolution.PARTIAL -> WaterAssessmentGap.PARTIAL_PLANT
        HealthEntityResolution.CUSTOM_UNVERIFIED -> WaterAssessmentGap.CUSTOM_UNVERIFIED
        HealthEntityResolution.CATALOG_MISSING -> WaterAssessmentGap.CATALOG_ENTRY_MISSING
        HealthEntityResolution.CATALOG_UNAVAILABLE -> WaterAssessmentGap.CATALOG_UNAVAILABLE
    }

    private fun LivestockParameterCoverage?.gap(): WaterAssessmentGap? = when (this) {
        LivestockParameterCoverage.COMPARED -> null
        LivestockParameterCoverage.MEASUREMENT_MISSING -> WaterAssessmentGap.MEASUREMENT_MISSING
        LivestockParameterCoverage.UNPARSEABLE_REQUIREMENT -> WaterAssessmentGap.UNPARSEABLE_REQUIREMENT
        LivestockParameterCoverage.INFORMATIONAL -> WaterAssessmentGap.INFORMATIONAL
        LivestockParameterCoverage.REQUIREMENT_MISSING, null -> WaterAssessmentGap.REQUIREMENT_MISSING
    }

    private data class EntityRequirements(
        val identity: WaterAssessmentEntity,
        val requirements: LivestockWaterRequirements,
        val resolution: HealthEntityResolution,
        val catalogRevision: String?,
        val scope: List<AquariumWaterParameter>,
        val habitatGap: WaterAssessmentGap? = null
    )
}
