package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import android.content.Context
import org.json.JSONObject

object LivestockCatalog {

    const val EXPECTED_ENTRY_COUNT = 687

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
        if (entryId.isBlank()) {
            return null
        }

        return entries(context).firstOrNull { entry ->
            entry.id == entryId
        }
    }

    fun findBySavedSelection(
        context: Context,
        name: String,
        category: String
    ): LivestockCatalogEntry? {
        return entries(context).firstOrNull { entry ->
            entry.category == category && entry.matchesSavedName(name)
        }
    }

    fun resolveSavedSelection(
        context: Context,
        catalogEntryId: String,
        name: String,
        category: String
    ): LivestockCatalogEntry? {
        return findById(context, catalogEntryId)
            ?: findBySavedSelection(
                context = context,
                name = name,
                category = category
            )
    }

    fun requirementsFor(
        context: Context,
        catalogEntryId: String,
        name: String,
        category: String
    ): LivestockWaterRequirements? {
        return resolveSavedSelection(
            context = context,
            catalogEntryId = catalogEntryId,
            name = name,
            category = category
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
            warningMode = json.optionalString("warningMode") ?: "SOFT"
        )
    }

    private fun validate(
        entries: List<LivestockCatalogEntry>
    ) {
        check(entries.size == EXPECTED_ENTRY_COUNT) {
            "Livestock catalog entry count mismatch: ${entries.size}."
        }

        check(entries.map(LivestockCatalogEntry::id).toSet().size == entries.size) {
            "Livestock catalog contains duplicate ids."
        }

        check(entries.all { entry -> entry.category in LivestockCategories.all }) {
            "Livestock catalog contains an unsupported category."
        }

        LivestockCategories.all.forEach { category ->
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
            ?: throw IllegalStateException(
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