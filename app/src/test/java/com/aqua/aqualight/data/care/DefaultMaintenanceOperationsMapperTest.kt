package com.aqua.aqualight.data.care

import com.aqua.aqualight.application.aquarium.AquariumLivestock
import com.aqua.aqualight.application.aquarium.AquariumMaterialSelection
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.application.aquarium.AquariumTankSnapshot
import com.aqua.aqualight.application.care.CareTaskSource
import com.aqua.aqualight.application.care.CareTaskStatus
import com.aqua.aqualight.application.care.CareTaskType
import com.aqua.aqualight.data.care.model.CareTask
import org.junit.Assert.assertEquals
import org.junit.Test
import com.aqua.aqualight.data.care.model.CareTaskSource as DataCareTaskSource
import com.aqua.aqualight.data.care.model.CareTaskStatus as DataCareTaskStatus
import com.aqua.aqualight.data.care.model.CareTaskType as DataCareTaskType

class DefaultMaintenanceOperationsMapperTest {

    @Test
    fun `data care task maps all presentation fields without owner metadata`() {
        val source = CareTask(
            id = 7L,
            ownerUid = "owner-secret",
            tankId = 11L,
            title = "Water change",
            description = "Replace water",
            type = DataCareTaskType.WATER_CHANGE,
            source = DataCareTaskSource.AUTOMATIC,
            status = DataCareTaskStatus.COMPLETED,
            dueAtMillis = 100L,
            completedAtMillis = 200L,
            repeatEnabled = true,
            repeatIntervalDays = 7,
            reminderEnabled = true,
            missedReminderEnabled = true,
            missedReminderDays = 2,
            waterChangePercent = 30,
            note = "Done",
            generatedRuleKey = "private-rule-key",
            createdAtMillis = 50L,
            updatedAtMillis = 250L
        )

        val mapped = source.toApplicationSnapshot()

        assertEquals(7L, mapped.id)
        assertEquals(11L, mapped.tankId)
        assertEquals("Water change", mapped.title)
        assertEquals("Replace water", mapped.description)
        assertEquals(CareTaskType.WATER_CHANGE, mapped.type)
        assertEquals(CareTaskSource.AUTOMATIC, mapped.source)
        assertEquals(CareTaskStatus.COMPLETED, mapped.status)
        assertEquals(100L, mapped.dueAtMillis)
        assertEquals(200L, mapped.completedAtMillis)
        assertEquals(true, mapped.repeatEnabled)
        assertEquals(7, mapped.repeatIntervalDays)
        assertEquals(true, mapped.reminderEnabled)
        assertEquals(true, mapped.missedReminderEnabled)
        assertEquals(2, mapped.missedReminderDays)
        assertEquals(30, mapped.waterChangePercent)
        assertEquals("Done", mapped.note)
        assertEquals(50L, mapped.createdAtMillis)
    }

    @Test
    fun `application and persistence task enums stay exactly aligned`() {
        CareTaskType.entries.forEach { applicationType ->
            assertEquals(applicationType.name, applicationType.toDataType().name)
        }
    }

    @Test
    fun `Smart Care tank mapping carries captured owner and nested values`() {
        val source = AquariumTankSnapshot(
            id = 21L,
            name = "Planted",
            description = "High tech",
            photoUri = "content://tank/21",
            setupDateEpochDay = 1000L,
            widthCm = 60,
            lengthCm = 35,
            heightCm = 36,
            sizeUnit = "cm",
            volumeUnit = "L",
            tankType = "Freshwater",
            tankStyle = "Nature",
            createdAtMillis = 2000L,
            smartCareEnabled = true,
            careRemindersEnabled = false,
            plants = listOf(
                AquariumPlantTag(31L, "Monte Carlo", "Carpet", 0.2f, 0.8f)
            ),
            materials = listOf(
                AquariumMaterialSelection(
                    id = 32L,
                    productId = "soil-1",
                    categoryKey = "substrate",
                    categoryTitle = "Substrate",
                    name = "Active Soil",
                    brand = "Aqua",
                    note = "Dark"
                )
            ),
            livestock = listOf(
                AquariumLivestock(33L, "Tetra", "Fish", 10, 3000L, "School")
            )
        )

        val mapped = source.toDataTank("owner-a")

        assertEquals("owner-a", mapped.ownerUid)
        assertEquals(source.id, mapped.id)
        assertEquals(source.name, mapped.name)
        assertEquals(source.smartCareEnabled, mapped.smartCareEnabled)
        assertEquals(source.careRemindersEnabled, mapped.careRemindersEnabled)
        assertEquals(31L, mapped.plants.single().id)
        assertEquals("Monte Carlo", mapped.plants.single().plantName)
        assertEquals(32L, mapped.materials.single().id)
        assertEquals("Active Soil", mapped.materials.single().name)
        assertEquals(33L, mapped.livestock.single().id)
        assertEquals(10, mapped.livestock.single().quantity)
    }
}
