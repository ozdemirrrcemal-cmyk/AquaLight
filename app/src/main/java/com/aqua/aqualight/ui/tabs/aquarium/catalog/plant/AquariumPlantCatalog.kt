package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumPlantCare
import com.aqua.aqualight.application.aquarium.AquariumPlantCatalogRecord
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object AquariumPlantCatalog {
    const val EXPECTED_RECORD_COUNT = 248
    private const val ASSET_NAME = "aqualight_plant_catalog.json"

    @Volatile private var cachedRecords: List<AquariumPlantCatalogRecord>? = null

    fun records(context: Context): List<AquariumPlantCatalogRecord> = cachedRecords
        ?: synchronized(this) {
            cachedRecords ?: parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() })
                .also { cachedRecords = it }
        }

    fun record(context: Context, catalogId: String): AquariumPlantCatalogRecord? =
        records(context).firstOrNull { it.id == catalogId }

    fun parse(json: String): List<AquariumPlantCatalogRecord> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("recordCount").asInt == EXPECTED_RECORD_COUNT)
        val rows = root.getAsJsonArray("records")
        require(rows.size() == EXPECTED_RECORD_COUNT)
        val result = rows.map { element ->
            val row = element.asJsonObject
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
            AquariumPlantCatalogRecord(
                id = id,
                displayName = row.text("displayName"),
                canonicalScientificName = row.text("canonicalScientificName"),
                placement = row.text("placement").split('|').filter(String::isNotBlank).toSet(),
                growthForm = row.text("growthForm"),
                care = care
            )
        }
        require(result.map(AquariumPlantCatalogRecord::id).distinct().size == result.size)
        require(result.all { record ->
            record.placement.isNotEmpty() && record.displayName.isNotBlank() &&
                record.care.lightRequirement != "UNKNOWN" &&
                record.care.healthDataStatus in setOf("VERIFIED", "PARTIAL") &&
                record.care.healthAnalysisReady == (record.care.healthDataStatus == "VERIFIED")
        })
        return result
    }

    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }
        ?.asString.orEmpty()

    private fun JsonObject.number(key: String): Double? = get(key)?.takeUnless { it.isJsonNull }
        ?.asDouble

    private fun JsonObject.flag(key: String): Boolean? = get(key)?.takeUnless { it.isJsonNull }
        ?.asBoolean
}
