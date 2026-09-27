package com.aqua.aqualight.data.aquarium.health

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.aquarium.LivestockCatalogItem
import com.aqua.aqualight.application.aquarium.LivestockRequirementEvidence
import com.aqua.aqualight.application.aquarium.LivestockWaterRequirements
import com.aqua.aqualight.application.aquarium.health.context.HealthEntityResolution
import com.aqua.aqualight.data.aquarium.catalog.plant.PlantCatalogParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthContextEntityResolverTest {
    @Test
    fun `local identities quantities custom missing and partial sources remain separate`() {
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main/assets/aqualight_plant_catalog.json").isFile }
        val catalog = PlantCatalogParser.parse(
            File(root, "app/src/main/assets/aqualight_plant_catalog.json").readText())
        val ready = catalog.records.first { it.care.healthAnalysisReady }
        val partial = catalog.records.first { !it.care.healthAnalysisReady }
        val tank = AquariumTankSnapshot(7, "Tank", "", null, null, 60, 30, 30, "cm", "L", "Freshwater Fish",
            "", 1_800_000_000_000, false, false,
            listOf(AquariumPlantTag(1, ready.id, "Local plant name", ""),
                AquariumPlantTag(2, partial.id, "Partial", ""), AquariumPlantTag(3, "plant:removed", "Missing", "")),
            emptyList(), listOf(AquariumLivestock(10, "Local animal name", quantity = 5, catalogEntryId = "fish"),
                AquariumLivestock(11, "Same species", quantity = 2, catalogEntryId = "fish"),
                AquariumLivestock(12, "Custom", catalogEntryId = "custom:12"),
                AquariumLivestock(13, "Missing", catalogEntryId = "removed")))
        val requirements = LivestockWaterRequirements(evidence = LivestockRequirementEvidence(
            "fish", "revision", "GUIDANCE", "SOFT", emptyMap()))
        val animal = LivestockCatalogItem("fish", "fish", "Catalog name", null, null, null,
            "Freshwater", null, null, null, requirements)
        val resolved = HealthContextEntityResolver.resolve(tank, catalog, listOf(animal))
        assertEquals(listOf(HealthEntityResolution.RESOLVED, HealthEntityResolution.PARTIAL,
            HealthEntityResolution.CATALOG_MISSING), resolved.plants.map { it.resolution })
        assertEquals(listOf(HealthEntityResolution.RESOLVED, HealthEntityResolution.RESOLVED,
            HealthEntityResolution.CUSTOM_UNVERIFIED, HealthEntityResolution.CATALOG_MISSING),
            resolved.livestock.map { it.resolution })
        assertEquals(listOf(10L, 11L, 12L, 13L), resolved.livestock.map { it.livestockId })
        assertEquals(listOf(5, 2, 1, 1), resolved.livestock.map { it.quantity })
        assertEquals("Local animal name", resolved.livestock.first().displayName)
        assertEquals("Local plant name", resolved.plants.first().displayName)
        assertEquals("revision", resolved.livestockRevision)
    }
}
