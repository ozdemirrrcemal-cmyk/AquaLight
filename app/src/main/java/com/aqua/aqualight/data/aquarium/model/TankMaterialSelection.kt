package com.aqua.aqualight.data.aquarium.model

import com.aqua.aqualight.application.aquarium.AquariumIdGenerator
import com.aqua.aqualight.application.aquarium.AquariumSubstrateSemantics

data class TankMaterialSelection(
    val id: Long = AquariumIdGenerator.newLong(),
    val productId: String,
    val categoryKey: String,
    val categoryTitle: String,
    val name: String,
    val brand: String = "",
    val note: String = ""
) {
    /** Derived again at the durable-store boundary; never accepted as caller input. */
    val substrateSemantic
        get() = AquariumSubstrateSemantics.resolve(
            productId = productId.trim(),
            categoryKey = categoryKey.trim()
        )
}
