package com.aqua.aqualight.ui.tabs.aquarium.catalog.plant

import com.aqua.aqualight.application.aquarium.AquariumPlantLightDemand

data class AquariumPlant(
    val catalogId: String,
    val name: String,
    val category: String,
    val searchNames: Set<String>,
    val lightDemand: AquariumPlantLightDemand
)
