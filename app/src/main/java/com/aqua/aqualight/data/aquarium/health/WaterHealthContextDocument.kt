package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumWaterParameter
import com.aqua.aqualight.application.aquarium.LivestockParameterRange
import com.aqua.aqualight.application.aquarium.LivestockRequirementParseResult
import com.aqua.aqualight.application.aquarium.health.context.AquariumHealthContext
import com.aqua.aqualight.application.aquarium.health.water.WaterAssessmentRequirements
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.security.MessageDigest

/** Explicit field names survive R8. This historical document is never resolved against live catalogs. */
internal object WaterHealthContextDocument {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()

    fun encode(context: AquariumHealthContext): String = gson.toJson(linkedMapOf(
        "schemaVersion" to 1,
        "tankId" to context.tankId,
        "capturedAtMillis" to context.capturedAtMillis,
        "timeBasis" to context.timeBasis.name,
        "tankType" to context.tankType,
        "waterEnvironment" to context.waterEnvironment,
        "setupDateEpochDay" to context.setupDateEpochDay,
        "geometricVolumeLitres" to context.geometricVolumeLitres,
        "dimensionsCm" to context.tank.dimensionsCm?.let {
            mapOf("width" to it.width, "length" to it.length, "height" to it.height)
        },
        "plantCatalogRevision" to context.plantCatalogRevision,
        "livestockCatalogRevision" to context.livestockCatalogRevision,
        "materials" to context.materials.map { linkedMapOf(
            "selectionId" to it.selectionId, "productId" to it.productId, "categoryKey" to it.categoryKey
        ) },
        "plants" to context.plants.map { linkedMapOf(
            "plantId" to it.plantId, "catalogId" to it.catalogId, "displayName" to it.displayName,
            "resolution" to it.resolution.name, "care" to it.care?.let(::plantCare)
        ) },
        "livestock" to context.livestock.map { animal -> linkedMapOf(
            "livestockId" to animal.livestockId, "catalogId" to animal.catalogId,
            "displayName" to animal.displayName, "quantity" to animal.quantity,
            "resolution" to animal.resolution.name, "waterGroup" to animal.waterGroup,
            "warningMode" to animal.requirements?.warningMode?.name,
            "confidence" to animal.requirements?.evidence?.confidence,
            "rawWarningMode" to animal.requirements?.evidence?.rawWarningMode,
            "requirements" to animal.requirements?.let { requirements ->
                AquariumWaterParameter.entries.associate { parameter -> parameter.name to linkedMapOf(
                    "range" to WaterAssessmentRequirements.range(requirements, parameter)?.let(::range),
                    "parseResult" to when (val result = requirements.evidence?.parameters?.get(parameter)) {
                        is LivestockRequirementParseResult.Parsed -> mapOf("kind" to "PARSED", "raw" to result.raw)
                        is LivestockRequirementParseResult.Unparseable ->
                            mapOf("kind" to "UNPARSEABLE", "raw" to result.raw)
                        else -> mapOf("kind" to "MISSING")
                    }
                ) }
            }
        ) }
    ))

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun contentRevision(context: AquariumHealthContext): String {
        val content = JsonParser.parseString(encode(context)).asJsonObject
        content.remove("capturedAtMillis")
        return "health-context-v1:" + sha256(gson.toJson(content))
    }

    private fun range(value: LivestockParameterRange): Map<String, Any?> = linkedMapOf(
        "minimum" to value.minimum, "maximum" to value.maximum,
        "minimumInclusive" to value.minimumInclusive, "maximumInclusive" to value.maximumInclusive,
        "approximate" to value.approximate, "nominalTarget" to value.nominalTarget
    )

    private fun plantCare(care: AquariumPlantCare): Map<String, Any?> = linkedMapOf(
        "lightRequirement" to care.lightRequirement, "co2Requirement" to care.co2Requirement,
        "difficulty" to care.difficulty, "growthRate" to care.growthRate,
        "temperatureMinC" to care.temperatureMinC, "temperatureMaxC" to care.temperatureMaxC,
        "pHMin" to care.pHMin, "pHMax" to care.pHMax, "khMin" to care.khMin, "khMax" to care.khMax,
        "ghMin" to care.ghMin, "ghMax" to care.ghMax, "nutrientDemand" to care.nutrientDemand,
        "substrateRequirement" to care.substrateRequirement, "rootFeeder" to care.rootFeeder,
        "waterColumnFeeder" to care.waterColumnFeeder, "healthDataStatus" to care.healthDataStatus,
        "healthAnalysisReady" to care.healthAnalysisReady, "verifiedCareFields" to care.verifiedCareFields.sorted()
    )
}
