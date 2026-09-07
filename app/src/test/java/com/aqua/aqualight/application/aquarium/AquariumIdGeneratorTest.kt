package com.aqua.aqualight.application.aquarium

import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumIdGeneratorTest {

    @Test
    fun `generated tank id is positive and does not collide with existing ids`() {
        val existingIds = setOf(1L, 2L, 3L)

        val generatedId = AquariumIdGenerator.newLong(existingIds)

        assertTrue(generatedId > 0L)
        assertFalse(existingIds.contains(generatedId))
    }

    @Test
    fun `custom product id canonicalizes material name and ends with UUID`() {
        val generatedId = AquariumIdGenerator.newCustomProductId(
            categoryKey = "plant",
            materialName = " Java  Fern! "
        )
        val prefix = "custom_plant_java_fern_"

        assertTrue(generatedId.startsWith(prefix))
        UUID.fromString(generatedId.removePrefix(prefix))
    }

    @Test
    fun `custom product id uses stable fallback for non alphanumeric material name`() {
        val generatedId = AquariumIdGenerator.newCustomProductId(
            categoryKey = "decoration",
            materialName = "***"
        )

        assertTrue(generatedId.startsWith("custom_decoration_custom_"))
    }
}
