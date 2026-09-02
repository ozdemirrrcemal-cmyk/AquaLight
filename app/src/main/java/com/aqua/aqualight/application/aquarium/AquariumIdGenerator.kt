package com.aqua.aqualight.application.aquarium

import java.util.UUID

object AquariumIdGenerator {

    fun newLong(
        existingIds: Set<Long> = emptySet()
    ): Long {
        var id = nextPositiveUuidLong()

        while (id <= 0L || existingIds.contains(id)) {
            id = nextPositiveUuidLong()
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

    private fun nextPositiveUuidLong(): Long {
        return UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    }
}
