package com.aqua.aqualight.data.user.archive

import com.aqua.aqualight.data.aquarium.health.observation.StoredHealthObservation
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.stream.JsonWriter
import java.io.File
import java.util.Base64

/** Stable readable field names plus the lossless wire record, including unknown future fields. */
internal object HealthHistoryPortableWriter {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    fun write(json: JsonWriter, history: Pair<HealthHistoryArchiveReference, File>?) {
        json.name("healthObservations").beginArray()
        history?.let { (reference, file) ->
            HealthHistoryArchive.visit(file, reference.recordCount) { row ->
                gson.toJson(gson.toJsonTree(event(row)), json)
            }
        }
        json.endArray()
    }

    private fun event(row: StoredHealthObservation): Map<String, Any?> = linkedMapOf(
        "schemaVersion" to row.schemaVersion, "observationId" to row.id, "ownerUid" to row.ownerUid,
        "createdAtMillis" to row.createdAtMillis, "tankId" to row.input.tankId,
        "observedAtMillis" to row.input.observedAtMillis, "requestId" to row.input.requestId,
        "kind" to row.input.kind, "subjectId" to row.input.subjectId.takeIf { row.hasSubject() },
        "findings" to row.input.findingsList, "algaeLocations" to row.input.algaeLocationsList,
        "algaeExtent" to row.input.algaeExtent.ifEmpty { null },
        "affectedQuantity" to row.input.affectedQuantity.takeIf { row.input.hasAffectedQuantity() },
        "note" to row.input.note, "photoIncluded" to false, "hasPhoto" to row.input.photoUri.isNotEmpty(),
        "phase" to row.input.phase,
        "previousObservationId" to row.input.previousObservationId.takeIf { row.input.hasPreviousObservationId() },
        "operatingEvidence" to row.input.operating.takeIf { row.input.hasOperating() }?.let { linkedMapOf(
            "photoperiodMinutes" to it.photoperiodMinutes.takeIf { _ -> it.hasPhotoperiodMinutes() },
            "lightMeasurement" to it.lightMeasurement, "co2Pattern" to it.co2Pattern,
            "dosingHistory" to it.dosingHistory) },
        "subject" to row.subject.takeIf { row.hasSubject() }?.let { linkedMapOf(
            "originalLocalId" to it.originalLocalId, "catalogId" to it.catalogId,
            "displayName" to it.displayName, "quantity" to it.quantity.takeIf { _ -> it.hasQuantity() },
            "resolution" to it.resolution) },
        "evidence" to evidence(row),
        "assessment" to linkedMapOf("engineRevision" to row.assessment.engineRevision,
            "state" to row.assessment.state, "gaps" to row.assessment.gapsList,
            "actions" to row.assessment.actionsList,
            "waterFindings" to row.assessment.waterFindingsList.map(WaterHistoryPortableWriter::finding)),
        "importOrigin" to row.origin.takeIf { row.hasOrigin() }?.let { linkedMapOf(
            "sourceOwnerUid" to it.sourceOwnerUid, "sourceObservationId" to it.sourceObservationId,
            "sourceTankId" to it.sourceTankId, "sourceSubjectId" to it.sourceSubjectId,
            "sourceRequestId" to it.sourceRequestId, "sourceRecordSha256" to it.sourceRecordSha256,
            "sourcePreviousObservationId" to it.sourcePreviousObservationId
                .takeIf { _ -> it.hasSourcePreviousObservationId() },
            "restoreTransactionId" to it.restoreTransactionId) },
        "wireFormat" to "StoredHealthObservation/proto3",
        "wireBase64" to Base64.getEncoder().encodeToString(row.toByteArray())
    )

    private fun evidence(row: StoredHealthObservation): Map<String, Any?> = linkedMapOf(
        "originalTankId" to row.evidence.originalTankId,
        "contextCapturedAtMillis" to row.evidence.contextCapturedAtMillis,
        "contextRevision" to row.evidence.contextRevision, "contextSha256" to row.evidence.contextSha256,
        "context" to JsonParser.parseString(row.evidence.contextJson),
        "waterRelation" to row.evidence.waterRelation, "waterPolicyRevision" to row.evidence.waterPolicyRevision,
        "waterMaximumAgeMillis" to row.evidence.waterMaximumAgeMillis,
        "waterAgeAtObservationMillis" to row.evidence.waterAgeAtObservationMillis
            .takeIf { row.evidence.hasWaterAgeAtObservationMillis() },
        "water" to row.evidence.water.takeIf { row.evidence.hasWater() }?.let { water -> linkedMapOf(
            "analysisId" to water.analysisId, "originalTankId" to water.originalTankId,
            "measuredAtMillis" to water.measuredAtMillis, "createdAtMillis" to water.createdAtMillis,
            "temperatureCelsius" to water.temperatureCelsius.takeIf { water.hasTemperatureCelsius() },
            "temperatureSource" to water.temperatureSource,
            "assessmentContextRevision" to water.assessmentContextRevision,
            "rawMeasurements" to water.rawMeasurementsList.map { linkedMapOf(
                "parameter" to it.parameter, "value" to it.value, "method" to it.method,
                "testKitId" to it.testKitId, "basis" to it.basis, "unit" to it.unit) },
            "canonicalMeasurements" to water.canonicalMeasurementsList.map { linkedMapOf(
                "parameter" to it.parameter, "basis" to it.basis, "unit" to it.unit,
                "value" to it.value.takeIf { _ -> it.hasValue() }, "conversionRevision" to it.conversionRevision) }) }
    )
}
