package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import com.aqua.aqualight.application.aquarium.AquariumMaterialCategoryKeys
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot

internal object TankHealthOverviewProjection {
    fun system(tank: AquariumTankSnapshot?): TankHealthSystemUi {
        if (tank == null) return TankHealthSystemUi()

        fun selectedName(categoryKey: String): String? = tank.materials
            .firstOrNull { selection ->
                selection.categoryKey == categoryKey && selection.name.isNotBlank()
            }
            ?.name

        return TankHealthSystemUi(
            hasSelectedCo2 = tank.materials.any { selection ->
                selection.categoryKey == AquariumMaterialCategoryKeys.CO2
            },
            lightingName = selectedName(AquariumMaterialCategoryKeys.LIGHT),
            filterName = selectedName(AquariumMaterialCategoryKeys.FILTER),
            livestockCount = tank.livestock.sumOf { animal -> animal.quantity }
        )
    }
}
