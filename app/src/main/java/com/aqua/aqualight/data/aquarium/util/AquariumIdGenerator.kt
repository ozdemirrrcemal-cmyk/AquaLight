package com.aqua.aqualight.data.aquarium.util

import java.util.UUID
import kotlin.math.abs

object AquariumIdGenerator {

    fun newLong(
        existingIds: Set<Long> = emptySet()
    ): Long {
        var id = abs(UUID.randomUUID().mostSignificantBits)

        if (id == 0L) {
            id = System.currentTimeMillis()
        }

        while (existingIds.contains(id) || id <= 0L) {
            id = abs(UUID.randomUUID().mostSignificantBits)
        }

        return id
    }

    fun newCustomProductId(
        categoryKey: String,
        materialName: String
    ): String {
        val safeName = materialName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank {
                "custom"
            }

        return "custom_${categoryKey}_${safeName}_${UUID.randomUUID()}"
    }
}
