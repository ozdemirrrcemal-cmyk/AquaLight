package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity
import com.aqua.aqualight.application.aquarium.AquariumLivestockTaxonomy
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import org.json.JSONObject

object LivestockCatalog {

    private const val ASSET_FILE = "livestock_catalog.jsonl"

    @Volatile
    private var cachedEntries: List<LivestockCatalogEntry>? = null

    fun entries(
        context: Context
    ): List<LivestockCatalogEntry> {
        cachedEntries?.let { entries ->
            return entries
        }

        return synchronized(this) {
            cachedEntries ?: loadEntries(context.applicationContext).also { entries ->
                validate(entries)
                cachedEntries = entries
            }
        }
    }

    fun findById(
        context: Context,
        entryId: String
    ): LivestockCatalogEntry? {
        val canonicalId = entryId.trim()
        if (canonicalId.isBlank() || AquariumLivestockIdentity.isCustom(canonicalId)) {
            return null
        }

        return entries(context).firstOrNull { entry ->
            entry.id == canonicalId
        }
    }

    fun requirementsFor(
        context: Context,
        catalogEntryId: String
    ): LivestockWaterRequirements? {
        return findById(
            context = context,
            entryId = catalogEntryId
        )?.waterRequirements
    }

    private fun loadEntries(
        context: Context
    ): List<LivestockCatalogEntry> {
        return context.assets.open(ASSET_FILE)
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank()) {
                        null
                    } else {
                        parseLine(
                            lineNumber = index + 1,
                            raw = trimmed
                        )
                    }
                }.toList()
            }
    }

    private fun parseLine(
        lineNumber: Int,
        raw: String
    ): LivestockCatalogEntry {
        val json = runCatching {
            JSONObject(raw)
        }.getOrElse { cause ->
            throw IllegalStateException(
                "Invalid livestock catalog JSON at line $lineNumber.",
                cause
            )
        }

        return LivestockCatalogEntry(
            id = json.requiredString("id", lineNumber),
            category = json.requiredString("category", lineNumber),
            commonName = json.requiredString("commonName", lineNumber),
            turkishName = json.optionalString("turkishName"),
            scientificName = json.optionalString("scientificName"),
            recordType = json.optionalString("recordType"),
            waterGroup = json.optionalString("waterGroup"),
            temperatureC = json.optionalString("temperatureC"),
            ph = json.optionalString("ph"),
            ghDgh = json.optionalString("ghDgh"),
            khDkh = json.optionalString("khDkh"),
            tdsPpm = json.optionalString("tdsPpm"),
            specificGravity = json.optionalString("specificGravity"),
            alkalinityDkh = json.optionalString("alkalinityDkh"),
            calciumPpm = json.optionalString("calciumPpm"),
            magnesiumPpm = json.optionalString("magnesiumPpm"),
            nitratePpm = json.optionalString("nitratePpm"),
            phosphatePpm = json.optionalString("phosphatePpm"),
            par = json.optionalString("par"),
            flow = json.optionalString("flow"),
            note = json.optionalString("note"),
            confidence = json.optionalString("confidence"),
            warningMode = json.requiredString("warningMode", lineNumber)
        )
    }

    private fun validate(
        entries: List<LivestockCatalogEntry>
    ) {
        check(entries.map(LivestockCatalogEntry::id).toSet().size == entries.size) {
            "Livestock catalog contains duplicate ids."
        }

        check(entries.all { entry ->
            entry.id.isNotBlank() &&
                entry.id == entry.id.trim() &&
                AquariumLivestockIdentity.isCustom(entry.id).not()
        }) {
            "Livestock catalog contains an invalid stable id."
        }

        check(entries.all { entry ->
            entry.warningMode in setOf("SOFT", "HARD", "INFORMATIONAL")
        }) {
            "Livestock catalog contains an unsupported warning mode."
        }

        check(entries.all { entry -> entry.category in AquariumLivestockTaxonomy.categoryCodes }) {
            "Livestock catalog contains an unsupported category."
        }

        AquariumLivestockTaxonomy.categoryCodes.forEach { category ->
            check(entries.any { entry -> entry.category == category }) {
                "Livestock catalog category is empty: $category"
            }
        }
    }

    private fun JSONObject.requiredString(
        key: String,
        lineNumber: Int
    ): String {
        return optionalString(key)
            ?: error(
                "Missing required livestock catalog field '$key' at line $lineNumber."
            )
    }

    private fun JSONObject.optionalString(
        key: String
    ): String? {
        if (!has(key) || isNull(key)) {
            return null
        }

        return optString(key)
            .trim()
            .takeIf(String::isNotBlank)
    }
}
