package com.aqua.aqualight.data.aquarium.catalog.livestock

import android.content.Context
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.application.aquarium.LivestockCatalogOperations

class DefaultLivestockCatalogOperations(
    context: Context
) : LivestockCatalogOperations {

    private val appContext = context.applicationContext

    override fun entries(): List<LivestockCatalogItem> {
        return LivestockCatalog.entries(appContext).map(LivestockCatalogEntry::toApplicationItem)
    }

    override fun findById(
        entryId: String
    ): LivestockCatalogItem? {
        return LivestockCatalog.findById(
            context = appContext,
            entryId = entryId
        )?.toApplicationItem()
    }
}

private fun LivestockCatalogEntry.toApplicationItem(): LivestockCatalogItem {
    return LivestockCatalogItem(
        id = id,
        category = category,
        commonName = commonName,
        turkishName = turkishName,
        scientificName = scientificName,
        recordType = recordType,
        waterGroup = waterGroup,
        temperatureC = temperatureC,
        ph = ph,
        specificGravity = specificGravity,
        waterRequirements = waterRequirements
    )
}
