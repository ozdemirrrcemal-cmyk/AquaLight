package com.aqua.aqualight.application.aquarium

data class LivestockCatalogItem(
    val id: String,
    val category: String,
    val commonName: String,
    val turkishName: String?,
    val scientificName: String?,
    val recordType: String?,
    val waterGroup: String?,
    val temperatureC: String?,
    val ph: String?,
    val specificGravity: String?,
    val waterRequirements: LivestockWaterRequirements
) {
    fun displayName(
        languageCode: String
    ): String {
        return if (languageCode.equals("tr", ignoreCase = true)) {
            turkishName?.takeIf(String::isNotBlank) ?: commonName
        } else {
            commonName
        }
    }

    fun matches(
        query: String
    ): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return true
        }

        return listOfNotNull(
            commonName,
            turkishName,
            scientificName,
            recordType,
            waterGroup
        ).any { value ->
            value.contains(normalizedQuery, ignoreCase = true)
        }
    }

    fun parameterSummary(): String {
        return buildList {
            temperatureC?.takeIf(String::isNotBlank)?.let { value ->
                add("$value °C")
            }
            ph?.takeIf(String::isNotBlank)?.let { value ->
                add("pH $value")
            }
            specificGravity?.takeIf(String::isNotBlank)?.let { value ->
                add("SG $value")
            }
        }.joinToString(separator = " • ")
    }
}

interface LivestockCatalogOperations {
    fun entries(): List<LivestockCatalogItem>
    fun findById(entryId: String): LivestockCatalogItem?
}
