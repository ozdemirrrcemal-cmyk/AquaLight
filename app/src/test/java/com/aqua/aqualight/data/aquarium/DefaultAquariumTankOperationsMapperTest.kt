package com.aqua.aqualight.data.aquarium

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankCleanupStage
import com.aqua.aqualight.application.aquarium.AquariumTankDraft
import com.aqua.aqualight.application.aquarium.DeleteAquariumTanksResult
import com.aqua.aqualight.data.aquarium.delete.OwnerTankDataCleaner
import com.aqua.aqualight.data.aquarium.model.SavedAquariumLivestock
import com.aqua.aqualight.data.aquarium.model.SavedAquariumMaterial
import com.aqua.aqualight.data.aquarium.model.SavedAquariumPlant
import com.aqua.aqualight.data.aquarium.model.SavedAquariumTank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultAquariumTankOperationsMapperTest {

    @Test
    fun `saved tank maps every UI-facing field without owner leakage`() {
        val source = SavedAquariumTank(
            id = 7L,
            ownerUid = "owner-secret",
            name = "Reef",
            description = "Mixed reef",
            photoUri = "content://tank/7",
            setupDateMillis = 100L,
            widthCm = 80,
            lengthCm = 40,
            heightCm = 45,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "Saltwater",
            tankStyle = "Mixed",
            createdAtMillis = 200L,
            smartCareEnabled = true,
            careRemindersEnabled = false,
            plants = listOf(
                SavedAquariumPlant(
                    id = 11L,
                    plantName = "Anubias",
                    category = "Rhizome",
                    markerX = 0.25f,
                    markerY = 0.75f
                )
            ),
            materials = listOf(
                SavedAquariumMaterial(
                    id = 12L,
                    productId = "soil-1",
                    categoryKey = "substrate",
                    categoryTitle = "Substrate",
                    name = "Active Soil",
                    brand = "Aqua",
                    note = "Dark"
                )
            ),
            livestock = listOf(
                SavedAquariumLivestock(
                    id = 13L,
                    name = "Clownfish",
                    category = "Fish",
                    quantity = 2,
                    addedDateMillis = 300L,
                    note = "Pair"
                )
            )
        )

        val mapped = source.toApplicationSnapshot()

        assertEquals(7L, mapped.id)
        assertEquals("Reef", mapped.name)
        assertEquals("Mixed reef", mapped.description)
        assertEquals("content://tank/7", mapped.photoUri)
        assertEquals(100L, mapped.setupDateMillis)
        assertEquals(80, mapped.widthCm)
        assertEquals(40, mapped.lengthCm)
        assertEquals(45, mapped.heightCm)
        assertEquals("cm", mapped.sizeUnit)
        assertEquals("L", mapped.volumeUnit)
        assertEquals("Saltwater", mapped.tankType)
        assertEquals("Mixed", mapped.tankStyle)
        assertEquals(200L, mapped.createdAtMillis)
        assertEquals(true, mapped.smartCareEnabled)
        assertEquals(false, mapped.careRemindersEnabled)
        assertEquals(
            AquariumPlantTag(11L, "Anubias", "Rhizome", 0.25f, 0.75f),
            mapped.plants.single()
        )
        assertEquals(
            AquariumMaterialSelection(
                id = 12L,
                productId = "soil-1",
                categoryKey = "substrate",
                categoryTitle = "Substrate",
                name = "Active Soil",
                brand = "Aqua",
                note = "Dark"
            ),
            mapped.materials.single()
        )
        assertEquals(
            AquariumLivestock(13L, "Clownfish", "Fish", 2, 300L, "Pair"),
            mapped.livestock.single()
        )
    }

    @Test
    fun `application draft maps nested values to persistence draft`() {
        val source = AquariumTankDraft(
            name = "Planted",
            description = "High tech",
            photoUri = "content://draft",
            plants = listOf(AquariumPlantTag(21L, "Monte Carlo", "Carpet", 0.1f, 0.9f)),
            materials = listOf(
                AquariumMaterialSelection(
                    id = 22L,
                    productId = "fert-1",
                    categoryKey = "fertilizer",
                    categoryTitle = "Fertilizer",
                    name = "Macro",
                    brand = "Aqua",
                    note = "Weekly"
                )
            ),
            info = "CO2",
            setupDateMillis = 500L,
            widthCm = 60,
            lengthCm = 35,
            heightCm = 36,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "Freshwater",
            tankStyle = "Nature"
        )

        val mapped = source.toDataDraft()

        assertEquals(source.name, mapped.name)
        assertEquals(source.description, mapped.description)
        assertEquals(source.photoUri, mapped.photoUri)
        assertEquals(source.info, mapped.info)
        assertEquals(source.setupDateMillis, mapped.setupDateMillis)
        assertEquals(source.widthCm, mapped.widthCm)
        assertEquals(source.lengthCm, mapped.lengthCm)
        assertEquals(source.heightCm, mapped.heightCm)
        assertEquals(source.sizeUnit, mapped.sizeUnit)
        assertEquals(source.volumeUnit, mapped.volumeUnit)
        assertEquals(source.tankType, mapped.tankType)
        assertEquals(source.tankStyle, mapped.tankStyle)
        assertEquals(21L, mapped.plants.single().id)
        assertEquals("Monte Carlo", mapped.plants.single().plantName)
        assertEquals(22L, mapped.materials.single().id)
        assertEquals("Macro", mapped.materials.single().name)
    }

    @Test
    fun `delete result keeps public stages and hides throwable details`() {
        val failure = OwnerTankDataCleaner.Result.DeleteFailed(
            IllegalStateException("private storage detail")
        )
        val deleted = OwnerTankDataCleaner.Result.Deleted(
            tankIds = listOf(7L),
            cleanupIssues = listOf(
                OwnerTankDataCleaner.CleanupIssue(
                    tankId = 7L,
                    stage = OwnerTankDataCleaner.CleanupStage.DEVICE_ASSIGNMENTS,
                    error = IllegalStateException("private cleanup detail")
                )
            )
        )

        assertSame(DeleteAquariumTanksResult.DeleteFailed, failure.toApplicationResult())
        val mapped = deleted.toApplicationResult() as DeleteAquariumTanksResult.Deleted
        assertEquals(listOf(7L), mapped.tankIds)
        assertEquals(AquariumTankCleanupStage.DEVICE_ASSIGNMENTS, mapped.cleanupIssues.single().stage)
    }
}
