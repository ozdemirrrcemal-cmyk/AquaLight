package com.aqua.aqualight.application.aquarium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AquariumPlantLightCatalogTest {

    @Test
    fun catalogContainsOneAuditableRecordForEverySupportedPlant() {
        val records = AquariumPlantLightCatalog.records

        assertEquals(AquariumPlantLightCatalog.EXPECTED_RECORD_COUNT, records.size)
        assertEquals(records.size, records.map { record -> record.catalogId }.toSet().size)
        assertEquals(records.map { record -> record.catalogId }.toSet(), AquariumPlantLightCatalog.catalogIds)
        assertTrue(records.all { record -> record.catalogId.startsWith("plant:") })
        assertTrue(records.all { record -> record.sourceOrganization.isNotBlank() })
        assertTrue(records.all { record -> record.sourceRecordId.isNotBlank() })
        assertTrue(records.all { record -> record.sourceUrl.startsWith("https://") })
    }

    @Test
    fun reviewedDemandClassesStayExplicitAndDeterministic() {
        assertEquals(
            mapOf(
                AquariumPlantLightDemand.LOW to 104,
                AquariumPlantLightDemand.MEDIUM to 67,
                AquariumPlantLightDemand.HIGH to 32
            ),
            AquariumPlantLightCatalog.records.groupingBy { record -> record.lightDemand }.eachCount()
        )
        assertEquals(
            AquariumPlantLightDemand.LOW,
            AquariumPlantLightCatalog.resolve("plant:anubias_barteri")
        )
        assertEquals(
            AquariumPlantLightDemand.MEDIUM,
            AquariumPlantLightCatalog.resolve("plant:micranthemum_tweediei_monte_carlo")
        )
        assertEquals(
            AquariumPlantLightDemand.HIGH,
            AquariumPlantLightCatalog.resolve("plant:tonina_sp_manaus")
        )
    }

    @Test
    fun missingExactRecordFailsClosedInsteadOfGuessingFromPlantName() {
        assertThrows(IllegalArgumentException::class.java) {
            AquariumPlantLightCatalog.resolve("plant:unreviewed_anubias_variant")
        }
    }

    @Test
    fun sourceIdentityIsKeptWithTheReviewedRequirement() {
        val tropicaRecord = AquariumPlantLightCatalog.requireRecord(
            "plant:micranthemum_tweediei_monte_carlo"
        )
        val additionalRecord = AquariumPlantLightCatalog.requireRecord(
            "plant:bucephalandra_sp_dark_skeleton_king"
        )

        assertEquals("4442", tropicaRecord.sourceRecordId)
        assertEquals("Tropica Aquarium Plants", tropicaRecord.sourceOrganization)
        assertEquals("Buce Plant", additionalRecord.sourceOrganization)
        assertEquals(
            AquariumPlantLightCatalog.CATALOG_REVISION,
            additionalRecord.catalogRevision
        )
    }
}
