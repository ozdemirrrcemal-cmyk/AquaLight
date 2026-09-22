package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity

/**
 * Background-safe resolver for livestock husbandry requirements.
 *
 * Tank records persist only a stable catalog identity. Care values remain canonical in the bundled
 * catalog so future sensor/measurement checks always use the current AquaLight profile instead of a
 * duplicated snapshot stored on every tank item.
 */
class LivestockCareProfileResolver(
    context: Context
) {

    private val appContext = context.applicationContext

    fun resolve(
        livestock: AquariumLivestock
    ): ResolvedLivestockCareProfile? {
        AquariumLivestockIdentity.requireValid(
            livestockId = livestock.id,
            catalogEntryId = livestock.catalogEntryId
        )

        if (AquariumLivestockIdentity.isCustom(livestock.catalogEntryId)) {
            return null
        }

        val entry = requireNotNull(
            LivestockCatalog.findById(
                context = appContext,
                entryId = livestock.catalogEntryId
            )
        ) {
            "Livestock catalog entry is unavailable: ${livestock.catalogEntryId}"
        }

        require(entry.category == livestock.category) {
            "Livestock category does not match its catalog entry."
        }

        return ResolvedLivestockCareProfile(
            catalogEntryId = entry.id,
            category = entry.category,
            commonName = entry.commonName,
            scientificName = entry.scientificName,
            waterGroup = entry.waterGroup,
            requirements = entry.waterRequirements,
            confidence = entry.confidence
        )
    }

    fun resolveAll(
        livestock: List<AquariumLivestock>
    ): List<ResolvedLivestockCareProfile> {
        return livestock.mapNotNull(::resolve)
    }
}

data class ResolvedLivestockCareProfile(
    val catalogEntryId: String,
    val category: String,
    val commonName: String,
    val scientificName: String?,
    val waterGroup: String?,
    val requirements: LivestockWaterRequirements,
    val confidence: String?
)
