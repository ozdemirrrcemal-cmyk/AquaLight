package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialCategoryKeys
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TankHealthOverviewProjectionTest {
    @Test
    fun summaryUsesOnlyTheSelectedTanksRealMaterialsAndLivestockQuantities() {
        val tank = emptyTank().copy(
            materials = listOf(
                material(AquariumMaterialCategoryKeys.CO2, "CO2 bottle"),
                material(AquariumMaterialCategoryKeys.LIGHT, ""),
                material(AquariumMaterialCategoryKeys.LIGHT, "LED 60"),
                material(AquariumMaterialCategoryKeys.FILTER, "Canister")
            ),
            livestock = listOf(
                AquariumLivestock(quantity = 3, catalogEntryId = "fish"),
                AquariumLivestock(quantity = 2, catalogEntryId = "shrimp")
            )
        )

        assertEquals(
            TankHealthSystemUi(
                hasSelectedCo2 = true,
                lightingName = "LED 60",
                filterName = "Canister",
                livestockCount = 5
            ),
            TankHealthOverviewProjection.system(tank)
        )
        assertEquals(TankHealthSystemUi(), TankHealthOverviewProjection.system(null))
        assertEquals(
            TankHealthSystemUi(livestockCount = 0),
            TankHealthOverviewProjection.system(emptyTank())
        )
    }

    private fun material(category: String, name: String) = AquariumMaterialSelection(
        productId = "test-$category",
        categoryKey = category,
        categoryTitle = category,
        name = name
    )

    private fun emptyTank() = AquariumTankSnapshot(
        id = 7L,
        name = "Tank",
        description = "",
        photoUri = null,
        setupDateEpochDay = null,
        widthCm = 60,
        lengthCm = 30,
        heightCm = 30,
        sizeUnit = "cm",
        volumeUnit = "L",
        tankType = "Freshwater Fish",
        tankStyle = "",
        createdAtMillis = 1L,
        smartCareEnabled = false,
        careRemindersEnabled = false,
        plants = emptyList(),
        materials = emptyList(),
        livestock = emptyList()
    )
}
