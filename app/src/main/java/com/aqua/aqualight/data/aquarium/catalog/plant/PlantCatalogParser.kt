package com.aqua.aqualight.data.aquarium.catalog.plant

import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.aqua.aqualight.application.aquarium.catalog.plant.PlantCareCatalogSnapshot
import com.aqua.aqualight.application.aquarium.AquariumPlantLightCatalog
import java.security.MessageDigest
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object PlantCatalogParser {
    const val EXPECTED_RECORD_COUNT = 271
    const val CONTENT_REVISION = "plant-care-2026-09-27.1"
    const val CONTENT_SHA256 = "64f3ed5ad4a0cb2016243f810c5ee1fdd76570a9839163c4ff8614e3492d9832"

    fun parse(json: String): PlantCareCatalogSnapshot {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        require(sha256 == CONTENT_SHA256) { "Plant content changed without a reviewed content revision." }
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("recordCount").asInt == EXPECTED_RECORD_COUNT)
        require(root.get("schema").asString == "aqualight.plant.catalog.v1")
        val rows = root.getAsJsonArray("records")
        require(rows.size() == EXPECTED_RECORD_COUNT)
        val result = rows.map { element -> parseRow(element.asJsonObject) }
        require(result.map(AquariumPlantCatalogRecord::id).distinct().size == result.size)
        require(result.all { record ->
            record.placement.isNotEmpty() && record.displayName.isNotBlank() &&
                record.care.lightRequirement != "UNKNOWN" &&
                record.care.healthDataStatus in setOf("VERIFIED", "PARTIAL") &&
                record.care.healthAnalysisReady == (record.care.healthDataStatus == "VERIFIED")
        })
        require(result.map(AquariumPlantCatalogRecord::id).toSet() == AquariumPlantLightCatalog.catalogIds)
        require(result.all { record ->
            AquariumPlantLightCatalog.requireRecord(record.id).lightRequirement == record.care.lightRequirement
        })
        return PlantCareCatalogSnapshot(CONTENT_REVISION, sha256, result)
    }

    private fun parseRow(row: JsonObject): AquariumPlantCatalogRecord {
        PlantCatalogValidation.validate(row)
        val id = "plant:" + row.text("recordId").removePrefix("plant-").replace('-', '_')
        val care = AquariumPlantCare(
            lightRequirement = row.text("lightRequirement"),
            co2Requirement = row.text("co2Requirement"),
            difficulty = row.text("difficulty"),
            growthRate = row.text("growthRate"),
            temperatureMinC = row.number("temperatureMinC"),
            temperatureMaxC = row.number("temperatureMaxC"),
            pHMin = row.number("pHMin"),
            pHMax = row.number("pHMax"),
            khMin = row.number("KHMin_dKH"),
            khMax = row.number("KHMax_dKH"),
            ghMin = row.number("GHMin_dGH"),
            ghMax = row.number("GHMax_dGH"),
            nutrientDemand = row.text("nutrientDemand"),
            substrateRequirement = row.text("substrateRequirement"),
            rootFeeder = row.flag("rootFeeder"),
            waterColumnFeeder = row.flag("waterColumnFeeder"),
            healthDataStatus = row.text("healthDataStatus"),
            healthAnalysisReady = row.get("healthAnalysisReady").asBoolean,
            verifiedCareFields = row.getAsJsonArray("verifiedCareFields")
                .map { it.asString }.toSet()
        )
        return AquariumPlantCatalogRecord(
            id = id,
            displayName = row.text("displayName"),
            canonicalScientificName = row.text("canonicalScientificName"),
            searchNames = (row.text("aliases") + "|" + row.text("commonNames"))
                .split('|', ';').map(String::trim).filter(String::isNotBlank).toSet(),
            placement = row.text("placement").split('|').filter(String::isNotBlank).toSet(),
            growthForm = row.text("growthForm"),
            catalogFlags = row.getAsJsonArray("catalogFlags").map { it.asString }.toSet(),
            care = care
        )
    }

    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }
        ?.asString.orEmpty()

    private fun JsonObject.number(key: String): Double? = get(key)?.takeUnless { it.isJsonNull }
        ?.asDouble

    private fun JsonObject.flag(key: String): Boolean? = get(key)?.takeUnless { it.isJsonNull }
        ?.asBoolean
}
