package com.aqua.aqualight.data.devices.add

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import java.util.Locale

object DeviceSetupSsidParser {

    private const val MIN_PART_COUNT = 3

    fun parse(
        ssid: String
    ): DeviceAddCandidate? {
        val parts = ssid
            .trim()
            .split("-")
            .map { part -> part.trim() }
            .filter { part -> part.isNotBlank() }

        if (parts.size < MIN_PART_COUNT) {
            return null
        }

        val familyPart = parts.first()
        val shortId = parts.last()
        val modelSlug = parts
            .drop(1)
            .dropLast(1)
            .joinToString(separator = "")

        if (
            familyPart.isBlank() ||
            modelSlug.isBlank() ||
            shortId.isBlank()
        ) {
            return null
        }

        val definition = AquaDeviceCatalog.allDefinitions.firstOrNull { definition ->
            matchesFamily(
                ssidFamily = familyPart,
                catalogFamily = definition.productFamily
            ) ||
                matchesFamily(
                    ssidFamily = familyPart,
                    catalogFamily = definition.legacyAquaName
                ) ||
                matchesFamily(
                    ssidFamily = familyPart,
                    catalogFamily = definition.family.legacyAquaName
                )
        }?.let { _ ->
            AquaDeviceCatalog.allDefinitions.firstOrNull { definition ->
                val familyMatches =
                    matchesFamily(familyPart, definition.productFamily) ||
                        matchesFamily(familyPart, definition.legacyAquaName) ||
                        matchesFamily(familyPart, definition.family.legacyAquaName)

                val modelMatches =
                    matchesModel(modelSlug, definition.productModel) ||
                        matchesModel(modelSlug, definition.legacyName) ||
                        matchesModel(modelSlug, definition.displayName)

                familyMatches && modelMatches
            }
        } ?: return null

        return DeviceAddCandidate(
            key = "setup:${ssid}",
            source = DeviceAddSource.SETUP_AP,
            displayName = definition.displayName,
            familyName = definition.family.displayName,
            deviceType = definition.type,
            stateText = "Ready for setup",
            actionText = "Set up",
            setupSsid = ssid,
            setupShortId = shortId
        )
    }

    fun isPossibleAquaSetupSsid(
        ssid: String
    ): Boolean {
        val normalized = ssid.trim()

        return normalized.startsWith("AquaLight-", ignoreCase = true) ||
            normalized.startsWith("AquaTimer-", ignoreCase = true) ||
            normalized.startsWith("AquaCool-", ignoreCase = true) ||
            normalized.startsWith("AquaControl-", ignoreCase = true) ||
            normalized.startsWith("Proelite-", ignoreCase = true)
    }

    private fun matchesFamily(
        ssidFamily: String,
        catalogFamily: String
    ): Boolean {
        return normalize(ssidFamily) == normalize(catalogFamily)
    }

    private fun matchesModel(
        ssidModel: String,
        catalogModel: String
    ): Boolean {
        return normalize(ssidModel) == normalize(catalogModel)
    }

    private fun normalize(
        value: String
    ): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")
    }
}