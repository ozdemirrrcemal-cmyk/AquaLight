package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.StoredWaterAnalysis
import com.aqua.aqualight.data.aquarium.health.StoredWaterEvaluation
import com.aqua.aqualight.data.aquarium.health.StoredWaterRuleFinding
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.Writer

/** Human-readable JSON with stable explicit field names and the original frozen evidence. */
internal object WaterHistoryPortableWriter {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    fun write(export: PortableUserDataExport, writer: Writer,
        history: Pair<WaterHistoryArchiveReference, File>?,
        healthHistory: Pair<HealthHistoryArchiveReference, File>? = null) {
        history?.let { (reference, file) ->
            WaterHistoryArchive.validate(reference, file, export.aquariumData.aquariums.map { it.id }.toSet())
        }
        healthHistory?.let { (reference, file) ->
            HealthHistoryArchive.validate(reference, file, export.aquariumData.aquariums.map { it.id }.toSet())
        }
        val document = gson.toJsonTree(export).asJsonObject
        val json = JsonWriter(writer).apply { setIndent("  ") }
        json.beginObject()
        document.entrySet().forEach { (key, value) ->
            json.name(key)
            gson.toJson(value, json)
        }
        json.name("waterAnalyses").beginArray()
        history?.let { (reference, file) ->
            WaterHistoryArchive.visit(file, reference.recordCount) { gson.toJson(event(it), json) }
        }
        json.endArray()
        HealthHistoryPortableWriter.write(json, healthHistory)
        json.endObject()
        json.flush()
    }

    private fun event(record: StoredWaterAnalysis): JsonObject = gson.toJsonTree(linkedMapOf(
        "analysisId" to record.id, "tankId" to record.tankId, "observedAtMillis" to record.measuredAtMillis,
        "createdAtMillis" to record.createdAtMillis, "requestId" to record.requestId,
        "temperatureCelsius" to record.temperatureCelsius.takeIf { record.hasTemperature },
        "temperatureSource" to record.temperatureSource.takeIf { record.hasTemperature },
        "measurements" to record.measurementsList.map { linkedMapOf(
            "parameter" to it.parameter, "value" to it.value, "method" to it.method,
            "testKitId" to it.testKitId, "basis" to it.basis, "unit" to it.unit) },
        "evaluation" to record.evaluation.takeIf { record.hasEvaluation() }?.let(::evaluation),
        "importOrigin" to record.importOrigin.takeIf { record.hasImportOrigin() }?.let { linkedMapOf(
            "sourceOwnerUid" to it.sourceOwnerUid, "sourceAnalysisId" to it.sourceAnalysisId,
            "sourceTankId" to it.sourceTankId, "sourceRequestId" to it.sourceRequestId,
            "sourceRecordSha256" to it.sourceRecordSha256, "restoreTransactionId" to it.restoreTransactionId) }
    )).asJsonObject

    private fun evaluation(value: StoredWaterEvaluation): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to value.schemaVersion, "tankId" to value.tankId,
        "capturedAtMillis" to value.capturedAtMillis, "contextRevision" to value.contextRevision,
        "contextSha256" to value.contextSha256, "context" to JsonParser.parseString(value.contextJson),
        "engineVersion" to value.engineVersion, "ruleRevision" to value.ruleRevision,
        "hazardSeverity" to value.hazardSeverity.ifEmpty { null }, "coverage" to value.coverage,
        "conflictCoverage" to value.conflictCoverage, "chemistryGap" to value.chemistryGap,
        "canonicalMeasurements" to value.canonicalMeasurementsList.map { linkedMapOf(
            "parameter" to it.parameter, "basis" to it.basis, "unit" to it.unit,
            "value" to it.value.takeIf { _ -> it.hasValue() }, "conversionRevision" to it.conversionRevision) },
        "findings" to value.findingsList.map(::finding),
        "conflicts" to value.conflictsList.map { linkedMapOf("firstRuleId" to it.firstRuleId,
            "secondRuleId" to it.secondRuleId, "authoritative" to it.authoritative) },
        "recommendations" to value.recommendationsList.map { linkedMapOf(
            "code" to it.code, "findingRuleIds" to it.findingRuleIdsList) },
        "habitatConflicts" to value.habitatConflictsList.map { linkedMapOf(
            "entityKind" to it.entityKind, "entityId" to it.entityId, "catalogId" to it.catalogId,
            "entityDisplayName" to it.entityDisplayName, "tankEnvironment" to it.tankEnvironment,
            "sourceWaterGroup" to it.sourceWaterGroup) }
    )

    internal fun finding(value: StoredWaterRuleFinding): Map<String, Any?> = linkedMapOf(
        "entityKind" to value.entityKind, "entityId" to value.entityId, "catalogId" to value.catalogId,
        "entityDisplayName" to value.entityDisplayName, "parameter" to value.parameter,
        "ruleId" to value.ruleId, "ruleRevision" to value.ruleRevision, "catalogRevision" to value.catalogRevision,
        "measuredValue" to value.measuredValue.takeIf { value.hasMeasuredValue() },
        "direction" to value.direction, "severity" to value.severity.ifEmpty { null }, "gap" to value.gap,
        "expectedRange" to value.expectedRange.takeIf { value.hasExpectedRange() }?.let { range -> linkedMapOf(
            "minimum" to range.minimum.takeIf { range.hasMinimum() },
            "maximum" to range.maximum.takeIf { range.hasMaximum() },
            "minimumInclusive" to range.minimumInclusive, "maximumInclusive" to range.maximumInclusive,
            "approximate" to range.approximate,
            "nominalTarget" to range.nominalTarget.takeIf { range.hasNominalTarget() }) }
    )
}
