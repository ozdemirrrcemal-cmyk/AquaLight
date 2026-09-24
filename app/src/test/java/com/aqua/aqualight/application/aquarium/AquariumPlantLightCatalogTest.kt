package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AquariumPlantLightCatalogTest {
    @Test
    fun everyPlantHasOneMinimumLightRequirement() {
        val records = AquariumPlantLightCatalog.records
        assertEquals(248, records.size)
        assertEquals(records.size, records.map { it.catalogId }.toSet().size)
        assertEquals(
            mapOf(
                AquariumPlantLightDemand.LOW to 63,
                AquariumPlantLightDemand.MEDIUM to 136,
                AquariumPlantLightDemand.HIGH to 49
            ),
            records.groupingBy { it.lightDemand }.eachCount()
        )
        assertEquals(AquariumPlantLightDemand.LOW, AquariumPlantLightCatalog.resolve("plant:anubias_barteri"))
        assertEquals(AquariumPlantLightDemand.HIGH, AquariumPlantLightCatalog.resolve("plant:littorella_uniflora"))
    }

    @Test
    fun unknownIdentityFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            AquariumPlantLightCatalog.resolve("plant:unknown")
        }
    }
}
