package com.aqua.aqualight.ui.tabs.aquarium.catalog.livestock

import android.content.Context
import androidx.core.os.ConfigurationCompat

data class LivestockCatalogEntry(
    val id: String,
    val category: String,
    val commonName: String,
    val turkishName: String?,
    val scientificName: String?,
    val recordType: String?,
    val waterGroup: String?,
    val temperatureC: String?,
    val ph: String?,
    val ghDgh: String?,
    val khDkh: String?,
    val tdsPpm: String?,
    val specificGravity: String?,
    val alkalinityDkh: String?,
    val calciumPpm: String?,
    val magnesiumPpm: String?,
    val nitratePpm: String?,
    val phosphatePpm: String?,
    val par: String?,
    val flow: String?,
    val note: String?,
    val confidence: String?,
    val warningMode: String
) {

    val waterRequirements: LivestockWaterRequirements by lazy(LazyThreadSafetyMode.PUBLICATION) {
        LivestockWaterRequirementParser.parse(this)
    }

    fun displayName(
        context: Context
    ): String {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
        val isTurkish = locale?.language.equals("tr", ignoreCase = true)

        return if (isTurkish) {
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
        val parts = buildList {
            temperatureC?.takeIf(String::isNotBlank)?.let { value ->
                add("$value °C")
            }
            ph?.takeIf(String::isNotBlank)?.let { value ->
                add("pH $value")
            }
            specificGravity?.takeIf(String::isNotBlank)?.let { value ->
                add("SG $value")
            }
        }

        return parts.joinToString(separator = " • ")
    }

    fun matchesSavedName(
        savedName: String
    ): Boolean {
        val normalized = savedName.trim()
        if (normalized.isBlank()) {
            return false
        }

        return commonName.equals(normalized, ignoreCase = true) ||
            turkishName.equals(normalized, ignoreCase = true)
    }
}