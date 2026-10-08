package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumLivestockIdentity

internal class LivestockSelectionValidator(
    context: Context
) {

    private val appContext = context.applicationContext

    fun requireCurrent(
        livestock: AquariumLivestock
    ) {
        AquariumLivestockIdentity.requireValid(
            livestockId = livestock.id,
            catalogEntryId = livestock.catalogEntryId
        )

        if (AquariumLivestockIdentity.isCustom(livestock.catalogEntryId)) {
            return
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
    }
}
