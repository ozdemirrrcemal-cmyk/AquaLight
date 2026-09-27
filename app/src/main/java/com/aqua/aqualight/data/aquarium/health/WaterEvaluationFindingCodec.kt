package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentDirection
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntity
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentEntityKind
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentGap
import com.aqua.aqualight.application.aquarium.health.water.WaterHazardSeverity
import com.aqua.aqualight.application.aquarium.health.water.WaterRuleFinding

internal object WaterEvaluationFindingCodec {
    fun encode(finding: WaterRuleFinding): StoredWaterRuleFinding = StoredWaterRuleFinding.newBuilder()
        .setEntityKind(finding.entity.kind.name).setEntityId(finding.entity.localId)
        .setCatalogId(finding.entity.catalogId).setEntityDisplayName(finding.entity.displayName)
        .setParameter(finding.parameter.name)
        .setRuleId(finding.ruleId).setRuleRevision(finding.ruleRevision)
        .setCatalogRevision(finding.catalogRevision.orEmpty())
        .setDirection(finding.direction?.name.orEmpty()).setSeverity(finding.severity?.name.orEmpty())
        .setGap(finding.gap?.name.orEmpty())
        .apply {
            finding.measuredValue?.let(::setMeasuredValue)
            finding.expectedRange?.let { setExpectedRange(encodeRange(it)) }
        }.build()

    fun decode(stored: StoredWaterRuleFinding): WaterRuleFinding {
        require(stored.entityId > 0 && stored.catalogId.isNotBlank())
        require(stored.ruleId.isNotBlank() && stored.ruleRevision.isNotBlank())
        val measured = stored.measuredValue.takeIf { stored.hasMeasuredValue() }
        require(measured?.isFinite() != false)
        val range = if (stored.hasExpectedRange()) decodeRange(stored.expectedRange) else null
        val direction = optionalEnum<WaterAssessmentDirection>(stored.direction)
        val severity = optionalEnum<WaterHazardSeverity>(stored.severity)
        val gap = optionalEnum<WaterAssessmentGap>(stored.gap)
        require((direction == null) == (severity == null) && (direction == null) == (gap != null))
        require(direction == null || measured != null && range != null && stored.catalogRevision.isNotBlank())
        validateOutcome(direction, severity, measured, range)
        return WaterRuleFinding(
            WaterAssessmentEntity(enumValue<WaterAssessmentEntityKind>(stored.entityKind),
                stored.entityId, stored.catalogId, stored.entityDisplayName),
            enumValue<AquariumWaterParameter>(stored.parameter), stored.ruleId, stored.ruleRevision,
            stored.catalogRevision.ifBlank { null }, measured, range, direction, severity, gap
        )
    }

    private fun validateOutcome(
        direction: WaterAssessmentDirection?,
        severity: WaterHazardSeverity?,
        measured: Double?,
        range: LivestockParameterRange?
    ) {
        if (direction == null) return
        val value = requireNotNull(measured)
        val bounds = requireNotNull(range)
        require(bounds.hasComparableBounds && !bounds.approximate)
        require((direction == WaterAssessmentDirection.WITHIN) == (severity == WaterHazardSeverity.NONE))
        val consistent = when (direction) {
            WaterAssessmentDirection.WITHIN -> bounds.contains(value)
            WaterAssessmentDirection.BELOW -> bounds.minimum?.let {
                value < it || value == it && !bounds.minimumInclusive
            } == true
            WaterAssessmentDirection.ABOVE -> bounds.maximum?.let {
                value > it || value == it && !bounds.maximumInclusive
            } == true
        }
        require(consistent)
    }

    private fun encodeRange(range: LivestockParameterRange): StoredWaterRequirementRange =
        StoredWaterRequirementRange.newBuilder().setApproximate(range.approximate)
            .setMinimumInclusive(range.minimumInclusive).setMaximumInclusive(range.maximumInclusive)
            .apply {
                range.minimum?.let(::setMinimum)
                range.maximum?.let(::setMaximum)
                range.nominalTarget?.let(::setNominalTarget)
            }.build()

    private fun decodeRange(range: StoredWaterRequirementRange): LivestockParameterRange = LivestockParameterRange(
        range.minimum.takeIf { range.hasMinimum() }, range.maximum.takeIf { range.hasMaximum() },
        range.approximate, range.minimumInclusive, range.maximumInclusive,
        range.nominalTarget.takeIf { range.hasNominalTarget() }
    )

    internal inline fun <reified T : Enum<T>> optionalEnum(value: String): T? =
        value.takeIf { it.isNotBlank() }?.let { enumValue<T>(it) }

    internal inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw WaterAnalysisReadFailure.UnsupportedValue("evaluation.${T::class.simpleName}", value)
}
